package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

public class ToolbarService extends MetadataService {
    public ToolbarService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode();
            String path = getRequest().getPathInfo();
            String[] pathParts = path.split("/");

            if (pathParts.length < 3) {
                throw new UnprocessableContentException("Invalid toolbar path");
            }

            String windowId = pathParts[2];
            String tabId = (pathParts.length >= 4 && !"undefined".equals(pathParts[3])) ? pathParts[3] : null;
            JSONObject toolbar = fetchToolbar(windowId, tabId);
            write(toolbar);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject fetchToolbar(String windowId, String tabId) {
        return new ToolbarBuilder(OBContext.getOBContext().getLanguage().getLanguage(), windowId, tabId,
            false).toJSON();
    }
}
