package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;

public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private String servletName;
    private String packageName;
    private final HttpSession session;

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        this.session = new RequestContext.HttpSessionWrapper();
        setUTF8Encoding(request);
    }

    public HttpServletRequestWrapper(HttpServletRequest request, String servletName, String packageName) {
        super(request);
        this.session = new RequestContext.HttpSessionWrapper();
        this.servletName = servletName;
        this.packageName = packageName;
        setUTF8Encoding(request);
    }

    private void setUTF8Encoding(HttpServletRequest request) {
        try {
            request.setCharacterEncoding("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
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
        return session;
    }
}
