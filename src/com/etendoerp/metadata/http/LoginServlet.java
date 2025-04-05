package com.etendoerp.metadata.http;

import com.etendoerp.metadata.auth.LoginManager;
import org.apache.http.entity.ContentType;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class LoginServlet extends HttpBaseServlet {
    private final LoginManager loginService = new LoginManager();

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(new HttpServletRequestWrapper(request), response);
    }

    @Override
    public void doOptions(HttpServletRequest request, HttpServletResponse response) {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
        OBContext.setAdminMode(true);
        JSONObject result;

        try {
            result = loginService.processLogin(request);
        } catch (Exception e) {
            result = loginService.buildErrorResponse(e);
        } finally {
            OBContext.restorePreviousMode();
        }

        writeResponse(response, result);
    }

    private void writeResponse(HttpServletResponse response, JSONObject result) throws IOException {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (Writer out = response.getWriter()) {
            out.write(result.toString());
        }
    }
}
