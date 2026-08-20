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

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.utils.Constants;

/**
 * Exposes {@code POST /sws/com.etendoerp.metadata.meta/login} without requiring a pre-existing
 * Bearer token, mirroring how {@code com.smf.securewebservices.service.SecureLoginServlet} is
 * separately mapped to the exact path {@code /sws/login} to bypass the same requirement.
 * <p>
 * Every other path under {@code /sws/*} is dispatched through
 * {@code com.smf.securewebservices.service.SecureWebServiceServlet} (mapped to the wildcard
 * {@code /sws/*} in {@code web.xml}), which requires a valid JWT before a request ever reaches
 * {@link MetadataServlet}. That is correct for every other {@code /meta/*} endpoint, but is
 * exactly what makes an unauthenticated login endpoint impossible to reach through that path -
 * there being no prior token is the very case a login endpoint has to handle. Per the servlet
 * spec, an exact-path mapping (this class, via {@code @WebServlet}) takes precedence over that
 * wildcard mapping, so this specific path reaches {@link MetadataServlet} directly instead -
 * exactly as {@code /sws/login} does for the classic login. Every other {@code /sws/*} path is
 * unaffected and keeps going through the normal authenticated flow.
 *
 * @see com.etendoerp.metadata.service.LoginService
 */
@WebServlet(urlPatterns = { "/sws/com.etendoerp.metadata.meta/login" })
public class UnauthenticatedLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        new MetadataServlet().process(withPathInfo(request), response);
    }

    /**
     * An exact-path {@code @WebServlet} mapping (no {@code *}) leaves {@code getPathInfo()} null
     * - there is no "extra" path segment left to report. {@link ServiceFactory} routes purely
     * off {@code getPathInfo()}, so without this override it would see an empty path and 404.
     * Wrapping it to report the same {@code /com.etendoerp.metadata.meta/login} segment the
     * wildcard-mapped route would have produced lets {@link ServiceFactory#normalizePath} reduce
     * it to {@link Constants#LOGIN_PATH} exactly as it does for every other {@code /meta/*} call.
     */
    private HttpServletRequest withPathInfo(HttpServletRequest original) {
        String pathInfo = "/com.etendoerp.metadata.meta" + Constants.LOGIN_PATH;
        return new HttpServletRequestWrapper(original) {
            @Override
            public String getPathInfo() {
                return pathInfo;
            }
        };
    }
}
