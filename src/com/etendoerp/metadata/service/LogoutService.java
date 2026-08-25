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

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Serves {@code POST /sws/com.etendoerp.metadata.meta/logout} - revokes the caller's JWT by its
 * {@code jti} claim so it stops working immediately instead of remaining valid until it expires.
 * <p>
 * No request body is read; the {@code Authorization} header is the only input, and the caller
 * (the frontend's BFF logout route) treats the response as best-effort and doesn't depend on its
 * body.
 *
 * @see com.etendoerp.metadata.auth.TokenRevocationStore
 */
public class LogoutService extends MetadataService {

    /**
     * Creates a new LogoutService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LogoutService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        DecodedJWT decoded = Utils.decodeBearerToken(getRequest());
        if (decoded == null) {
            throw new UnauthorizedException("Valid Authorization: Bearer <token> header required");
        }

        // Tokens minted outside this module (classic /sws/login, via
        // SecureWebServicesUtils.generateToken) carry no jti claim and therefore can't be
        // revoked by this mechanism - same accepted limitation as any other WebService bean
        // outside com.etendoerp.metadata. Every token this module itself issues (LoginService,
        // ChangeProfileService) always sets jti, so this only skips tokens already out of scope.
        String jti = decoded.getClaim("jti").asString();
        if (jti != null && !jti.isEmpty()) {
            TokenRevocationStore.revoke(jti, decoded.getExpiresAt());
        }

        getResponse().setStatus(HttpServletResponse.SC_OK);
    }
}
