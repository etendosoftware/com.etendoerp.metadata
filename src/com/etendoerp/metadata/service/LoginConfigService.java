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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;

import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * Serves POST /meta/login-config, before the user logs in: the System-level configuration the login screen
 * applies, as classic {@code Login.java} reads it. Today only {@code ForcedLoginURL}, the address the login
 * page must be reached through.
 */
public class LoginConfigService extends MetadataService {

    static final String FORCED_LOGIN_URL_PREFERENCE = "ForcedLoginURL";
    static final String FORCED_LOGIN_URL = "forcedLoginUrl";

    /**
     * Creates a new LoginConfigService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LoginConfigService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            write(new JSONObject().put(FORCED_LOGIN_URL, readForcedLoginUrl()));
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Reads {@code ForcedLoginURL} at System level, as classic: an undefined or conflicting preference means
     * the standard login.
     *
     * @return the forced address, or an empty string when there is none
     */
    static String readForcedLoginUrl() {
        try {
            return StringUtils.defaultString(
                    Preferences.getPreferenceValue(FORCED_LOGIN_URL_PREFERENCE, true, (String) null, null, null, null,
                            null));
        } catch (PropertyException e) {
            return "";
        }
    }
}
