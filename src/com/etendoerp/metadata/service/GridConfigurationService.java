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
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.etendoerp.metadata.utils.Utils;

/**
 * Durable, per-user grid column configuration (visibility, order, width and pinning) of the new UI.
 * <p>
 * Mirrors classic {@code OBUIAPP_GridConfiguration}: the configuration of every tab of a window is
 * stored as a single JSON map ({@code {<tabId>: {...}}}) in one AD_Preference per user and window.
 * It uses its own attribute because the classic value is a SmartClient view state, which has a
 * different format. The tab configuration itself is opaque to the server.
 * <ul>
 * <li>{@code GET ?windowId=} returns {@code {windowId, configuration}}.</li>
 * <li>{@code PUT {windowId, tabId, configuration}} replaces the configuration of one tab, or removes
 * it when {@code configuration} is {@code null}, and returns the updated map.</li>
 * </ul>
 */
public class GridConfigurationService extends MetadataService {

    static final String GRID_CONFIGURATION_ATTRIBUTE = "ETMETA_GridConfiguration";
    static final String FIELD_WINDOW_ID = "windowId";
    static final String FIELD_TAB_ID = "tabId";
    static final String FIELD_CONFIGURATION = "configuration";

    /**
     * Constructs a new GridConfigurationService.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public GridConfigurationService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        // Like classic StorePropertyActionHandler: no client/org access check, because
        // Preferences.setPreferenceValue always creates the AD_Preference in client/org 0. Access is
        // still scoped to the current user's own preference.
        OBContext.setAdminMode();
        try {
            switch (getRequest().getMethod()) {
                case "GET":
                    handleGet();
                    break;
                case "PUT":
                    handlePut();
                    break;
                default:
                    throw new MethodNotAllowedException();
            }
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleGet() throws JSONException, IOException {
        Window window = getWindow(requireValue(getRequest().getParameter(FIELD_WINDOW_ID), FIELD_WINDOW_ID));
        JSONObject configuration = readConfiguration(findPreference(window));
        write(buildResponse(window, configuration));
    }

    private void handlePut() throws JSONException, IOException {
        JSONObject body = readBody();
        Window window = getWindow(requireValue(body.optString(FIELD_WINDOW_ID, null), FIELD_WINDOW_ID));
        String tabId = requireValue(body.optString(FIELD_TAB_ID, null), FIELD_TAB_ID);
        validateTabBelongsToWindow(tabId, window);
        if (!body.has(FIELD_CONFIGURATION)) {
            throw new UnprocessableContentException("Missing " + FIELD_CONFIGURATION);
        }

        Preference preference = findPreference(window);
        JSONObject configuration = readConfiguration(preference);
        mergeTabConfiguration(configuration, tabId, body.get(FIELD_CONFIGURATION));
        persist(window, preference, configuration);
        write(buildResponse(window, configuration));
    }

    /**
     * Replaces the configuration of a tab in the window map, or removes it when the value is null.
     *
     * @param configuration the window map, modified in place
     * @param tabId         the tab whose configuration changes
     * @param value         the new tab configuration: a JSON object, or JSON null to remove it
     * @throws JSONException                 if the value cannot be stored in the map
     * @throws UnprocessableContentException if the value is neither an object nor null
     */
    static void mergeTabConfiguration(JSONObject configuration, String tabId, Object value) throws JSONException {
        if (value instanceof JSONObject) {
            configuration.put(tabId, value);
        } else if (value == null || JSONObject.NULL.equals(value)) {
            configuration.remove(tabId);
        } else {
            throw new UnprocessableContentException(FIELD_CONFIGURATION + " must be an object or null");
        }
    }

    /**
     * Parses the stored window map. A missing or unreadable value yields an empty map, so a corrupt
     * preference never breaks the grid: the next save overwrites it.
     *
     * @param preference the stored preference, or {@code null} when there is none
     * @return the window map
     */
    JSONObject readConfiguration(Preference preference) {
        if (preference == null || StringUtils.isBlank(preference.getSearchKey())) {
            return new JSONObject();
        }
        try {
            return new JSONObject(preference.getSearchKey());
        } catch (JSONException e) {
            logger.warn("Ignoring unreadable grid configuration {}: {}", preference.getId(), e.getMessage());
            return new JSONObject();
        }
    }

    private void persist(Window window, Preference preference, JSONObject configuration) {
        SessionInfo.auditThisThread(false);
        if (configuration.length() > 0) {
            OBContext context = OBContext.getOBContext();
            Preferences.setPreferenceValue(GRID_CONFIGURATION_ATTRIBUTE, configuration.toString(), false,
                    context.getCurrentClient(), null, context.getUser(), null, window, null);
        } else if (preference != null) {
            OBDal.getInstance().remove(preference);
        }
        OBDal.getInstance().flush();
    }

    /**
     * Finds the current user's preference for the window, scoped exactly as it is stored: current
     * client, current user and window, with no organization or role.
     */
    private Preference findPreference(Window window) {
        OBContext context = OBContext.getOBContext();
        List<Preference> preferences = Preferences.getPreferences(GRID_CONFIGURATION_ATTRIBUTE, false,
                context.getCurrentClient().getId(), null, context.getUser().getId(), null, window.getId());
        return preferences.isEmpty() ? null : preferences.get(0);
    }

    private Window getWindow(String windowId) {
        Window window = OBDal.getInstance().get(Window.class, windowId);
        if (window == null) {
            throw new NotFoundException("Window not found: " + windowId);
        }
        return window;
    }

    private void validateTabBelongsToWindow(String tabId, Window window) {
        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null || !window.getId().equals(tab.getWindow().getId())) {
            throw new UnprocessableContentException("Tab " + tabId + " does not belong to window " + window.getId());
        }
    }

    private static String requireValue(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new UnprocessableContentException("Missing " + field);
        }
        return value;
    }

    private static JSONObject buildResponse(Window window, JSONObject configuration) throws JSONException {
        return new JSONObject().put(FIELD_WINDOW_ID, window.getId()).put(FIELD_CONFIGURATION, configuration);
    }

    private JSONObject readBody() throws IOException {
        try {
            return new JSONObject(Utils.readRequestBody(getRequest()));
        } catch (JSONException e) {
            throw new UnprocessableContentException("Invalid JSON body: " + e.getMessage());
        }
    }
}
