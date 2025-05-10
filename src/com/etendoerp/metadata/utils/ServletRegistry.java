package com.etendoerp.metadata.utils;

import static com.etendoerp.metadata.utils.Constants.SERVLET_PATH;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletRegistration;

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
        int secondSlash = path.indexOf("/", 1);

        return secondSlash == -1 ? path : path.substring(0, secondSlash);
    }

    private static HttpSecureAppServlet getOrCreateServlet(String uri) {
        try {
            String servletName = findMatchingServlet(uri).getClassName();
            Class<?> klazz = Class.forName(servletName);
            List<?> servlets = WeldUtils.getInstances(klazz);

            return servlets.isEmpty() ? createServletInstance(klazz) : (HttpSecureAppServlet) servlets.get(0);
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

    private static ServletRegistration findMatchingServlet(String uri) {
        ServletRegistration servlet = SERVLET_REGISTRY.get(uri);

        if (servlet == null) {
            servlet = Optional.of(SERVLET_REGISTRY.get(getFirstSegment(uri))).orElseThrow(NotFoundException::new);
        }

        return servlet;
    }

    private static String getMappingPath(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new NotFoundException("Missing path info in request");
        }

        return uri.replaceFirst(SERVLET_PATH, "");
    }

    public static HttpSecureAppServlet getDelegatedServlet(HttpSecureAppServlet caller, String uri) {
        HttpSecureAppServlet servlet = getOrCreateServlet(getMappingPath(uri));

        if (servlet.getServletConfig() == null) {
            servlet.init(caller.getServletConfig());
        }

        return servlet;
    }
}
