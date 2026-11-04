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

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONObject;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.LoginSessionRegistry;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Serves {@code POST /sws/com.etendoerp.metadata.meta/logout} - revokes the caller's JWT by the
 * SHA-256 hash of its full raw string, so it stops working immediately instead of remaining
 * valid until it expires. Hashing the whole token (rather than reading a {@code jti} claim)
 * means this works regardless of which login path issued the token — not every issuer sets
 * {@code jti} (classic {@code /sws/login} doesn't).
 * <p>
 * The {@code Authorization} header identifies the token to revoke. An optional JSON body
 * {@code {"sessionId": "..."}} names the {@code AD_Session} recorded at login, which is then closed
 * (see {@link LoginSessionRegistry#close}). The caller (the frontend's BFF logout route) treats the
 * response as best-effort and doesn't depend on its body.
 *
 * @see com.etendoerp.metadata.auth.TokenRevocationStore
 */
public class LogoutService extends MetadataService {
    private static final String USER_CLAIM = "user";

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

        String rawToken = Utils.extractBearerToken(getRequest());
        TokenRevocationStore.revoke(rawToken, decoded.getExpiresAt());
        closeLoginSession(decoded);

        getResponse().setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Closes the {@code AD_Session} recorded at login when the body names it, as Classic does when
     * its HTTP session ends. Only a session of the token's user can be closed.
     */
    private void closeLoginSession(DecodedJWT decoded) {
        JSONObject body = com.etendoerp.metadata.utils.Utils.getRequestData(getRequest());
        String sessionId = body.optString(LoginSessionRegistry.SESSION_ID, null);
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        LoginSessionRegistry.close(sessionId, decoded.getClaim(USER_CLAIM).asString());
    }
}
