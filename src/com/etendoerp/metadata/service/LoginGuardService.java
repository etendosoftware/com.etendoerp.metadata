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

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.auth.LoginAttemptGuard;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Serves {@code POST /meta/login-guard} - applies the Classic failed-attempt protections to a New
 * UI password login before the token is requested from {@code /sws/login}.
 * <p>
 * {@code /sws/login} belongs to {@code com.smf.securewebservices} and only checks the password, so
 * the frontend first sends the same credentials here: the answer is delayed by the previous failed
 * attempts, a locked user is refused, and a failed attempt is recorded and may lock the user (see
 * {@link LoginAttemptGuard}). Reachable without a token, like the login itself.
 * <p>
 * A successful check records nothing: the successful session is recorded once the token exists
 * (see {@link LoginSessionService}).
 */
public class LoginGuardService extends MetadataService {

    /**
     * Creates a new LoginGuardService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LoginGuardService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        try {
            OBContext.setAdminMode(true);
            JSONObject body = com.etendoerp.metadata.utils.Utils.getRequestData(getRequest());
            LoginCredentials credentials = LoginCredentials.from(body);

            LoginAttemptGuard.authenticate(getRequest(), credentials.getUsername(), credentials.getPassword());
            write(new JSONObject());
        } catch (UnauthorizedException | UnprocessableContentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error checking the login attempt: {}", e.getMessage(), e);
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
