package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.builders.WindowBuilder;

/**
 * @author luuchorocha
 */
public class WindowService extends MetadataService {
    public WindowService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        String id = getRequest().getPathInfo().substring(8);
        write(new WindowBuilder(id).toJSON());
    }
}
