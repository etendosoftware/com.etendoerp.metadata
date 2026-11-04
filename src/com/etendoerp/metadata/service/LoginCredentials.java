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

import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Username and password of a login request body, shared by the endpoints that authenticate them.
 */
final class LoginCredentials {
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private final String username;
    private final String password;

    private LoginCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Reads the credentials of a login request body.
     *
     * @param body the request body
     * @return the credentials
     * @throws UnprocessableContentException when the username or the password is missing
     */
    static LoginCredentials from(JSONObject body) {
        String username = ProfileRequestUtils.getStringOrNull(body, USERNAME_KEY);
        String password = ProfileRequestUtils.getStringOrNull(body, PASSWORD_KEY);

        if (username == null || password == null) {
            throw new UnprocessableContentException("username and password are required");
        }

        return new LoginCredentials(username, password);
    }

    String getUsername() {
        return username;
    }

    String getPassword() {
        return password;
    }
}
