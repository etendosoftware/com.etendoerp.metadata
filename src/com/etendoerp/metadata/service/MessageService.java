package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.erpCommon.utility.OBError;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MessageService extends MetadataService {
    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String MAX_AGE = "Access-Control-Max-Age";

    public MessageService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    protected void setCORSHeaders(HttpServletRequest request, HttpServletResponse response) throws
                                                                                            ServletException,
                                                                                            IOException {

        String origin = request.getHeader("Origin");

        if (origin != null && !origin.isEmpty()) {
            response.setHeader(ALLOW_ORIGIN, origin);
            response.setHeader(ALLOW_METHODS, "POST, GET, OPTIONS");
            response.setHeader(ALLOW_CREDENTIALS, "true");
            response.setHeader(ALLOW_HEADERS, "Content-Type, origin, accept, X-Requested-With");
            response.setHeader(MAX_AGE, "1000");
        }
    }

    @Override
    public void process() {
        final VariablesSecureApp vars = new VariablesSecureApp(request);
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
            setCORSHeaders(request, response);
        } catch (Exception e) {
            throw new OBException("Error while processing message", e);
        }
        write(jsonResponse);
    }
}
