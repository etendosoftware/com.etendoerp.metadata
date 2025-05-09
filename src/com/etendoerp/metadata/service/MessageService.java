package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.TAB_ID;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.erpCommon.utility.OBError;

import com.etendoerp.metadata.data.RequestVariables;

public class MessageService extends MetadataService {
    public MessageService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    protected void setCORSHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        if (origin != null && !origin.isEmpty()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, origin, accept, X-Requested-With");
            response.setHeader("Access-Control-Max-Age", "1000");
        }
    }

    @Override
    public void process() throws IOException {
        final HttpServletRequest req = getRequest();
        final HttpServletResponse res = getResponse();
        final VariablesSecureApp vars = new RequestVariables(req);
        final JSONObject jsonResponse = new JSONObject();

        final String tabId = req.getParameter(TAB_ID);
        final OBError error = vars.getMessage(tabId);

        try {
            vars.removeMessage(tabId);

            if (error != null) {
                jsonResponse.put("message", error.getMessage());
                jsonResponse.put("type", error.getType());
                jsonResponse.put("title", error.getTitle());
            } else {
                jsonResponse.put("message", "");
            }

            setCORSHeaders(req, res);
        } catch (Exception e) {
            throw new OBException("Error while processing message", e);
        }

        write(jsonResponse);
    }
}
