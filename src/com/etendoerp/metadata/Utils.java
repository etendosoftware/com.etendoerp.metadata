package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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

    private static JSONObject getJsonObject() throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", "Error processing response");
        error.put("messageType", "Error");
        error.put("title", "");

        JSONObject responseObj = new JSONObject();
        responseObj.put("status", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        responseObj.put("error", error);
        responseObj.put("totalRows", 0);

        JSONObject wrapper = new JSONObject();
        wrapper.put("response", responseObj);

        return wrapper;
    }

    public static void sendSuccessResponse(HttpServletResponse response, JSONObject data) throws IOException {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("response", data);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating success response", e);
            sendErrorResponse(response);
        }
    }

    public static void sendErrorResponse(HttpServletResponse response) throws IOException {
        try {
            JSONObject wrapper = getJsonObject();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating error response", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"response\":{\"status\":500,\"error\":{\"message\":\"Internal server error\",\"messageType\":\"Error\",\"title\":\"\"},\"totalRows\":0}}");
        }
    }
}
