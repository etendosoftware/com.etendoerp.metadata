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

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.service.ServiceFactory;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.PasswordExpirationUtils;
import com.etendoerp.metadata.utils.Utils;

/**
 * Abstract base class that provides common HTTP method implementations
 * for WebService implementations. All HTTP methods delegate to the
 * abstract {@link #process(HttpServletRequest, HttpServletResponse)} method.
 *
 * <p>This class eliminates code duplication across different servlet implementations
 * by providing a single point of delegation for all HTTP verbs.</p>
 *
 * <p>Every verb goes through {@link #dispatch(HttpServletRequest, HttpServletResponse)}, which first
 * rejects the request outright if the caller's token was revoked (e.g. via {@code /logout}), then
 * rejects it when the caller's password has expired. This mirrors Etendo Classic, where a
 * user with an expired password never gets a usable session until the password is updated.</p>
 *
 * <p><b>Revocation only covers this module.</b> The check queries {@link TokenRevocationStore},
 * which only ever contains {@code jti}s revoked via this module's own {@code /logout}. A token
 * accepted here but never routed through this module's own login (e.g. one minted directly by
 * classic {@code /sws/login}, which sets no {@code jti} claim at all) can never be revoked by
 * this mechanism, by design — see {@code com.etendoerp.metadata.service.LogoutService} and the
 * JWT logout revocation design spec for the full reasoning. Other {@code /sws/*} services outside
 * this module are unaffected either way: a revoked token still works against them.</p>
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
     * Applies the revoked-token guard, then the expired-password guard, and finally runs the
     * actual request processing.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @throws Exception if the password is expired or the request processing fails
     */
    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (isTokenRevoked(request)) {
            Utils.writeJsonErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            return;
        }
        rejectIfPasswordExpired(request);
        process(request, response);
    }

    /**
     * Checks the caller's {@code jti} against {@link TokenRevocationStore}. Deliberately does
     * <b>not</b> throw {@code UnauthorizedException} on a hit - an exception thrown from here
     * (before {@link #process}) never reaches this module's own exception-to-status mapping (see
     * the design spec, "Why not throw UnauthorizedException") - the caller must write the 401
     * response itself and return without calling {@link #process}.
     *
     * @param request the HTTP request
     * @return {@code true} if the request's token is revoked
     */
    private boolean isTokenRevoked(HttpServletRequest request) {
        DecodedJWT decoded = com.etendoerp.metadata.auth.Utils.decodeBearerToken(request);
        if (decoded == null) {
            return false;
        }
        String jti = decoded.getClaim("jti").asString();
        return TokenRevocationStore.isRevoked(jti);
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