package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext.HttpServletRequestWrapper;
import org.openbravo.client.kernel.RequestContext.HttpSessionWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class ServletRequestWrapper extends HttpServletRequestWrapper {
    private volatile HttpSession session;

    public ServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.session = new HttpSessionWrapper();
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public HttpSession getSession(boolean f) {
        if (f && session == null) {
            synchronized (this) {
                if (session == null) {
                    session = new HttpSessionWrapper();
                }
            }
        }
        return session;
    }
}
