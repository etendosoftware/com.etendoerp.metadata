package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Utils.initializeGlobalConfig;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        req = HttpServletRequestWrapper.wrap(req);
        req.setAttribute(AuthenticationManager.STATELESS_REQUEST_PARAMETER, "true");
        super.service(req, res);
    }

    private void process(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        ServiceFactory.getService(req, res).process();
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
