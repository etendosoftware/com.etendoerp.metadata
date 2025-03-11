package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.DimensionDisplayUtility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.json.DataResolvingMode;

import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.Map;

/**
 * @author luuchorocha
 */
public class SessionBuilder extends Builder {
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
            json.put("configuration", getConfiguration());

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private Map<String, String> getConfiguration() {
        return DimensionDisplayUtility.getAccountingDimensionConfiguration(OBContext.getOBContext().getCurrentClient());
    }

    private JSONObject buildSessionJSON() throws JSONException {
        JSONObject result = new JSONObject();
        HttpSession session = RequestContext.get().getRequest().getSession();

        for (Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements(); ) {
            String attribute = e.nextElement();
            result.put(attribute, session.getAttribute(attribute));
        }

        return result;
    }
}
