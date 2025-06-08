package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.SERVLET_FULL_PATH;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    private static final ThreadLocal<HttpSession> session = ThreadLocal.withInitial(HttpSessionWrapper::new);

    private HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public static void clear() {
        session.remove();
    }

    public static HttpServletRequestWrapper wrap(HttpServletRequest request) {
        if (request.getClass().equals(HttpServletRequestWrapper.class)) {
            return (HttpServletRequestWrapper) request;
        } else {
            return new HttpServletRequestWrapper(request);
        }
    }

    @Override
    public HttpSession getSession() {
        return session.get();
    }

    @Override
    public HttpSession getSession(boolean f) {
        return session.get();
    }

    @Override
    public String getServletPath() {
        String result = super.getServletPath();

        if (!result.startsWith(SERVLET_FULL_PATH)) {
            result = SERVLET_FULL_PATH.concat(result);
        }

        return result;
    }
}