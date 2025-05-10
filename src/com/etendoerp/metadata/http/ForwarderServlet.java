package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.utils.ServletRegistry;

/**
 * @author luuchorocha
 */
public class ForwarderServlet extends BaseServlet {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        super.service(req, res, false);

        HttpSecureAppServlet servlet = ServletRegistry.getDelegatedServlet(this, req.getPathInfo());
        initializeSession();
        servlet.service(RequestContext.get().getRequest(), RequestContext.get().getResponse());
    }
}
