package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.utils.Utils.getJsonObject;

import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.RoleOrganization;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.OrgWarehouse;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * @author luuchorocha
 */
public class SessionBuilder extends Builder {
    private final RequestVariables vars = (RequestVariables) RequestContext.get().getVariablesSecureApp();

    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject();
            OBContext context = OBContext.getOBContext();
            User user = context.getUser();
            Role role = context.getRole();
            Organization organization = context.getCurrentOrganization();
            Client client = context.getCurrentClient();
            Warehouse warehouse = context.getWarehouse();

            json.put("user", getJsonObject(user));
            json.put("currentRole", getJsonObject(role));
            json.put("currentClient", getJsonObject(client));
            json.put("currentOrganization", getJsonObject(organization));
            json.put("currentWarehouse", getJsonObject(warehouse));
            json.put("roles", getRoles(user));
            json.put("attributes", vars.getCasedSessionAttributes());
            json.put("languages", new LanguageBuilder().toJSON());

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private JSONArray getRoles(User user) {
        JSONArray roles = new JSONArray();

        try {
            List<UserRoles> userRoleList = user.getADUserRolesList();

            for (UserRoles userRole : userRoleList) {
                JSONObject json = new JSONObject();
                Role role = userRole.getRole();

                json.put("id", role.getId());
                json.put("name", role.get(Role.PROPERTY_NAME, language, role.getId()));
                json.put("organizations", getOrganizations(role));

                roles.put(json);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return roles;
    }

    private JSONArray getOrganizations(Role role) {
        JSONArray organizations = new JSONArray();

        try {
            for (RoleOrganization roleOrg : role.getADRoleOrganizationList()) {
                JSONObject json = new JSONObject();
                Organization organization = roleOrg.getOrganization();

                json.put("id", organization.getId());
                json.put("name", organization.get(Organization.PROPERTY_NAME, language, organization.getId()));
                json.put("warehouses", getWarehouses(organization));

                organizations.put(json);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return organizations;
    }

    private JSONArray getWarehouses(Organization organization) {
        JSONArray warehouses = new JSONArray();

        try {
            for (OrgWarehouse orgWarehouse : organization.getOrganizationWarehouseList()) {
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
}
