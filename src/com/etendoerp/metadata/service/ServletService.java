package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.auth.SessionManager.initializeSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.client.kernel.RequestContext;

import com.etendoerp.metadata.exceptions.NotFoundException;


/**
 * @author luuchorocha
 */
public class ServletService extends MetadataService {
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = buildServletRegistry();

    public ServletService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

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

    public static String getFirstSegment(String path) {
        int firstSlash = path.indexOf("/");
        int secondSlash = path.indexOf("/", firstSlash + 1);

        if (firstSlash == -1) return path;
        if (secondSlash == -1) return path;

        return path.substring(0, secondSlash);
    }

    private String findMatchingMapping() {
        String uri = getRequest().getPathInfo();

        if (uri == null || uri.isBlank()) {
            throw new NotFoundException("Missing path info in request");
        }

        ServletRegistration servlet = SERVLET_REGISTRY.get(uri);

        if (servlet != null) {
            return uri;
        }

        servlet = SERVLET_REGISTRY.get(getFirstSegment(uri));

        if (servlet != null) {
            return uri;
        }

        throw new NotFoundException("Invalid path: " + uri);
    }

    @Override
    public void process() throws ServletException, IOException {
        HttpServletRequest request = getRequest();
        HttpServletResponse response = getResponse();
        initializeSession(request);
        request.getRequestDispatcher(findMatchingMapping()).forward(request, response);
    }
}
