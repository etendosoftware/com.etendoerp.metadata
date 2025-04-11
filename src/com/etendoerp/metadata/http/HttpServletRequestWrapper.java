package com.etendoerp.metadata.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.jboss.weld.module.web.servlet.SessionHolder;
import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private static final ThreadLocal<HttpSessionWrapper> session = new ThreadLocal<>();

    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        session.set(new HttpSessionWrapper());
        SessionHolder.sessionCreated(session.get());
    }

    public static void clear() {
        session.remove();
        SessionHolder.clear();
    }

    public static HttpSessionWrapper getCurrentSession() {
        return session.get();
    }

    @Override
    public HttpSession getSession() {
        return session.get();
    }

    @Override
    public HttpSession getSession(boolean f) {
        if (session.get() == null && f) {
            session.set(new HttpSessionWrapper());
        }

        return session.get();
    }

    @Override
    public String getServletPath() {
        String result = super.getServletPath();

        if (!result.startsWith("/meta")) {
            result = "/meta".concat(result);
        }

        return result;
    }
}
