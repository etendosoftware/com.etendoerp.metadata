package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext.HttpServletRequestWrapper;
import org.openbravo.client.kernel.RequestContext.HttpSessionWrapper;

import javax.servlet.http.HttpServletRequest;

public class ServletRequestWrapper extends HttpServletRequestWrapper {
    private String servletName;
    private String packageName;
    private volatile HttpSessionWrapper session;

    public ServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public ServletRequestWrapper(HttpServletRequest request, String servletName, String packageName) {
        super(request);
        this.servletName = servletName;
        this.packageName = packageName;
    }

    @Override
    public String getRequestURI() {
        if (servletName != null && packageName != null) {
            return super.getRequestURI().replaceFirst(servletName, packageName);
        } else {
            return super.getRequestURI();
        }
    }

    @Override
    public HttpSessionWrapper getSession() {
        return session;
    }

    @Override
    public HttpSessionWrapper getSession(boolean f) {
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
