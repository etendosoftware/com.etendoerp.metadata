package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext.HttpSessionWrapper;
import org.openbravo.client.kernel.RequestContext.HttpServletRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class ServletRequestWrapper extends HttpServletRequestWrapper {
    private String servletName;
    private String packageName;
    private HttpSession session;

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
            return getDelegate().getRequestURI().replaceFirst(servletName, packageName);
        } else {
            return super.getRequestURI();
        }
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public HttpSession getSession(boolean f) {
        if (f) {
            synchronized (this) {
                if (session == null) {
                    this.session = new HttpSessionWrapper();
                }
            }
        }
        return session;
    }
}
