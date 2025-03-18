package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private final String servletName;
    private final String packageName;
    private final HttpSession session;

    public HttpServletRequestWrapper(HttpServletRequest request, String servletName, String packageName) {
        super(request);
        this.session = new RequestContext.HttpSessionWrapper();
        this.servletName = servletName;
        this.packageName = packageName;
    }

    @Override
    public String getRequestURI() {
        return getDelegate().getRequestURI().replaceFirst(servletName, packageName);
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public HttpSession getSession(boolean f) {
        return session;
    }
}
