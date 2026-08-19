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

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Request-body resolution helpers shared by the JWT-issuing services
 * ({@link LoginService} and {@link ChangeProfileService}), which both resolve an
 * organization/warehouse combination from a request body before minting a token via
 * {@link com.etendoerp.metadata.auth.Utils#generateToken}.
 */
final class ProfileRequestUtils {
    private ProfileRequestUtils() { }

    /**
     * Reads a string value from the body, treating missing, null, and empty values alike as absent.
     *
     * @param body the parsed request body
     * @param key   the key to read
     * @return the value, or null if absent
     */
    static String getStringOrNull(JSONObject body, String key) {
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

    /**
     * Resolves the organization identified by {@code key} in the body, if present.
     *
     * @param body the parsed request body
     * @param key   the key holding the organization id
     * @return the resolved organization, or null if the key is absent
     * @throws UnprocessableContentException if the id does not resolve to an organization
     */
    static Organization resolveOrganization(JSONObject body, String key) {
        String organizationId = getStringOrNull(body, key);
        if (organizationId == null) {
            return null;
        }

        Organization organization = OBDal.getInstance().get(Organization.class, organizationId);
        if (organization == null) {
            throw new UnprocessableContentException("Invalid organization: " + organizationId);
        }

        return organization;
    }

    /**
     * Resolves the warehouse identified by {@code key} in the body, if present.
     *
     * @param body the parsed request body
     * @param key   the key holding the warehouse id
     * @return the resolved warehouse, or null if the key is absent
     * @throws UnprocessableContentException if the id does not resolve to a warehouse
     */
    static Warehouse resolveWarehouse(JSONObject body, String key) {
        String warehouseId = getStringOrNull(body, key);
        if (warehouseId == null) {
            return null;
        }

        Warehouse warehouse = OBDal.getInstance().get(Warehouse.class, warehouseId);
        if (warehouse == null) {
            throw new UnprocessableContentException("Invalid warehouse: " + warehouseId);
        }

        return warehouse;
    }
}
