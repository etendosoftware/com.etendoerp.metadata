package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import javax.servlet.http.HttpSession;
import java.util.Enumeration;

public class SessionBuilder extends Builder {
    private final DataToJsonConverter converter;

    public SessionBuilder() {
        converter = new DataToJsonConverter();
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
            json.put("session", buildSessionJSON());

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private JSONObject buildSessionJSON() throws JSONException {
        JSONObject result = new JSONObject();
        HttpSession session = RequestContext.get().getRequest().getSession();
        Enumeration<String> attributes = session.getAttributeNames();

        while (attributes.hasMoreElements()) {
            String attribute = attributes.nextElement();
            result.put(attribute, session.getAttribute(attribute));
        }

        return result;
    }
}
