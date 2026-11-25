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

package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;

import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;

/**
 * Answers a JSON 401 when a request to a Classic servlet carries a Bearer token that can no longer
 * be used: malformed, badly signed, expired or revoked.
 *
 * <p>The new UI calls these servlets with the Bearer token of its session. They authenticate it
 * through the Classic authentication manager, which turns an unusable token into a 500 or into an
 * empty 200, never into a 401 — so the new UI could not tell an invalid session from a server
 * error. The {@code /sws/*} services already answer 401 on their own and are not mapped here.</p>
 *
 * <p>Only requests that carry an {@code Authorization: Bearer} header are checked. Classic itself
 * authenticates with the session cookie and never sends one, so it is not affected. A token that
 * is accepted here is exactly the one the Classic authentication manager accepts (same decoder),
 * plus the revocation check that those servlets never applied.</p>
 */
@WebFilter(urlPatterns = { "/attachments/*", "/notes/*", "/org.openbravo.service.datasource/*",
        "/org.openbravo.client.kernel/*" })
public class BearerTokenGuardFilter implements Filter {

    private static final Logger log4j = LogManager.getLogger(BearerTokenGuardFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed.
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String rejection = resolveRejection(com.etendoerp.metadata.auth.Utils.extractBearerToken(request));

        if (rejection == null) {
            chain.doFilter(req, res);
            return;
        }

        log4j.debug("[meta] Rejected Bearer token on {}: {}", request.getRequestURI(), rejection);
        Utils.writeJsonErrorResponse((HttpServletResponse) res, HttpServletResponse.SC_UNAUTHORIZED, rejection);
    }

    @Override
    public void destroy() {
        // No resources to release.
    }

    /**
     * Resolves why a Bearer token must be rejected, if it must.
     *
     * @param token the raw Bearer token, or {@code null} when the request carries none
     * @return the error to answer with, or {@code null} when the request can go on
     */
    static String resolveRejection(String token) {
        if (token == null) {
            return null;
        }
        try {
            com.etendoerp.metadata.auth.Utils.decodeToken(token);
        } catch (OBException e) {
            return Constants.INVALID_TOKEN_ERROR;
        }
        if (TokenRevocationStore.isRevoked(token)) {
            return Constants.TOKEN_REVOKED_ERROR;
        }
        return null;
    }
}
