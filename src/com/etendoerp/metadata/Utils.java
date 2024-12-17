package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;

public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static String getToken(HttpServletRequest request) {
        String authStr = request.getHeader("Authorization");
        String token = null;

        if (authStr != null && authStr.startsWith("Bearer ")) token = authStr.substring(7);
        if (token == null) throw new UnauthorizedException();

        return token;
    }

    public static String buildErrorJson(Exception e) {
        try {
            return new JSONObject(JsonUtils.convertExceptionToJson(e)).toString();
        } catch (Exception err) {
            logger.warn(err.getMessage());

            return "";
        }
    }

    public static String getLanguageCode(HttpServletRequest request) {
        String[] providedLanguages = {request.getParameter("language"), request.getHeader("language"), request.getLocale().toString()};
        String languageCode = null;

        for (String language : providedLanguages) {
            if (language != null && !language.isEmpty()) {
                languageCode = language;
                break;
            }
        }

        Language language = OBDal.getInstance().createQuery(Language.class, "language = :languageCode").setNamedParameter("languageCode", languageCode).uniqueResult();

        if (language != null) {
            return language.getLanguage();
        }

        return null;
    }
}

