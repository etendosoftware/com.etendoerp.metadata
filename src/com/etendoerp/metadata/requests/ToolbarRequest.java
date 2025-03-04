package com.etendoerp.metadata.requests;

import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.etendoerp.metadata.Utils.sendSuccessResponse;

public class ToolbarRequest extends Request {
    public ToolbarRequest(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        String path = request.getPathInfo();
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
}
