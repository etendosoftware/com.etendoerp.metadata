package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private String servletName;
    private String packageName;
    private final HttpSession session;

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.session = new RequestContext.HttpSessionWrapper();
    }

    public HttpServletRequestWrapper(HttpServletRequest request, String servletName, String packageName) {
        this(request);
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
