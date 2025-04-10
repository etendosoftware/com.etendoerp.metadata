package com.etendoerp.metadata.service;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.data.ServletMapping;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.client.kernel.RequestContext;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.openbravo.base.weld.WeldUtils.getInstanceFromStaticBeanManager;

/**
 * @author luuchorocha
 */
public class ServletService extends MetadataService {
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = buildServletRegistry();

    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
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

    @Deprecated
    private void initializeGlobalConfig() {
        if (KernelServlet.getGlobalParameters() == null) {
            getInstanceFromStaticBeanManager(KernelServlet.class).init(getCaller().getServletConfig());
        }
    }

    private ServletMapping findMatchingServlet() {
        String uri = getRequest().getPathInfo();

        if (uri == null || uri.isBlank()) {
            throw new NotFoundException("Missing path info in request");
        }

        ServletRegistration servlet = SERVLET_REGISTRY.get(uri);

        if (servlet != null) {
            return new ServletMapping(servlet, uri);
        }

        servlet = SERVLET_REGISTRY.get(getFirstSegment(uri));

        if (servlet != null) {
            return new ServletMapping(servlet, uri);
        }

        throw new NotFoundException("Invalid path: " + uri);
    }

    @Override
    public void process() throws ServletException {
        try {
            HttpServletRequestWrapper request = getRequest();
            HttpSecureAppServlet caller = getCaller();
            HttpServletResponse response = getResponse();
            SessionManager.initializeSession(request);
            ServletContext context = caller.getServletContext();
            RequestDispatcher dispatcher = context.getRequestDispatcher(findMatchingServlet().getMapping());
            dispatcher.forward(request, response);
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage());
        }
    }
}
