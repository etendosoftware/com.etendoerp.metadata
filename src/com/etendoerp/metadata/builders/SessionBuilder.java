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

package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.utils.Utils.getJsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.DimensionDisplayUtility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * Builds a JSON representation of the current user session including roles, organizations, and warehouses.
 */
public class SessionBuilder extends Builder {

    /** Caches the built roles/organizations/warehouses tree per user, avoiding a rebuild on every request. */
    private static final Map<String, String> ROLES_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads a user's roles together with their role-organization links and organizations in a
     * single round trip, replacing the lazy {@code user.getADUserRolesList()} ->
     * {@code role.getADRoleOrganizationList()} navigation that issued one query per role.
     */
    private static final String ROLES_BY_USER_HQL =
        "select distinct ur from ADUserRoles ur"
            + " join fetch ur.role ro"
            + " join fetch ro.client cl"
            + " left join fetch ro.aDRoleOrganizationList roOrg"
            + " left join fetch roOrg.organization org"
            + " where ur.userContact.id = :userId"
            + " order by ro.name, org.name";

    /**
     * Loads the warehouses for every organization referenced by the user's roles in a single
     * round trip, replacing the per-organization {@code organization.getOrganizationWarehouseList()}
     * lazy navigation.
     */
    private static final String WAREHOUSES_BY_ORGANIZATION_HQL =
        "select distinct ow from OrganizationWarehouse ow"
            + " join fetch ow.warehouse w"
            + " where ow.organization.id in (:orgIds)"
            + " order by w.name";

    /**
     * Clears the cached roles tree for every user. Invoked by
     * {@link com.etendoerp.metadata.cache.SessionCacheInvalidationObserver} when a role,
     * role-organization, or organization-warehouse assignment changes.
     */
    public static void clearRolesCache() {
        ROLES_CACHE.clear();
    }

    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject();
            OBContext context = OBContext.getOBContext();
            User user = context.getUser();
            Role role = context.getRole();
            Organization organization = context.getCurrentOrganization();
            Client client = context.getCurrentClient();
            Warehouse warehouse = context.getWarehouse();
            // ponytail: if warehouse doesn't belong to the resolved org, pick one that does
            warehouse = validateWarehouseForOrg(warehouse, organization, role);

            json.put("user", getJsonObject(user));
            json.put("currentRole", getJsonObject(role));
            json.put("currentClient", getJsonObject(client));
            json.put("currentOrganization", getJsonObject(organization));
            json.put("currentWarehouse", getJsonObject(warehouse));
            json.put("roles", getRoles(user));
            json.put("languages", new LanguageBuilder().toJSON());
            json.put("attributes", buildAcctDimensionSessionAttributes(client));

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private JSONArray getRoles(User user) {
        JSONArray roles = new JSONArray();

        try {
            String cacheKey = user.getId();
            String cached = ROLES_CACHE.get(cacheKey);
            if (cached != null) {
                return new JSONArray(cached);
            }

            List<UserRoles> userRoleList = OBDal.getInstance().getSession()
                .createQuery(ROLES_BY_USER_HQL, UserRoles.class)
                .setParameter("userId", cacheKey)
                .list();

            Map<String, List<OrgWarehouse>> warehousesByOrganization = getWarehousesByOrganization(userRoleList);

            for (UserRoles userRole : userRoleList) {
                JSONObject json = new JSONObject();
                Role role = userRole.getRole();
                Client client = role.getClient();

                json.put("id", role.getId());
                json.put("name", role.get(Role.PROPERTY_NAME, language, role.getId()));
                json.put("organizations", getOrganizations(role, warehousesByOrganization));
                json.put("client", client.get(Client.PROPERTY_NAME, language, client.getId()));

                roles.put(json);
            }

            ROLES_CACHE.put(cacheKey, roles.toString());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return roles;
    }

    /**
     * Batches the warehouse lookup for every organization referenced by the given roles into a
     * single query, keyed by organization id, instead of lazily loading warehouses per organization.
     */
    private Map<String, List<OrgWarehouse>> getWarehousesByOrganization(List<UserRoles> userRoleList) {
        Set<String> orgIds = new LinkedHashSet<>();
        for (UserRoles userRole : userRoleList) {
            try {
                for (RoleOrganization roleOrg : userRole.getRole().getADRoleOrganizationList()) {
                    orgIds.add(roleOrg.getOrganization().getId());
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        if (orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<OrgWarehouse> orgWarehouses = OBDal.getInstance().getSession()
                .createQuery(WAREHOUSES_BY_ORGANIZATION_HQL, OrgWarehouse.class)
                .setParameter("orgIds", orgIds)
                .list();

            Map<String, List<OrgWarehouse>> warehousesByOrganization = new HashMap<>();
            for (OrgWarehouse orgWarehouse : orgWarehouses) {
                warehousesByOrganization
                    .computeIfAbsent(orgWarehouse.getOrganization().getId(), id -> new ArrayList<>())
                    .add(orgWarehouse);
            }

            return warehousesByOrganization;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            return Collections.emptyMap();
        }
    }

    private JSONArray getOrganizations(Role role, Map<String, List<OrgWarehouse>> warehousesByOrganization) {
        JSONArray organizations = new JSONArray();

        try {
            for (RoleOrganization roleOrg : role.getADRoleOrganizationList()) {
                JSONObject json = new JSONObject();
                Organization organization = roleOrg.getOrganization();

                json.put("id", organization.getId());
                json.put("name", organization.get(Organization.PROPERTY_NAME, language, organization.getId()));
                json.put("warehouses", getWarehouses(organization, warehousesByOrganization));

                organizations.put(json);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return organizations;
    }

    /**
     * Builds the session attributes object exposed to the new client, mirroring
     * the keys classic UI populates at login (see
     * {@code LoginUtils#fillSessionArguments}). The new UI evaluates per-field
     * {@code gridDisplayLogicExpression} that classic rewrites against these
     * keys (e.g. {@code $Element_BP_APP_L}); without them, accounting-dimension
     * columns in P&E grids cannot resolve visibility.
     *
     * @param client The current AD Client
     * @return JSON map of session attribute name → "Y"/"N" string value
     */
    private JSONObject buildAcctDimensionSessionAttributes(Client client) throws JSONException {
        JSONObject attributes = new JSONObject();
        if (client == null) {
            return attributes;
        }
        boolean isCentrallyMaintained = client.isAcctdimCentrallyMaintained();
        attributes.put(DimensionDisplayUtility.IsAcctDimCentrally, isCentrallyMaintained ? "Y" : "N");

        if (isCentrallyMaintained) {
            Map<String, String> acctDimMap = DimensionDisplayUtility.getAccountingDimensionConfiguration(client);
            for (Map.Entry<String, String> entry : acctDimMap.entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
        }

        return attributes;
    }

    private JSONArray getWarehouses(Organization organization, Map<String, List<OrgWarehouse>> warehousesByOrganization) {
        JSONArray warehouses = new JSONArray();

        try {
            List<OrgWarehouse> orgWarehouses = warehousesByOrganization.getOrDefault(organization.getId(),
                Collections.emptyList());

            for (OrgWarehouse orgWarehouse : orgWarehouses) {
                JSONObject json = new JSONObject();
                Warehouse warehouse = orgWarehouse.getWarehouse();

                json.put("id", warehouse.getId());
                json.put("name", warehouse.get(Warehouse.PROPERTY_NAME, language, warehouse.getId()));

                warehouses.put(json);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return warehouses;
    }

    /**
     * Returns the given warehouse if it belongs to an organization accessible by the role.
     * Otherwise returns the first valid warehouse for the current organization.
     * ponytail: guard against cross-org warehouse from stale token, pick org-scoped fallback
     */
    private Warehouse validateWarehouseForOrg(Warehouse warehouse, Organization organization, Role role) {
        if (warehouse == null) {
            return null;
        }
        String whOrgId = warehouse.getOrganization().getId();
        boolean belongsToRole = role.getADRoleOrganizationList().stream()
            .anyMatch(ro -> ro.getOrganization().getId().equals(whOrgId));
        if (belongsToRole) {
            return warehouse;
        }
        // Fallback: first warehouse linked to the current organization
        List<OrgWarehouse> orgWarehouses = organization.getOrganizationWarehouseList();
        if (!orgWarehouses.isEmpty()) {
            return orgWarehouses.get(0).getWarehouse();
        }
        return warehouse;
    }
}
