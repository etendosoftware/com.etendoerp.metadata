package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.builders.MenuBuilder;

/**
 * @author luuchorocha
 */
public class MenuService extends MetadataService {
    public MenuService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        write(new MenuBuilder().toJSON());
    }
}
