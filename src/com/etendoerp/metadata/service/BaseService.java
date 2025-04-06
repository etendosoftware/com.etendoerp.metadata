package com.etendoerp.metadata.service;

import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.jboss.weld.module.web.servlet.SessionHolder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class BaseService extends MetadataService {
    protected HttpSecureAppServlet caller;

    public BaseService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        this.caller = caller;
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request);
        SessionHolder.requestInitialized(wrapped);
        setRequest(wrapped);
    }

    public abstract void process() throws IOException, ServletException;
}
