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

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.ui.Window;

/**
 * Service that returns all resolved preferences for the current user session.
 * Exposes preferences as a JSON map so that the new UI can load them at login
 * and use them for display logic expressions.
 */
public class PreferencesService extends MetadataService {

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
}
