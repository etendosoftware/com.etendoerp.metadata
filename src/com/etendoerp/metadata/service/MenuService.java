package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.MenuBuilder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MenuService extends BaseService {
    public MenuService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        write(new MenuBuilder().toJSON());
    }
}
