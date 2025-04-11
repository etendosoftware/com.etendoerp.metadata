package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;
import static org.openbravo.service.json.JsonUtils.convertExceptionToJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            super.service(new HttpServletRequestWrapper(request), response);
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            response.setStatus(getResponseStatus(e));
            response.getWriter().write(convertExceptionToJson(e));
        }
    }

    private void process(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        setContentHeaders(response);
        ServiceFactory.getService(this, request, response).process();
    }

    @Override
    public final void doGet(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        process(request, response);
    }

    @Override
    public final void doPost(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        process(request, response);
    }

    @Override
    public final void doPut(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        process(request, response);
    }

    @Override
    public final void doDelete(HttpServletRequest request,
        HttpServletResponse response) throws IOException, ServletException {
        process(request, response);
    }

    private void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

}
