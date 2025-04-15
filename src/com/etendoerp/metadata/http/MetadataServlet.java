package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;
import static com.etendoerp.metadata.utils.Utils.initializeGlobalConfig;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.service.json.JsonUtils;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            super.service(HttpServletRequestWrapper.wrap(req), res);
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            res.setStatus(getResponseStatus(e));
            res.getWriter().write(JsonUtils.convertExceptionToJson(e));
        }
    }

    private void process(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            ServiceFactory.getService(req, res).process();
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            res.setStatus(getResponseStatus(e));
            res.getWriter().write(JsonUtils.convertExceptionToJson(e));
        }
    }

    @Override
    public final void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        process(req, res);
    }

    @Override
    public final void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        process(req, res);
    }

    @Override
    public final void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        process(req, res);
    }

    @Override
    public final void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        process(req, res);
    }

    @Override
    public void init(ServletConfig config) {
        super.init(config);
        initializeGlobalConfig(config);
    }
}
