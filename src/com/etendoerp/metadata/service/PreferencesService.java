/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.SessionInfo;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.etendoerp.metadata.utils.Utils;

/**
 * Service that returns all resolved preferences for the current user session.
 * Exposes preferences as a JSON map so that the new UI can load them at login
 * and use them for display logic expressions.
 *
 * <p>A POST stores one preference of the Property List for the current user, as the classic
 * {@code OB.PropertyStore.set} does through {@code StorePropertyActionHandler} (for example the
 * "Never show this message again" links). Body: {@code {"property": "...", "value": "...", "windowId": "..."}},
 * where {@code windowId} is optional.
 */
public class PreferencesService extends MetadataService {

    static final String POST_METHOD = "POST";
    static final String PROPERTY = "property";
    static final String VALUE = "value";
    static final String WINDOW_ID = "windowId";
    /** AD_Reference_ID of "Property Configuration", the Property List of AD_Preference. */
    static final String PROPERTY_LIST_REFERENCE_ID = "A26BA480E2014707B47257024C3CBFF7";
    private static final String QUOTE = "\"";

    /**
     * Constructs a new PreferencesService.
     *
     * @param request the HttpServletRequest object that contains the request the client has made of the service
     * @param response the HttpServletResponse object that contains the response the service sends to the client
     */
    public PreferencesService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (POST_METHOD.equalsIgnoreCase(getRequest().getMethod())) {
            storePreference();
            return;
        }
        OBContext.setAdminMode();
        try {
            final JSONObject result = new JSONObject();
            final JSONObject preferences = new JSONObject();

            final List<Preference> allPrefs = Preferences.getAllPreferences(
                    OBContext.getOBContext().getCurrentClient().getId(),
                    OBContext.getOBContext().getCurrentOrganization().getId(),
                    OBContext.getOBContext().getUser().getId(),
                    OBContext.getOBContext().getRole().getId());

            final List<String> handledIds = new ArrayList<>();

            for (Preference pref : allPrefs) {
                processPreference(pref, preferences, handledIds);
            }

            result.put("preferences", preferences);
            write(result);
        } catch (Exception e) {
            logger.error("Error retrieving preferences: {}", e.getMessage(), e);
            throw new IOException("Error retrieving preferences", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Publishes a single preference into the result map.
     *
     * @param pref the preference to publish
     * @param preferences the map being built, keyed as described in {@link #buildPreferenceKey}
     * @param handledIds keys already published, so the first row wins on duplicates
     * @throws Exception if the value cannot be written to the JSON map
     */
    private void processPreference(Preference pref, JSONObject preferences, List<String> handledIds)
            throws Exception {
        String key = getPreferenceKey(pref);
        if (key == null) {
            return;
        }

        // Classic emits a single key per preference: a window-specific row is published ONLY under
        // KEY_<windowId>, never under the bare KEY, so a global lookup can never be satisfied by a
        // window-scoped row. See PropertiesComponent#59-75.
        addPreferenceIfNotExists(buildPreferenceKey(key, pref.getWindow()), pref.getSearchKey(),
                preferences, handledIds);
    }

    /**
     * Builds the single key a preference is published under, mirroring classic PropertiesComponent:
     * {@code KEY_<windowId>} for a window-specific preference, the bare {@code KEY} otherwise.
     *
     * @param key the preference property or attribute name
     * @param window the preference's window, or {@code null} when the preference is global
     * @return the key to publish the preference value under
     */
    static String buildPreferenceKey(String key, Window window) {
        if (window == null) {
            return key;
        }
        return key + "_" + window.getId();
    }

    private String getPreferenceKey(Preference pref) {
        if (pref.getProperty() != null) {
            return pref.getProperty();
        } else {
            return pref.getAttribute();
        }
    }

    private void addPreferenceIfNotExists(String key, String value, JSONObject preferences, 
            List<String> handledIds) throws Exception {
        if (!handledIds.contains(key)) {
            handledIds.add(key);
            preferences.put(key, value != null ? value : "");
        }
    }

    /**
     * Stores the preference of the request body for the current client, organization, user and role, and
     * answers it back. Runs in admin mode, as the classic handler: preferences belong to the System client.
     *
     * @throws IOException if the response cannot be written
     */
    private void storePreference() throws IOException {
        OBContext.setAdminMode();
        try {
            JSONObject body = Utils.getRequestData(getRequest());
            String property = readProperty(body);
            String value = cleanValue(body.optString(VALUE, ""));
            Window window = readWindow(body);
            OBContext context = OBContext.getOBContext();
            Preferences.setPreferenceValue(property, value, true, context.getCurrentClient(),
                    context.getCurrentOrganization(), context.getUser(), context.getRole(), window, null);
            SessionInfo.auditThisThread(false);
            OBDal.getInstance().flush();
            write(new JSONObject().put(PROPERTY, property).put(VALUE, value));
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Reads the property to store, which must be a value of the Property List: the endpoint never writes a
     * free attribute, so it cannot be used to change other kinds of configuration.
     *
     * @param body the request body
     * @return the property name
     * @throws UnprocessableContentException when the property is missing or not in the Property List
     */
    static String readProperty(JSONObject body) {
        String property = body.optString(PROPERTY, "");
        if (StringUtils.isBlank(property) || !isListProperty(property)) {
            throw new UnprocessableContentException("Unknown preference property: " + property);
        }
        return property;
    }

    /**
     * Tells whether {@code property} is a value of the Property List reference.
     *
     * @param property the property name
     * @return true when the Property List holds it
     */
    static boolean isListProperty(String property) {
        return OBDal.getInstance()
                .createQuery(org.openbravo.model.ad.domain.List.class,
                        "as l where l.reference.id = :reference and l.searchKey = :property")
                .setNamedParameter("reference", PROPERTY_LIST_REFERENCE_ID)
                .setNamedParameter(PROPERTY, property)
                .count() > 0;
    }

    /**
     * Reads the optional window the preference is scoped to.
     *
     * @param body the request body
     * @return the window, or {@code null} when the preference is global
     * @throws UnprocessableContentException when the window id does not exist
     */
    static Window readWindow(JSONObject body) {
        String windowId = body.optString(WINDOW_ID, "");
        if (StringUtils.isBlank(windowId)) {
            return null;
        }
        Window window = OBDal.getInstance().get(Window.class, windowId);
        if (window == null) {
            throw new UnprocessableContentException("Unknown window: " + windowId);
        }
        return window;
    }

    /**
     * Cleans the value as the classic handler does: no line breaks and no surrounding quotes.
     *
     * @param value the raw value
     * @return the value to store
     */
    static String cleanValue(String value) {
        String cleaned = value.replace("\n", "");
        if (cleaned.length() > 1 && cleaned.startsWith(QUOTE) && cleaned.endsWith(QUOTE)) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
