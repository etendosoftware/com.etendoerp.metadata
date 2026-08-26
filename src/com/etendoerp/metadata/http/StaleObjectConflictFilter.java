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

/**
 * Detects stale-object (optimistic-lock) conflicts on {@code add}/{@code update} requests that
 * hit the core {@code org.openbravo.service.datasource.DataSourceServlet} directly.
 *
 * <p>The frontend's {@code getDatasourceEndpoint} (in {@code endpoints.ts}) routes add/update/
 * remove operations straight to that servlet's own URL ({@code org.openbravo.service.datasource/
 * <entity>}) rather than through the {@code sws/com.etendoerp.metadata.forward/...} path that
 * {@link ForwarderServlet} serves -- so {@link ForwarderServlet}'s own conflict detection never
 * sees the requests that can actually produce a conflict. This filter is mapped to the same URL
 * pattern as that direct path (via the servlet-3.0 {@link WebFilter} annotation, the same
 * mechanism already used by {@link MetadataFilter} in this module -- picked up on deploy with no
 * {@code web.xml}/AD_MODEL_OBJECT registration needed), so it runs ahead of whichever servlet
 * ends up serving that path and can still buffer and rewrite the response the same way.</p>
 */
@WebFilter(urlPatterns = "/org.openbravo.service.datasource/*")
public class StaleObjectConflictFilter implements Filter {

    private static final Logger log4j = LogManager.getLogger(StaleObjectConflictFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed.
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method = request.getMethod();
        String operationType = request.getParameter("_operationType");
        boolean isSaveOperation = "PUT".equalsIgnoreCase(method)
                || ("POST".equalsIgnoreCase(method) && ("add".equals(operationType) || "update".equals(operationType)));

        if (!isSaveOperation) {
            chain.doFilter(req, res);
            return;
        }

        BufferedResponseWrapper buffered = new BufferedResponseWrapper(response);
        chain.doFilter(req, buffered);

        String body = buffered.getCapturedBodyAsString();
        String marker = StaleObjectConflictDetector.resolveStaleMarker(body);
        if (marker != null) {
            String correlationId = StaleObjectConflictDetector.newCorrelationId();
            log4j.warn("[meta] Stale object conflict on direct-path save (cid={}): {} {}", correlationId, method,
                    request.getPathInfo());
            StaleObjectConflictDetector.writeConflictResponse(response, correlationId, marker);
        } else {
            buffered.flushToRealResponse();
        }
    }

    @Override
    public void destroy() {
        // No resources to release.
    }
}
