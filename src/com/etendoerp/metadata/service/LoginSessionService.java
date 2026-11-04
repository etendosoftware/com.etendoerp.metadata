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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.User;

import com.etendoerp.metadata.auth.LoginSessionRegistry;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;

/**
 * Serves {@code POST /meta/login-session} - records the successful login of the authenticated user
 * in the Classic session registry ({@code AD_Session}) and returns its id.
 * <p>
 * {@code /sws/login} issues the New UI token without recording any session, so the frontend calls
 * this endpoint with the new token right after the login. The returned id is sent back on logout to
 * close the session (see {@link LogoutService}).
 */
public class LoginSessionService extends MetadataService {

    /**
     * Creates a new LoginSessionService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LoginSessionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        try {
            OBContext.setAdminMode(true);
            User user = OBContext.getOBContext().getUser();
            String sessionId = LoginSessionRegistry.register(getRequest(), user.getUsername(), user.getId(),
                LoginSessionRegistry.SUCCESS_STATUS);

            write(new JSONObject().put(LoginSessionRegistry.SESSION_ID, sessionId));
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
