package com.etendoerp.metadata.service;

import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.weld.module.web.servlet.SessionHolder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class MetadataService {
    protected final Logger logger = LogManager.getLogger(this.getClass());
    protected final HttpServletRequestWrapper request;
    protected final HttpServletResponse response;
    protected final HttpSecureAppServlet caller;

    public MetadataService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request);

        this.caller = caller;
        this.request = wrapped;
        this.response = response;

        SessionHolder.requestInitialized(this.request);
    }

    protected void write(JSONObject data) throws IOException {
        response.getWriter().write(data.toString());
    }

    public abstract void process() throws IOException, ServletException;
}
