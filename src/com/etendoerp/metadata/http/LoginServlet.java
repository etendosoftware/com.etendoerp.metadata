package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;

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
import org.openbravo.service.json.JsonUtils;

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
            writeResponse(response, new LoginManager().processLogin(request));
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            response.setStatus(getResponseStatus(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
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
