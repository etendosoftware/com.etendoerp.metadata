package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

public class SessionBuilder {
    private final Logger logger;
    private final DataToJsonConverter converter;

    public SessionBuilder() {
        logger = LogManager.getLogger(this.getClass());
        converter = new DataToJsonConverter();
    }

    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject();
            User user = OBContext.getOBContext().getUser();
            Role role = OBContext.getOBContext().getRole();

            json.put("user", converter.toJsonObject(user, DataResolvingMode.FULL_TRANSLATABLE));
            json.put("role", converter.toJsonObject(role, DataResolvingMode.FULL_TRANSLATABLE));

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }
}
