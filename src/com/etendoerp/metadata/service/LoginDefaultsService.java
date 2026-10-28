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
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.auth.LoginDefaultsResolver;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;

/**
 * Serves {@code GET /meta/login-defaults} - returns the role, organization, warehouse and client
 * the authenticated user would log in with in Classic (see {@link LoginDefaultsResolver}), plus a
 * {@code current} flag telling whether the caller's token already uses them.
 * <p>
 * The New UI's password login goes through {@code /sws/login}, which does not follow the Classic
 * rule when the request names no role. Right after that login the frontend asks this endpoint for
 * the Classic defaults and, when {@code current} is {@code false}, switches the brand new token to
 * them, so both UIs start with the same role.
 */
public class LoginDefaultsService extends MetadataService {
    private static final String ROLE = "role";
    private static final String ORGANIZATION = "organization";
    private static final String WAREHOUSE = "warehouse";
    private static final String CLIENT = "client";
    private static final String CURRENT = "current";

    /**
     * Creates a new LoginDefaultsService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LoginDefaultsService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"GET".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        try {
            OBContext.setAdminMode(true);
            OBContext context = OBContext.getOBContext();
            AuthData defaults = LoginDefaultsResolver.resolve(context.getUser());

            write(toJSON(defaults, isCurrent(defaults, context)));
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private static boolean isCurrent(AuthData defaults, OBContext context) {
        return sameId(defaults.getRole(), context.getRole())
            && sameId(defaults.getOrg(), context.getCurrentOrganization())
            && sameId(defaults.getWarehouse(), context.getWarehouse());
    }

    private static boolean sameId(BaseOBObject expected, BaseOBObject actual) {
        return Objects.equals(idOf(expected), idOf(actual));
    }

    private static Object idOf(BaseOBObject object) {
        if (object == null) {
            return null;
        }
        return object.getId();
    }

    private static JSONObject toJSON(AuthData defaults, boolean current) throws JSONException {
        return new JSONObject()
            .put(ROLE, idOf(defaults.getRole()))
            .put(ORGANIZATION, idOf(defaults.getOrg()))
            .put(WAREHOUSE, idOf(defaults.getWarehouse()))
            .put(CLIENT, idOf(defaults.getClient()))
            .put(CURRENT, current);
    }
}
