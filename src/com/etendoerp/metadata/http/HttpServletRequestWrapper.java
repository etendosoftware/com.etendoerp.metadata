package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private final HttpSession session;

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.session = new HttpSessionWrapper();
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
