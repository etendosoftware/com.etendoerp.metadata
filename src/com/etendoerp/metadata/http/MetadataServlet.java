package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;
import static com.etendoerp.metadata.utils.Utils.initializeGlobalConfig;
import static org.openbravo.service.json.JsonUtils.convertExceptionToJson;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;

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
        try {
            OBContext.setAdminMode();
            ServiceFactory.getService(this, request, response).process();
        } finally {
            OBContext context = OBContext.getOBContext();

            if (context != null && context.isInAdministratorMode()) {
                OBContext.restorePreviousMode();
            }
        }
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

    @Override
    public void init(ServletConfig config) {
        super.init(config);
        initializeGlobalConfig(config);
    }
}
