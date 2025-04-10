package com.etendoerp.metadata.service;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.KernelServlet;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;
import static org.openbravo.base.weld.WeldUtils.getInstanceFromStaticBeanManager;

/**
 * @author luuchorocha
 */
public class ServletService extends MetadataService {
    private static final Map<String, HttpSecureAppServlet> SERVLET_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
        initializeGlobalConfig();
        initializeServletRegistry();
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) {
        return SERVLET_CACHE.computeIfAbsent(servletName, key -> {
            try {
                List<?> servlets = WeldUtils.getInstances(getClassCached(key));
                return !servlets.isEmpty() ? (HttpSecureAppServlet) servlets.get(0) : createServletInstance(
                        key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static HttpSecureAppServlet createServletInstance(String servletName) throws Exception {
        return getClassCached(servletName).asSubclass(HttpSecureAppServlet.class)
                                          .getDeclaredConstructor()
                                          .newInstance();
    }

    private static Class<?> getClassCached(String className) {
        return CLASS_CACHE.computeIfAbsent(className, key -> {
            try {
                return Class.forName(key);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static Method findMethod(HttpSecureAppServlet servlet, String methodName) throws MethodNotAllowedException {
        return METHOD_CACHE.computeIfAbsent(servlet.getClass(), cls -> {
            Map<String, Method> methods = new HashMap<>();
            for (Method method : cls.getDeclaredMethods()) {
                methods.put(method.getName(), method);
            }
            return methods;
        }).getOrDefault(methodName, null);
    }

    public static String getFirstSegment(String path) {
        int firstSlash = path.indexOf("/");
        int secondSlash = path.indexOf("/", firstSlash + 1);

        if (firstSlash == -1) return path;
        if (secondSlash == -1) return path;

        return path.substring(0, secondSlash);
    }

    private void initializeGlobalConfig() {
        if (KernelServlet.getGlobalParameters() == null) {
            getInstanceFromStaticBeanManager(KernelServlet.class).init(getCaller().getServletConfig());
        }
    }

    private void initializeServletRegistry() {
        getRequest().getServletContext().getServletRegistrations().values().forEach(sr -> {
            for (String mapping : sr.getMappings()) {
                SERVLET_REGISTRY.put(mapping.replace("/*", ""), sr);
            }
        });
    }

    private String findMatchingServlet() {
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
    public void process() throws ServletException {
        try {
            HttpServletRequestWrapper request = getRequest();
            HttpSecureAppServlet caller = getCaller();
            HttpServletResponse response = getResponse();
            request.removeAttribute(STATELESS_REQUEST_PARAMETER);
            SessionManager.initializeSession(request);
            ServletContext context = caller.getServletContext();
            RequestDispatcher dispatcher = context.getRequestDispatcher(findMatchingServlet());
            dispatcher.forward(request, response);
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage());
        }
    }
}
