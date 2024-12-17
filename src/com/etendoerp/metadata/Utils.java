package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    public static JSONObject getBody(HttpServletRequest request) {
        try {
            String contentType = request.getContentType();

            if (null == contentType || !contentType.equals(ContentType.APPLICATION_JSON.getMimeType()))
                return new JSONObject();

            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line = reader.readLine();

            do sb.append(line); while ((line = reader.readLine()) != null);

            return new JSONObject(sb.toString());
        } catch (JSONException | IOException e) {
            logger.warn(e.getMessage());

            return new JSONObject();
        }
    }

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

