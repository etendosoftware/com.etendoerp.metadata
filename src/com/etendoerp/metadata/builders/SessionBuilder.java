package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.data.RequestVariables;
import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.json.DataResolvingMode;

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
            OBDal dal = OBDal.getInstance();
            User user = dal.get(User.class, context.getUser().getId());
            Role role = dal.get(Role.class, context.getRole().getId());

            json.put("user", converter.toJsonObject(user, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("role", converter.toJsonObject(role, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("languages", new LanguageBuilder().toJSON());
            json.put("attributes", vars.getCasedSessionAttributes());

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }
}
