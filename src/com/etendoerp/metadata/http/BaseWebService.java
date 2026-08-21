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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;
import org.openbravo.service.web.WebService;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.service.ServiceFactory;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.PasswordExpirationUtils;

/**
 * Abstract base class that provides common HTTP method implementations
 * for WebService implementations. All HTTP methods delegate to the
 * abstract {@link #process(HttpServletRequest, HttpServletResponse)} method.
 *
 * <p>This class eliminates code duplication across different servlet implementations
 * by providing a single point of delegation for all HTTP verbs.</p>
 *
 * <p>Every verb goes through {@link #dispatch(HttpServletRequest, HttpServletResponse)}, which
 * rejects the request when the caller's password has expired. This mirrors Etendo Classic, where a
 * user with an expired password never gets a usable session until the password is updated.</p>
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
     *
     * @throws Exception if the password is expired or the request processing fails
     */
    @Override
    public void doGet(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        dispatch(request, response);
    }

    /**
     * Handles HTTP POST requests by delegating to the process method.
     *
     * @throws Exception if the password is expired or the request processing fails
     */
    @Override
    public void doPost(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        dispatch(request, response);
    }

    /**
     * Handles HTTP DELETE requests by delegating to the process method.
     *
     * @throws Exception if the password is expired or the request processing fails
     */
    @Override
    public void doDelete(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        dispatch(request, response);
    }

    /**
     * Handles HTTP PUT requests by delegating to the process method.
     *
     * @throws Exception if the password is expired or the request processing fails
     */
    @Override
    public void doPut(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        dispatch(request, response);
    }

    /**
     * Handles HTTP PATCH requests by delegating to the process method.
     *
     * @throws Exception if the password is expired or the request processing fails
     */
    public void doPatch(String path, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        dispatch(request, response);
    }

    /**
     * Applies the expired-password guard and then runs the actual request processing.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @throws Exception if the password is expired or the request processing fails
     */
    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        rejectIfPasswordExpired(request);
        process(request, response);
    }

    /**
     * Rejects the request when the authenticated user must change an expired password, unless the
     * requested path is one of the bootstrap endpoints the client needs to render the mandatory
     * password-change screen (see {@link Constants#PASSWORD_EXPIRED_ALLOWED_PATHS}).
     *
     * @param request the HTTP request being served
     * @throws UnauthorizedException if the password is expired and the path is not allowed
     */
    private void rejectIfPasswordExpired(HttpServletRequest request) {
        if (Constants.PASSWORD_EXPIRED_ALLOWED_PATHS.contains(ServiceFactory.normalizePath(request))) {
            return;
        }

        OBContext context = OBContext.getOBContext();

        if (context != null && PasswordExpirationUtils.isExpired(context.getUser())) {
            throw new UnauthorizedException(Constants.PASSWORD_EXPIRED_ERROR);
        }
    }
}