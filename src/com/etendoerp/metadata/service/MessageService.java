package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.erpCommon.utility.OBError;

import com.etendoerp.metadata.data.RequestVariables;

public class MessageService extends MetadataService {

    public MessageService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
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
        final VariablesSecureApp vars = new RequestVariables(getRequest());
        OBError error = vars.getMessage("186");
        vars.setMessage("186", null);
        JSONObject jsonResponse = new JSONObject();
        try {
            if (error != null) {
                jsonResponse.put("message", error.getMessage());
                jsonResponse.put("type", error.getType());
                jsonResponse.put("title", error.getTitle());
            } else {
                jsonResponse.put("message", "");
            }
            setCORSHeaders(getRequest(), getResponse());
        } catch (Exception e) {
            throw new OBException("Error while processing message", e);
        }
        write(jsonResponse);
    }
}
