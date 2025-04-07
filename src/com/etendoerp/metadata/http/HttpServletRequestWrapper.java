package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private static final ThreadLocal<HttpSession> sessionHolder = ThreadLocal.withInitial(HttpSessionWrapper::new);

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public static void clearSession() {
        sessionHolder.remove();
    }

    @Override
    public HttpSession getSession() {
        return sessionHolder.get();
    }

    @Override
    public HttpSession getSession(boolean f) {
        return sessionHolder.get();
    }
}
