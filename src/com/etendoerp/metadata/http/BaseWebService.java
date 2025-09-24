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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.service.web.WebService;

/**
 * Abstract base class that provides common HTTP method implementations
 * for WebService implementations. All HTTP methods delegate to the
 * abstract {@link #process(HttpServletRequest, HttpServletResponse)} method.
 *
 * <p>This class eliminates code duplication across different servlet implementations
 * by providing a single point of delegation for all HTTP verbs.</p>
 */
public abstract class BaseWebService implements WebService {

    /**
     * Abstract method that subclasses must implement to handle the actual
     * request processing logic.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @throws Exception if an error occurs during processing
     */
    protected abstract void process(HttpServletRequest request, HttpServletResponse response) throws Exception;

    /**
     * Handles HTTP GET requests by delegating to the process method.
     */
    @Override
    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    /**
     * Handles HTTP POST requests by delegating to the process method.
     */
    @Override
    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    /**
     * Handles HTTP DELETE requests by delegating to the process method.
     */
    @Override
    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }

    /**
     * Handles HTTP PUT requests by delegating to the process method.
     */
    @Override
    public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        process(request, response);
    }
}