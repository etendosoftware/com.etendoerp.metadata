package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Utils.initializeGlobalConfig;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.data.RequestVariables;

public class BaseServlet extends HttpSecureAppServlet {
    private static AuthenticationManager authenticationManager = null;

    @Override
    public void init(ServletConfig config) {
        super.init(config);
        initializeGlobalConfig(config);
        authenticationManager = AuthenticationManager.getAuthenticationManager(this);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        HttpServletRequestWrapper request = HttpServletRequestWrapper.wrap(req);
        RequestVariables vars = new RequestVariables(request);
        RequestContext requestContext = RequestContext.get();
        requestContext.setRequest(request);
        requestContext.setVariableSecureApp(vars);
        requestContext.setResponse(res);
        authenticate();
    }

    protected String authenticate() throws ServletException, IOException {
        return authenticationManager.authenticate(RequestContext.get().getRequest(),
            RequestContext.get().getResponse());
    }

    protected void setSessionProperties() {
        VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
        readNumberFormat(vars, KernelServlet.getGlobalParameters().getFormatPath());
        readProperties(vars);
    }
}
