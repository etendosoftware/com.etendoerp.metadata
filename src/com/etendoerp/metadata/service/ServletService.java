package com.etendoerp.metadata.service;

import com.etendoerp.metadata.Constants;
import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.SessionManager;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.http.ServletRequestWrapper;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.etendoerp.metadata.Constants.DELEGATED_SERVLET_PATH;
import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

public class ServletService extends MetadataService {
    private static final Map<String, String> METHOD_MAP = new HashMap<>();
    private static final Map<String, HttpSecureAppServlet> SERVLET_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    static {
        METHOD_MAP.put(Constants.HTTP_METHOD_GET, Constants.SERVLET_DO_GET_METHOD);
        METHOD_MAP.put(Constants.HTTP_METHOD_POST, Constants.SERVLET_DO_POST_METHOD);
        METHOD_MAP.put(Constants.HTTP_METHOD_DELETE, Constants.SERVLET_DO_DELETE_METHOD);
    }

    private final HttpSecureAppServlet caller;

    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        this.caller = caller;
        initializeServletRegistry();
    }

    private static String getMethodName(String method) {
        return METHOD_MAP.getOrDefault(method, "service");
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) {
            try {
                List<?> servlets = WeldUtils.getInstances(getClassCached(servletName));
                return !servlets.isEmpty() ? (HttpSecureAppServlet) servlets.get(0) : createServletInstance(servletName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    private static HttpSecureAppServlet createServletInstance(String servletName) throws Exception {
        return getClassCached(servletName).asSubclass(HttpSecureAppServlet.class).getDeclaredConstructor()
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

    private void initializeServletRegistry() {
        caller.getServletContext().getServletRegistrations().values().forEach(sr -> {
            for (String mapping : sr.getMappings()) {
                SERVLET_REGISTRY.put(mapping.replace("/*", ""), sr);
            }
        });
    }

    private ServletRegistration findMatchingServlet(String uri) {
        return SERVLET_REGISTRY.get(uri);
    }

    @Override
    public void process() {
        try {
            String uri = getFirstSegment(request.getPathInfo().replaceAll(DELEGATED_SERVLET_PATH, ""));
            String servletName = findMatchingServlet(uri).getClassName();
            HttpSecureAppServlet servlet = getOrCreateServlet(servletName);
            ServletRequestWrapper wrappedRequest = new ServletRequestWrapper(request);

            forwardRequest(wrappedRequest, servlet);
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage(), e);
            throw new InternalServerException(e.getMessage());
        }
    }

    private void forwardRequest(ServletRequestWrapper wrappedRequest, HttpSecureAppServlet servlet) throws
                                                                                                    IllegalAccessException,
                                                                                                    InvocationTargetException,
                                                                                                    ServletException {
        wrappedRequest.removeAttribute(STATELESS_REQUEST_PARAMETER);
        if (servlet.getServletConfig() == null) {
            servlet.init();
        }

        SessionManager.initializeSession(wrappedRequest, true);
        findMethod(servlet, getMethodName(request.getMethod())).invoke(servlet, wrappedRequest, response);
    }
}
