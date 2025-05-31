package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Constants.SERVLET_FULL_PATH;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
    public HttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
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
        return super.getSession();
    }

    @Override
    public HttpSession getSession(boolean f) {
        return super.getSession(f);
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