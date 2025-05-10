package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.http.BaseServlet.initializeSession;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.SessionBuilder;

/**
 * @author luuchorocha
 */
public class SessionService extends MetadataService {
    public SessionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            write(new SessionBuilder(initializeSession()).toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
