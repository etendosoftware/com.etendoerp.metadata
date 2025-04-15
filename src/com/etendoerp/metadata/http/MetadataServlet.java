package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;
import static com.etendoerp.metadata.utils.Utils.initializeGlobalConfig;

import java.io.IOException;
import java.io.Writer;

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
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            super.service(req instanceof HttpServletRequestWrapper ? req : new HttpServletRequestWrapper(req), res);
        } catch (Exception e) {
            res.setStatus(getResponseStatus(e));
            Writer writer = res.getWriter();
            writer.write(JsonUtils.convertExceptionToJson(e));
            writer.close();
        }
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
