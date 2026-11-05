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

/**
 * Builds the Set-Cookie header for the legacy JSESSIONID cookie emitted by
 * {@link LegacyProcessServlet}.
 */
final class LegacySessionCookie {

    private LegacySessionCookie() {
        // utility class
    }

    /**
     * Builds the Set-Cookie header value for the legacy JSESSIONID cookie.
     * Defaults to SameSite=Lax; only uses SameSite=None (with Secure) when the
     * request is cross-site AND running in production, since None requires Secure
     * and would otherwise be rejected or invalid on plain HTTP.
     *
     * @param sessionId    the session identifier to be stored in the cookie
     * @param isProduction whether the server is running in a production environment
     * @param crossSite    whether the cookie must be usable in a cross-site context
     * @return the fully formed Set-Cookie header value
     */
    static String header(String sessionId, boolean isProduction, boolean crossSite) {
        boolean useNone = crossSite && isProduction; // None requires Secure
        StringBuilder sb = new StringBuilder();
        sb.append("JSESSIONID=").append(sessionId);
        sb.append("; Path=/etendo");
        sb.append("; HttpOnly");
        if (isProduction) sb.append("; Secure");
        sb.append("; SameSite=").append(useNone ? "None" : "Lax");
        return sb.toString();
    }
}
