package com.etendoerp.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;

public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final int DEFAULT_WS_INACTIVE_INTERVAL = 60;
    private static Integer wsInactiveInterval = null;

    public static JSONObject getBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            return new JSONObject(sb.toString());
        } catch (JSONException | IOException e) {
            logger.warn(e.getMessage());

            return new JSONObject();
        }
    }

    public static String getToken(HttpServletRequest request) {
        String authStr = request.getHeader("Authorization");
        String token = null;

        if (authStr != null && authStr.startsWith("Bearer ")) {
            token = authStr.substring(7);
        }

        return token;
    }

    public static int getWSInactiveInterval() {
        if (wsInactiveInterval == null) {
            try {
                wsInactiveInterval = Integer.parseInt(OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty("ws.maxInactiveInterval", Integer.toString(DEFAULT_WS_INACTIVE_INTERVAL)));
            } catch (Exception e) {
                wsInactiveInterval = DEFAULT_WS_INACTIVE_INTERVAL;
            }
            logger.info("Sessions for WS calls expire after ".concat(wsInactiveInterval.toString()).concat(" seconds. This can be configured with ws.maxInactiveInterval property."));
        }

        return wsInactiveInterval;
    }

    public static String buildErrorJson(Exception e) {
        try {
            return new JSONObject(JsonUtils.convertExceptionToJson(e)).toString();
        } catch (Exception err) {
            logger.warn(err.getMessage());

            return "";
        }
    }

    public static String getLanguage(HttpServletRequest request) {
        JSONObject payload = getBody(request);
        String language = null;

        try {
            language = payload.getString("language") != null ? payload.getString("language") : "";
        } catch (JSONException e) {
            logger.warn(e.getMessage());
        }

        OBCriteria<Language> languageCriteria = OBDal.getInstance().createCriteria(Language.class);
        languageCriteria.add(Restrictions.eq(Language.PROPERTY_LANGUAGE, language));

        Language obLanguage = (Language) languageCriteria.uniqueResult();

        if (null != obLanguage) return obLanguage.getLanguage();
        else return null;
    }
}
