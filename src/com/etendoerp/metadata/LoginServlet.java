package com.etendoerp.metadata;

import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import com.smf.securewebservices.service.SecureLoginServlet;
import org.openbravo.base.HttpBaseServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LoginServlet extends HttpBaseServlet {
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        SecureLoginServlet servlet = new SecureLoginServlet();
        servlet.init(this.getServletConfig());
        servlet.doPost(new HttpServletRequestWrapper(request), response);
    }
}
