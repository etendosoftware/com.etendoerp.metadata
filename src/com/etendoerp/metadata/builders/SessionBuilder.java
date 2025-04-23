package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.exceptions.Utils.getJsonObject;

import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
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
    private final RequestVariables vars;

    public SessionBuilder(RequestVariables vars) {
        this.vars = vars;
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
        JSONArray result = new JSONArray();

        try {
            List<UserRoles> userRoleList = user.getADUserRolesList();

            for (UserRoles userRole : userRoleList) {
                JSONObject role = new JSONObject();

                role.put("id", userRole.getRole().getId());
                role.put("name", userRole.getRole().getName());
                role.put("organizations", getOrganizations(userRole.getRole()));

                result.put(role);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return result;
    }

    private JSONArray getOrganizations(Role role) {
        JSONArray result = new JSONArray();

        try {
            List<RoleOrganization> roleOrgList = role.getADRoleOrganizationList();

            for (RoleOrganization roleOrg : roleOrgList) {
                JSONObject org = new JSONObject();

                org.put("id", roleOrg.getOrganization().getId());
                org.put("name", roleOrg.getOrganization().getName());
                org.put("warehouses", getWarehouses(roleOrg.getOrganization()));

                result.put(org);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return result;
    }

    private JSONArray getWarehouses(Organization organization) {
        JSONArray result = new JSONArray();

        try {
            List<OrgWarehouse> orgWarehouses = organization.getOrganizationWarehouseList();

            for (OrgWarehouse orgWarehouse : orgWarehouses) {
                JSONObject org = new JSONObject();

                org.put("id", orgWarehouse.getWarehouse().getId());
                org.put("name", orgWarehouse.getWarehouse().getName());

                result.put(org);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return result;
    }
}
