package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import com.etendoerp.metadata.http.HttpServletRequestWrapper;

/**
 * @author luuchorocha
 */
public abstract class MetadataService {
    private static final ThreadLocal<HttpServletRequestWrapper> requestThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpServletResponse> responseThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpSecureAppServlet> callerThreadLocal = new ThreadLocal<>();
    protected final Logger logger = LogManager.getLogger(this.getClass());

    public MetadataService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        requestThreadLocal.set(new HttpServletRequestWrapper(request));
        responseThreadLocal.set(response);
        callerThreadLocal.set(caller);
    }

    public static void clear() {
        HttpServletRequestWrapper.clear();
        requestThreadLocal.remove();
        responseThreadLocal.remove();
        callerThreadLocal.remove();
    }

    protected HttpServletRequestWrapper getRequest() {
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

    public abstract void process() throws IOException;
}
