package com.etendoerp.metadata.http;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class BaseServlet extends HttpSecureAppServlet {
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(new HttpServletRequestWrapper(request), response);
    }
}