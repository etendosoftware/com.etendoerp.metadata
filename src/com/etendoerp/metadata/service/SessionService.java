package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.data.RequestVariables;

/**
 * @author luuchorocha
 */
public class SessionService extends MetadataService {
    public SessionService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode();
            RequestVariables vars = SessionManager.initializeSession(getRequest());
            write(new SessionBuilder(vars).toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
