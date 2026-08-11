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
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Serves POST /meta/login - authenticates a user by username/password and issues a JWT,
 * independently of {@code /sws/login}.
 * <p>
 * This exists specifically as a fallback for the initial (username/password) login when the
 * user's default role's organization has no warehouses and no valid stored default warehouse:
 * {@code /sws/login}'s username/password path calls
 * {@code SecureWebServicesUtils.generateToken(user, role, org, warehouse)} directly, which does
 * not tolerate that case and fails with {@code SMFSWS_OrgHasNoRole} before a token is ever
 * issued - unlike a role switch on an already-authenticated session, there is no prior JWT to
 * fall back on here, so the frontend cannot recover without a login path of its own.
 * <p>
 * Authenticates via {@link PasswordHash#getUserWithPassword}, part of core (not
 * {@code com.smf.securewebservices}), so no pre-existing session or token is required. Role,
 * organization, and warehouse resolution then goes through
 * {@link com.etendoerp.metadata.auth.Utils#generateToken}, which already tolerates an
 * organization with no warehouses (see its {@code "0"} sentinel).
 */
public class LoginService extends MetadataService {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String ROLE = "role";
    private static final String ORGANIZATION = "organization";
    private static final String WAREHOUSE = "warehouse";
    private static final String TOKEN = "token";

    /**
     * Creates a new LoginService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public LoginService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"POST".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }

        try {
            OBContext.setAdminMode(true);

            JSONObject body = com.etendoerp.metadata.utils.Utils.getRequestData(getRequest());
            String username = getStringOrNull(body, USERNAME);
            String password = getStringOrNull(body, PASSWORD);

            if (username == null || password == null) {
                throw new UnprocessableContentException("username and password are required");
            }

            Optional<User> authenticatedUser = PasswordHash.getUserWithPassword(username, password);
            if (!authenticatedUser.isPresent()) {
                throw new UnauthorizedException("Invalid username or password");
            }
            User user = authenticatedUser.get();

            Role role = resolveRole(body, user);
            Organization organization = resolveOrganization(body);
            Warehouse warehouse = resolveWarehouse(body);
            Client client = role.getClient();

            AuthData authData = new AuthData(user, role, organization, warehouse, client);
            String token = com.etendoerp.metadata.auth.Utils.generateToken(authData, null);

            write(new JSONObject().put(TOKEN, token));
        } catch (UnauthorizedException | UnprocessableContentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during login: {}", e.getMessage(), e);
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private Role resolveRole(JSONObject body, User user) {
        String roleId = getStringOrNull(body, ROLE);
        String id = roleId != null ? roleId : (user.getDefaultRole() != null ? user.getDefaultRole().getId() : null);

        if (id == null) {
            throw new UnprocessableContentException("No role available for this user");
        }

        Role role = OBDal.getInstance().get(Role.class, id);
        if (role == null || role.getClient() == null) {
            throw new UnprocessableContentException("Invalid role: " + id);
        }

        return role;
    }

    private Organization resolveOrganization(JSONObject body) {
        String organizationId = getStringOrNull(body, ORGANIZATION);
        if (organizationId == null) {
            return null;
        }

        Organization organization = OBDal.getInstance().get(Organization.class, organizationId);
        if (organization == null) {
            throw new UnprocessableContentException("Invalid organization: " + organizationId);
        }

        return organization;
    }

    private Warehouse resolveWarehouse(JSONObject body) {
        String warehouseId = getStringOrNull(body, WAREHOUSE);
        if (warehouseId == null) {
            return null;
        }

        Warehouse warehouse = OBDal.getInstance().get(Warehouse.class, warehouseId);
        if (warehouse == null) {
            throw new UnprocessableContentException("Invalid warehouse: " + warehouseId);
        }

        return warehouse;
    }

    private String getStringOrNull(JSONObject body, String key) {
        if (!body.has(key) || body.isNull(key)) {
            return null;
        }

        try {
            String value = body.getString(key);
            return value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }
}
