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

/**
 * Service that returns all resolved preferences for the current user session.
 * Exposes preferences as a JSON map so that the new UI can load them at login
 * and use them for display logic expressions.
 */
public class PreferencesService extends MetadataService {

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
                String key;
                if (pref.getProperty() != null) {
                    key = pref.getProperty();
                } else {
                    key = pref.getAttribute();
                }

                if (key == null) {
                    continue;
                }

                String value = pref.getSearchKey();

                // If the preference is window-specific, add a window-scoped entry
                if (pref.getWindow() != null) {
                    String windowKey = key + "_" + pref.getWindow().getId();
                    if (!handledIds.contains(windowKey)) {
                        handledIds.add(windowKey);
                        preferences.put(windowKey, value != null ? value : "");
                    }
                }

                // Add the global entry (non-window-scoped), skip duplicates
                if (!handledIds.contains(key)) {
                    handledIds.add(key);
                    preferences.put(key, value != null ? value : "");
                }
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
}
