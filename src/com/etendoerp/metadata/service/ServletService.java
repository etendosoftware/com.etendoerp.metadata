package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.auth.SessionManager.initializeSession;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.utils.ServletRegistry;


/**
 * @author luuchorocha
 * @see  {@link com.etendoerp.metadata.http.ForwarderServlet}
 */
public class ServletService extends MetadataService {
    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws ServletException, IOException {
        HttpServletRequest request = getRequest();
        HttpServletResponse response = getResponse();
        HttpSecureAppServlet servlet = ServletRegistry.getDelegatedServlet(request);

        if (servlet.getServletConfig() == null) {
            servlet.init(getCaller().getServletConfig());
        }

        initializeSession();
        servlet.service(request, response);
    }
}
