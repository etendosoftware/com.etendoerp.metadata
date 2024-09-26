package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
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

    public static String getLanguage(HttpServletRequest request) {
        try {
            return getBody(request).getString("language");
        } catch (JSONException e) {
            return null;
        }
    }
}
