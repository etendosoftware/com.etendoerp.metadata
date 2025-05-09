package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.MenuBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;

/**
 * @author luuchorocha
 */
public class MenuService extends MetadataService {
    public MenuService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            write(new MenuBuilder().toJSON());
        } catch (JSONException e) {
            throw new InternalServerException(e.getMessage());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}

