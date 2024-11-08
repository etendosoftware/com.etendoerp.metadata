package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.MenuBuilder;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.apache.http.entity.ContentType;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException {
        try {
            OBContext.setAdminMode(true);
            handleRequest(request, response);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.toString());

        if (path.startsWith("/window/")) {
            response.getWriter().write(this.fetchWindow(request.getPathInfo().substring(8)).toString());
        } else if (path.equals("/menu")) {
            response.getWriter().write(this.fetchMenu().toString());
        } else if (path.equals("/session")) {
            response.getWriter().write(this.fetchSession().toString());
        } else if (path.startsWith("/toolbar")) {
            String[] pathParts = path.split("/");
            if (pathParts.length < 3) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid toolbar path");
                return;
            }
            try {
                String windowId = pathParts[2];
                String tabId = (pathParts.length >= 4 && !"undefined".equals(pathParts[3])) ? pathParts[3] : null;
                JSONObject toolbar = fetchToolbar(windowId, tabId);
                sendSuccessResponse(response, toolbar);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing request");
            }
        } else {
            throw new NotFoundException("Not found");
        }

    }

    private void sendSuccessResponse(HttpServletResponse response, JSONObject data) throws IOException {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("response", data);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating success response", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing response");
        }
    }

    private void sendSuccessResponse(HttpServletResponse response, JSONArray data) throws IOException {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("response", data);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating success response", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing response");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        try {
            JSONObject error = new JSONObject();
            error.put("message", message);
            error.put("messageType", "Error");
            error.put("title", "");

            JSONObject responseObj = new JSONObject();
            responseObj.put("status", status);
            responseObj.put("error", error);
            responseObj.put("totalRows", 0);

            JSONObject wrapper = new JSONObject();
            wrapper.put("response", responseObj);

            response.setStatus(status);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating error response", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"response\":{\"status\":500,\"error\":{\"message\":\"Internal server error\",\"messageType\":\"Error\",\"title\":\"\"},\"totalRows\":0}}");
        }
    }

    private JSONObject fetchToolbar(String windowId, String tabId) {
        try {
            String language = OBContext.getOBContext().getLanguage().getLanguage();
            boolean isNew = false;
            ToolbarBuilder toolbarBuilder = new ToolbarBuilder(language, windowId, isNew);
            return toolbarBuilder.toJSON();
        } catch (Exception e) {
            logger.error("Error creating toolbar for window: {}", windowId, e);
            throw new RuntimeException("Error creating toolbar", e);
        }
    }

    private JSONObject fetchWindow(String id) {
        return new WindowBuilder(id).toJSON();
    }

    private JSONArray fetchMenu() {
        return new MenuBuilder().toJSON();
    }

    private JSONObject fetchSession() {
        return new SessionBuilder().toJSON();
    }
}