package com.etendoerp.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Language;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    public static Language getLanguage(HttpServletRequest request) {
        String[] providedLanguages = {request.getParameter("language"), request.getHeader("language"), request.getLocale().toString()};
        String languageCode = Arrays.stream(providedLanguages)
                                    .filter(language -> language != null && !language.isEmpty())
                                    .findFirst()
                                    .orElse(null);

        return (Language) OBDal.getInstance()
                               .createCriteria(Language.class)
                               .add(Restrictions.eq(Language.PROPERTY_SYSTEMLANGUAGE, true))
                               .add(Restrictions.eq(Language.PROPERTY_ACTIVE, true))
                               .add(Restrictions.eq(Language.PROPERTY_LANGUAGE, languageCode))
                               .uniqueResult();
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
            response.getWriter()
                    .write("{\"response\":{\"status\":500,\"error\":{\"message\":\"Internal server error\",\"messageType\":\"Error\",\"title\":\"\"},\"totalRows\":0}}");
        }
    }

    public static int getResponseStatus(Exception e) {
        switch (e.getClass().getSimpleName()) {
            case "OBSecurityException":
            case "UnauthorizedException":
                return 401;
            case "MethodNotAllowedException":
                return 405;
            case "UnprocessableContentException":
                return 422;
            default:
                return 500;
        }
    }
}
