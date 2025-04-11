package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.json.DataResolvingMode;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * @author luuchorocha
 */
public class SessionBuilder extends Builder {
    private final RequestVariables vars;

    public SessionBuilder(RequestVariables vars) {
        super();
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

            json.put("user", converter.toJsonObject(user, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("currentRole", converter.toJsonObject(role, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("currentOrganization", converter.toJsonObject(organization, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("currentClient", converter.toJsonObject(client, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("currentWarehouse", converter.toJsonObject(warehouse, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("languages", new LanguageBuilder().toJSON());
            json.put("attributes", vars.getCasedSessionAttributes());

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }
}
