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

import com.etendoerp.metadata.service.ExtraPropertiesEnricher;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.service.json.JsonConstants;

/**
 * Servlet that intercepts datasource requests and enriches form-urlencoded fetch requests
 * with {@code _extraProperties} for FK fields whose referenced entity has a Color-typed column
 * (AD_Reference ID {@code "27"}).
 *
 * <p>Only POST requests with {@code _operationType=fetch} are enriched; all other requests are
 * forwarded to {@link org.openbravo.service.datasource.DataSourceServlet} unmodified.</p>
 *
 * <p>The enrichment causes {@code DataToJsonConverter} to include the nested color value in each
 * datasource row (e.g. {@code "priority$color"}) alongside the existing identifier
 * (e.g. {@code "priority$_identifier"}).</p>
 */
public class ForwarderServlet extends BaseWebService {

    private static final Logger log4j = LogManager.getLogger(ForwarderServlet.class);

    @Override
    protected void process(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            processForwardRequest(req.getPathInfo(), req, res);
        } catch (IOException | ServletException e) {
            log4j.error("Error processing forward request: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processForwardRequest(String path, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        org.openbravo.service.datasource.DataSourceServlet dataSourceServlet =
                WeldUtils.getInstanceFromStaticBeanManager(org.openbravo.service.datasource.DataSourceServlet.class);
        String method = request.getMethod();
        String operationType = request.getParameter("_operationType");

        HttpServletRequest enrichedRequest = ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))
                ? tryEnrichRequest(request, path)
                : request;

        boolean isPost = "POST".equalsIgnoreCase(method);
        boolean isPut = "PUT".equalsIgnoreCase(method);
        boolean isSaveOperation = isPut || (isPost && ("add".equals(operationType) || "update".equals(operationType)));

        if (isSaveOperation) {
            forwardSaveWithConflictDetection(dataSourceServlet, isPost, enrichedRequest, response, method, path);
        } else if (isPost) {
            dataSourceServlet.doPost(enrichedRequest, response);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            dataSourceServlet.doDelete(request, response);
        } else {
            dataSourceServlet.doGet(enrichedRequest, response);
        }
    }

    /**
     * Forwards an {@code add}/{@code update} request to the core {@code DataSourceServlet},
     * buffering its response so it can be inspected before reaching the real client.
     *
     * <p>The core resolves optimistic-lock (stale object) conflicts internally and always
     * writes them as a generic HTTP 200 body, indistinguishable from any other save error.
     * This method detects that specific case and rewrites it as a distinct, structured
     * HTTP 409 response; every other outcome (success or any other error) is replayed to the
     * real response unchanged.</p>
     *
     * @param dataSourceServlet the core servlet to forward to
     * @param isPost            {@code true} for POST (add/update), {@code false} for PUT (update)
     * @param request           the (possibly enriched) request to forward
     * @param realResponse      the real response ultimately sent to the client
     * @param method            the HTTP method, used only for logging
     * @param path              the request path, used only for logging
     */
    private void forwardSaveWithConflictDetection(org.openbravo.service.datasource.DataSourceServlet dataSourceServlet,
            boolean isPost, HttpServletRequest request, HttpServletResponse realResponse, String method, String path)
            throws IOException, ServletException {
        BufferedResponseWrapper buffered = new BufferedResponseWrapper(realResponse);
        if (isPost) {
            dataSourceServlet.doPost(request, buffered);
        } else {
            dataSourceServlet.doPut(request, buffered);
        }

        String body = buffered.getCapturedBodyAsString();
        String marker = StaleObjectConflictDetector.resolveStaleMarker(body);
        if (marker != null) {
            String correlationId = StaleObjectConflictDetector.newCorrelationId();
            log4j.warn("[meta] Stale object conflict on forwarded save (cid={}): {} {}", correlationId, method, path);
            StaleObjectConflictDetector.writeConflictResponse(realResponse, correlationId, marker);
        } else {
            buffered.flushToRealResponse();
        }
    }

    /**
     * Returns a request enriched with {@code _extraProperties} when the operation is a fetch
     * and the entity has FK fields pointing to entities with Color-typed columns.
     * Returns the original request unchanged in all other cases.
     *
     * @param request  the original HTTP request
     * @param pathInfo the servlet path info used to extract the entity name
     * @return the original or wrapped request
     */
    private HttpServletRequest tryEnrichRequest(HttpServletRequest request, String pathInfo) {
        String operationType = request.getParameter("_operationType");

        if (operationType != null && !operationType.isEmpty() &&
                !"fetch".equals(operationType) && !"add".equals(operationType) && !"update".equals(operationType)) {
            return request;
        }
        // A _distinct query re-parents the result set to the referenced entity, so color paths
        // computed for the grid's own entity (e.g. "leadStatus.color") no longer resolve and would
        // NPE during serialization. Pick-list/distinct responses never render color tags, so skip
        // enrichment entirely — matching Classic, which never adds color extra-properties to pick lists.
        if (request.getParameter(JsonConstants.DISTINCT_PARAMETER) != null) {
            return request;
        }
        String entityName = request.getParameter("_entityName");

        if (entityName == null || entityName.isEmpty()) {
            entityName = extractEntityName(pathInfo);
        }
        if (entityName == null || entityName.isEmpty()) {
            return request;
        }
        String extraProps = ExtraPropertiesEnricher.getExtraProperties(entityName);
        if (extraProps == null || extraProps.isEmpty()) {
            return request;
        }
        log4j.debug("ForwarderServlet: injecting _extraProperties for entity {}: {}", entityName, extraProps);
        return new ExtraPropertiesRequestWrapper(request, extraProps);
    }

    /**
     * Extracts the entity name from the last path segment (e.g. {@code "/ETASK_TaskType"} → {@code "ETASK_TaskType"}).
     *
     * @param pathInfo the servlet path info
     * @return the entity name, or {@code null} if the path is blank
     */
    private static String extractEntityName(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }
        String[] parts = pathInfo.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    /**
     * A {@link HttpServletRequestWrapper} that merges additional extra-property paths into
     * the existing {@code _extraProperties} form parameter without altering any other parameter.
     *
     * <p>If {@code _extraProperties} is already present in the original request, the new paths
     * are appended with a comma separator.</p>
     */
    private static class ExtraPropertiesRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> cachedParams;

        /**
         * @param request                   the original request
         * @param additionalExtraProperties comma-separated property paths to inject
         */
        public ExtraPropertiesRequestWrapper(HttpServletRequest request, String additionalExtraProperties) {
            super(request);
            this.cachedParams = new HashMap<>(request.getParameterMap());

            String[] existing = this.cachedParams.get("_extraProperties");
            String newValue = (existing == null || existing.length == 0 || existing[0].isEmpty())
                    ? additionalExtraProperties
                    : existing[0] + "," + additionalExtraProperties;

            this.cachedParams.put("_extraProperties", new String[]{ newValue });
        }

        @Override
        public String getParameter(String name) {
            String[] values = cachedParams.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            return cachedParams.get(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(cachedParams);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(cachedParams.keySet());
        }
    }
}
