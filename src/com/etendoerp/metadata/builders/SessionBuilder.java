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
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.DimensionDisplayUtility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

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
     * Loads active warehouses whose organization belongs to the role's org set,
     * matching Classic's RoleInfo.getOrganizationWarehouses() query.
     */
    private static final String WAREHOUSES_BY_ORGANIZATION_HQL =
        "select w from Warehouse w"
            + " where w.active = true"
            + " and w.organization.id in (:orgIds)"
            + " and w.client.id = :clientId"
            + " and w.organization.active = true"
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

            Set<String> allOrgIds = new LinkedHashSet<>();
            String clientId = collectOrgIdsAndClient(userRoleList, allOrgIds);
            List<Warehouse> pooledWarehouses = fetchWarehouses(allOrgIds, clientId);
            OrganizationStructureProvider osp = clientId != null
                ? OBContext.getOBContext().getOrganizationStructureProvider(clientId)
                : null;

            for (UserRoles userRole : userRoleList) {
                JSONObject json = new JSONObject();
                Role role = userRole.getRole();
                Client client = role.getClient();

                // Scoped to this role's own granted organizations only - pooling every role's
                // orgIds together (as before) let a warehouse granted to a DIFFERENT role leak
                // into this role's organization bucket whenever this role's org happened to be
                // an ancestor, in the org tree, of that other role's org.
                Set<String> roleOrgIds = collectRoleOrgIds(role);
                Map<String, List<Warehouse>> warehousesByOrganization = osp != null
                    ? distributeByDescendantTree(pooledWarehouses, roleOrgIds, osp)
                    : Collections.emptyMap();

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
     * Fetches the warehouses belonging to any of the given organizations (pooled across every
     * role the user has, as a single round trip). Callers must still scope eligibility to a
     * single role's own granted organizations when distributing these via
     * {@link #distributeByDescendantTree(List, Set, OrganizationStructureProvider)} - this method
     * only avoids the N+1 query, it does not decide which role a warehouse belongs to.
     */
    private List<Warehouse> fetchWarehouses(Set<String> orgIds, String clientId) {
        if (orgIds.isEmpty() || clientId == null) {
            return Collections.emptyList();
        }

        try {
            return OBDal.getInstance().getSession()
                .createQuery(WAREHOUSES_BY_ORGANIZATION_HQL, Warehouse.class)
                .setParameter("orgIds", orgIds)
                .setParameter("clientId", clientId)
                .list();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String collectOrgIdsAndClient(List<UserRoles> userRoleList, Set<String> orgIds) {
        String clientId = null;
        for (UserRoles userRole : userRoleList) {
            try {
                if (clientId == null) {
                    clientId = userRole.getRole().getClient().getId();
                }
                for (RoleOrganization roleOrg : userRole.getRole().getADRoleOrganizationList()) {
                    orgIds.add(roleOrg.getOrganization().getId());
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return clientId;
    }

    /**
     * Collects the organization ids explicitly granted to a single role, tolerating an
     * inaccessible or failing {@code getADRoleOrganizationList()} by returning whatever was
     * gathered so far instead of failing the whole roles/organizations tree build.
     *
     * @param role the role to collect granted organization ids for
     * @return the role's own organization ids, or an empty set if they couldn't be loaded
     */
    private Set<String> collectRoleOrgIds(Role role) {
        Set<String> roleOrgIds = new LinkedHashSet<>();
        try {
            for (RoleOrganization roleOrg : role.getADRoleOrganizationList()) {
                roleOrgIds.add(roleOrg.getOrganization().getId());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return roleOrgIds;
    }

    /**
     * Distributes warehouses across organizations using each org's descendant subtree,
     * replicating Classic's RoleInfo behavior: a warehouse is added to a bucket org (from
     * {@code orgIds}) when the warehouse's own organization is that org itself or one of its
     * descendants - covering the legitimate case of a parent org exposing the warehouses of its
     * child orgs. {@code orgIds} must be a single role's own granted organizations, never a set
     * pooled across multiple roles, since membership is what scopes eligibility.
     * <p>
     * Deliberately uses {@link OrganizationStructureProvider#getChildTree(String, boolean)}
     * rather than {@link OrganizationStructureProvider#getNaturalTree(String)}: the natural tree
     * also matches ancestors, and organization {@code "0"} (the {@code *} org) is defined as an
     * ancestor of every organization ({@code isInNaturalTree} special-cases it to always match).
     * Using the natural tree here would attribute any warehouse owned by org {@code "0"} to
     * every role's every granted organization, regardless of whether that role actually has
     * {@code "0"} among its own granted orgIds. The child tree only matches descendants (plus
     * the org itself), so it keeps the intended parent-sees-child-warehouse behavior without
     * that false positive.
     */
    private Map<String, List<Warehouse>> distributeByDescendantTree(
            List<Warehouse> warehouses, Set<String> orgIds, OrganizationStructureProvider osp) {
        Map<String, List<Warehouse>> result = new HashMap<>();
        for (String orgId : orgIds) {
            result.put(orgId, new ArrayList<>());
        }
        for (Warehouse wh : warehouses) {
            String whOrgId = wh.getOrganization().getId();
            for (String orgId : orgIds) {
                if (osp.getChildTree(orgId, true).contains(whOrgId)) {
                    result.get(orgId).add(wh);
                }
            }
        }
        return result;
    }

    private JSONArray getOrganizations(Role role, Map<String, List<Warehouse>> warehousesByOrganization) {
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

    private JSONArray getWarehouses(Organization organization, Map<String, List<Warehouse>> warehousesByOrganization) {
        JSONArray warehouses = new JSONArray();

        try {
            List<Warehouse> orgWarehouses = warehousesByOrganization.getOrDefault(organization.getId(),
                Collections.emptyList());

            for (Warehouse warehouse : orgWarehouses) {
                JSONObject json = new JSONObject();

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
     * Otherwise returns the first valid warehouse for the current organization, or {@code null}
     * if that organization has none - a warehouse from an unrelated organization (e.g. a stale
     * token, or a role/org whose own organization has no warehouses at all) must never be
     * returned as-is, or it leaks into the UI as if it were valid for the current context.
     * <p>
     * The organization-scoped fallback deliberately uses
     * {@link SecureWebServicesUtils#getOrganizationWarehouses(Organization)} (warehouses whose
     * own organization is {@code organization} or a descendant of it) rather than
     * {@code organization.getOrganizationWarehouseList()} ({@code AD_ORG_WAREHOUSE}, i.e. which
     * warehouses the organization is merely assigned to use). Those are two different questions:
     * an organization can be assigned to use a warehouse owned by an unrelated organization
     * (e.g. the {@code *} org) without that warehouse being a valid *default* for it - Classic's
     * own profile widget (see {@code RoleInfo.getOrganizationWarehouses()}) only ever offers
     * warehouses resolved the first way, never via the assignment table.
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
        // Fallback: first warehouse owned by (or a descendant of) the current organization
        List<Warehouse> orgWarehouses = SecureWebServicesUtils.getOrganizationWarehouses(organization);
        if (!orgWarehouses.isEmpty()) {
            return orgWarehouses.get(0);
        }
        return null;
    }
}
