package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.MenuBuilder;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    public static final String TOOLBAR_PATH = "/toolbar";
    public static final String SESSION_PATH = "/session";
    public static final String MENU_PATH = "/menu";
    public static final String WINDOW_PATH = "/window/";
    private static final Logger logger = LogManager.getLogger(MetadataServlet.class);
    private static final String KERNEL_CLIENT_PATH = "/org.openbravo.client.kernel";

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

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getPathInfo();

        if (path.startsWith(WINDOW_PATH)) {
            response.getWriter().write(this.fetchWindow(request.getPathInfo().substring(8)).toString());
        } else if (path.equals(MENU_PATH)) {
            response.getWriter().write(this.fetchMenu().toString());
        } else if (path.equals(SESSION_PATH)) {
            response.getWriter().write(this.fetchSession().toString());
        } else if (path.startsWith(TOOLBAR_PATH)) {
            handleToolbarRequest(response, path);
        } else if (path.startsWith(KERNEL_CLIENT_PATH)) {
            handleKernelRequest(request, response);
        } else {
            throw new NotFoundException();
        }
    }

    private void handleKernelRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        WeldUtils.getInstanceFromStaticBeanManager(org.openbravo.client.kernel.KernelServlet.class).doGet(request, response);
    }

    private void handleToolbarRequest(HttpServletResponse response, String path) {
        String[] pathParts = path.split("/");
        if (pathParts.length < 3) {
            throw new UnprocessableContentException("Invalid toolbar path");
        }
        try {
            String windowId = pathParts[2];
            String tabId = (pathParts.length >= 4 && !"undefined".equals(pathParts[3])) ? pathParts[3] : null;
            JSONObject toolbar = fetchToolbar(windowId, tabId);
            sendSuccessResponse(response, toolbar);
        } catch (IllegalArgumentException e) {
            throw new UnprocessableContentException(e.getMessage());
        } catch (Exception e) {
            throw new InternalServerException();
        }
    }

    private void sendSuccessResponse(HttpServletResponse response, JSONObject data) throws IOException {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("response", data);
            response.getWriter().write(wrapper.toString());
        } catch (JSONException e) {
            logger.error("Error creating success response", e);
            sendErrorResponse(response);
        }
    }

    private void sendErrorResponse(HttpServletResponse response) throws IOException {
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

    private JSONObject fetchToolbar(String windowId, String tabId) {
        try {
            String language = OBContext.getOBContext().getLanguage().getLanguage();
            boolean isNew = false;
            ToolbarBuilder toolbarBuilder = new ToolbarBuilder(language, windowId, tabId, isNew);
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