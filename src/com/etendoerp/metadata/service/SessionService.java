package com.etendoerp.metadata.service;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.data.RequestVariables;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class SessionService extends MetadataService {
    public SessionService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
       super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        RequestVariables vars = SessionManager.initializeSession(getRequest());
        write(new SessionBuilder(vars).toJSON());
    }
}
