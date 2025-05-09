package com.etendoerp.metadata.utils;

import static com.etendoerp.metadata.utils.Constants.SERVLET_PATH;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;

import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.exceptions.NotFoundException;

public class ServletRegistry {
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = buildServletRegistry();

    private static Map<String, ServletRegistration> buildServletRegistry() {
        final Collection<? extends ServletRegistration> servletRegistrations = getServletRegistrations();
        final Map<String, ServletRegistration> result = new ConcurrentHashMap<>();

        for (ServletRegistration sr : servletRegistrations) {
            for (String mapping : sr.getMappings()) {
                result.put(mapping.replace("/*", ""), sr);
            }
        }

        return result;
    }

    private static Collection<? extends ServletRegistration> getServletRegistrations() {
        return RequestContext.getServletContext().getServletRegistrations().values();
    }

    private static String getFirstSegment(String path) {
        int firstSlash = path.indexOf("/");
        int secondSlash = path.indexOf("/", firstSlash + 1);

        if (firstSlash == -1) return path;
        if (secondSlash == -1) return path;

        return path.substring(0, secondSlash);
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) {
        try {
            Class<?> klazz = Class.forName(servletName);
            List<?> servlets = WeldUtils.getInstances(klazz);
            return !servlets.isEmpty() ? (HttpSecureAppServlet) servlets.get(0) : createServletInstance(klazz);
        } catch (Exception e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private static HttpSecureAppServlet createServletInstance(Class<?> klazz) {
        try {
            return klazz.asSubclass(HttpSecureAppServlet.class).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private static ServletRegistration findMatchingServlet(HttpServletRequest req) {
        String uri = req.getPathInfo();

        if (uri == null || uri.isBlank()) {
            throw new NotFoundException("Missing path info in request");
        }

        uri = uri.replace(SERVLET_PATH, "");
        ServletRegistration servlet = SERVLET_REGISTRY.get(uri);

        if (servlet != null) {
            return servlet;
        }

        servlet = SERVLET_REGISTRY.get(getFirstSegment(uri));

        if (servlet != null) {
            return servlet;
        }

        throw new NotFoundException("Invalid path: " + uri);
    }

    public static HttpSecureAppServlet getDelegatedServlet(HttpServletRequest req) {
        return getOrCreateServlet(findMatchingServlet(req).getClassName());
    }
}
