package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;

/**
 * @author luuchorocha
 */
public abstract class MetadataService {
    private static final ThreadLocal<HttpServletRequest> requestThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpServletResponse> responseThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpSecureAppServlet> callerThreadLocal = new ThreadLocal<>();
    protected final Logger logger = LogManager.getLogger(this.getClass());

    public MetadataService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        requestThreadLocal.set(request);
        responseThreadLocal.set(response);
        callerThreadLocal.set(caller);

        OBContext.setAdminMode();
    }

    public static void clear() {
        requestThreadLocal.remove();
        responseThreadLocal.remove();
        callerThreadLocal.remove();

        OBContext context = OBContext.getOBContext();

        if (context != null && context.isInAdministratorMode()) {
            OBContext.restorePreviousMode();
        }
    }

    protected HttpServletRequest getRequest() {
        return requestThreadLocal.get();
    }

    protected HttpServletResponse getResponse() {
        return responseThreadLocal.get();
    }

    protected HttpSecureAppServlet getCaller() {
        return callerThreadLocal.get();
    }

    protected void write(JSONObject data) throws IOException {
        getResponse().getWriter().write(data.toString());
    }

    public abstract void process() throws ServletException, IOException;
}
