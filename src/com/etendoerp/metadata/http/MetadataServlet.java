package com.etendoerp.metadata.http;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        super.service(req, res);
        super.serviceInitialized(RequestContext.get().getRequest(), RequestContext.get().getResponse());
    }

    private void process(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        ServiceFactory.getService(this, req, res).process();
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
}
