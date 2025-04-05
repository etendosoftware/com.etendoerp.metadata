package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ToolbarService extends BaseService {
    public ToolbarService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String path = request.getPathInfo();
        String[] pathParts = path.split("/");

        if (pathParts.length < 3) {
            throw new UnprocessableContentException("Invalid toolbar path");
        }

        String windowId = pathParts[2];
        String tabId = (pathParts.length >= 4 && !"undefined".equals(pathParts[3])) ? pathParts[3] : null;
        JSONObject toolbar = fetchToolbar(windowId, tabId);
        write(toolbar);
    }

    private JSONObject fetchToolbar(String windowId, String tabId) {
        return new ToolbarBuilder(OBContext.getOBContext().getLanguage().getLanguage(),
                                  windowId,
                                  tabId,
                                  false).toJSON();
    }
}
