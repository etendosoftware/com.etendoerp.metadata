package com.etendoerp.metadata.http;

import org.openbravo.client.kernel.RequestContext.HttpServletRequestWrapper;
import org.openbravo.client.kernel.RequestContext.HttpSessionWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe wrapper for servlet requests, used to customize the request URI and lazy-initialize the session.
 */
public final class ServletRequestWrapper extends HttpServletRequestWrapper {

    private final String servletName;
    private final String packageName;

    // Use volatile for safe publication across threads
    private final HttpSession session;

    public ServletRequestWrapper(HttpServletRequest request) {
        this(request, null, null);
    }

    public ServletRequestWrapper(HttpServletRequest request, String servletName, String packageName) {
        super(request);
        this.servletName = servletName;
        this.packageName = packageName;
        this.session = new HttpSessionWrapper();
    }

    @Override
    public String getRequestURI() {
        if (servletName != null && packageName != null) {
            return super.getRequestURI()
                        .replaceFirst(Pattern.quote(servletName), Matcher.quoteReplacement(packageName));
        }
        return super.getRequestURI();
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public HttpSession getSession(boolean create) {
        return session;
    }
}
