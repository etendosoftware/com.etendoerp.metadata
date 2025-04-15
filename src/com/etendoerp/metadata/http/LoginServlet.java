package com.etendoerp.metadata.http;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.auth.LoginManager;

/**
 * @author luuchorocha
 */
public class LoginServlet extends HttpBaseServlet {
    @Override
    public final void service(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        super.service(HttpServletRequestWrapper.wrap(request), response);
    }

    @Override
    public void doOptions(HttpServletRequest request, HttpServletResponse response) {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);

        try {
            OBContext.setAdminMode(true);
            final LoginManager loginService = new LoginManager();
            final JSONObject result;
            writeResponse(response, loginService.processLogin(request));
        } finally {
            OBContext.restorePreviousMode();
        }

    }

    private void writeResponse(HttpServletResponse response, JSONObject result) throws IOException {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (Writer out = response.getWriter()) {
            out.write(result.toString());
        }
    }
}
