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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.service.web.WebService;

/**
 * Servlet that forwards incoming requests to DataSourceServlet for modern API endpoints.
 * This servlet is designed exclusively for SWS-authenticated requests and delegates
 * all processing to the DataSource servlet.
 *
 * <p>
 * Legacy requests (HTML files, manual processes) are now handled by LegacyProcessServlet
 * to maintain clear separation of responsibilities between authenticated modern APIs
 * and legacy processes.
 * </p>
 *
 */
public class ForwarderServlet implements WebService {
    private static final Logger log4j = LogManager.getLogger(ForwarderServlet.class);
    /**
     * Main entry point for HTTP requests. All requests are forwarded to DataSourceServlet
     * as this servlet is exclusively for modern SWS-authenticated API endpoints.
     *
     * @param req  the HttpServletRequest object
     * @param res  the HttpServletResponse object
     * @throws IOException if an input or output error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void process(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            // All requests are forwarded to DataSourceServlet for modern API processing
            processForwardRequest(req, res);
        } catch (IOException | ServletException e) {
            log4j.error("Error processing forward request: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delegates all requests to DataSourceServlet for processing.
     * This method handles modern API endpoints that require SWS authentication.
     *
     * @param request  the HttpServletRequest
     * @param response the HttpServletResponse
     * @throws IOException if an input or output error occurs
     * @throws ServletException if a servlet error occurs
     */
    private void processForwardRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        WeldUtils.getInstanceFromStaticBeanManager(org.openbravo.service.datasource.DataSourceServlet.class)
                .doGet(request, response);
    }

    @Override
    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    @Override
    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    @Override
    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    @Override
    public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }
}