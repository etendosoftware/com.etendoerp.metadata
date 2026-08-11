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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
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
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Serves POST /meta/change-profile - issues a new JWT for a role/organization/warehouse change.
 * <p>
 * This exists specifically as a fallback for {@code /sws/login}: that endpoint requires the
 * selected organization to have at least one warehouse and fails with
 * {@code SMFSWS_OrgHasNoRole} otherwise, while Classic tolerates an organization with none (the
 * session's warehouse is simply left empty). {@link com.etendoerp.metadata.auth.Utils#generateToken}
 * already handles that case gracefully, so this service is only meant to be called by the
 * frontend when {@code /sws/login} fails with that specific error - regular role changes should
 * keep using {@code /sws/login}, which also synchronizes the classic {@code JSESSIONID} session
 * that legacy features (attachments, notes, printing) rely on; this endpoint does not.
 */
public class ChangeProfileService extends MetadataService {
    private static final String ROLE = "role";
    private static final String ORGANIZATION = "organization";
    private static final String WAREHOUSE = "warehouse";
    private static final String TOKEN = "token";

    /**
     * Creates a new ChangeProfileService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public ChangeProfileService(HttpServletRequest request, HttpServletResponse response) {
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
            User user = OBDal.getInstance().get(User.class, OBContext.getOBContext().getUser().getId());

            Role role = resolveRole(body);
            Organization organization = resolveOrganization(body);
            Warehouse warehouse = resolveWarehouse(body);
            Client client = role.getClient();

            AuthData authData = new AuthData(user, role, organization, warehouse, client);
            String token = Utils.generateToken(authData, null);

            write(new JSONObject().put(TOKEN, token));
        } catch (UnprocessableContentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error changing profile: {}", e.getMessage(), e);
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private Role resolveRole(JSONObject body) {
        String roleId = getStringOrNull(body, ROLE);
        String id = roleId != null ? roleId : OBContext.getOBContext().getRole().getId();
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
