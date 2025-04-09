package com.etendoerp.metadata.service;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import com.etendoerp.metadata.utils.Constants;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.etendoerp.metadata.utils.Constants.DELEGATED_SERVLET_PATH;
import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

/**
 * @author luuchorocha
 */
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

    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
        initializeServletRegistry();
    }

    private static String getMethodName(String method) {
        return METHOD_MAP.getOrDefault(method, "service");
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) {
        return SERVLET_CACHE.computeIfAbsent(servletName, key -> {
            try {
                List<?> servlets = WeldUtils.getInstances(getClassCached(key));
                return !servlets.isEmpty() ? (HttpSecureAppServlet) servlets.get(0) : createServletInstance(key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
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
        request.getServletContext().getServletRegistrations().values().forEach(sr -> {
            for (String mapping : sr.getMappings()) {
                SERVLET_REGISTRY.put(mapping.replace("/*", ""), sr);
            }
        });
    }

    private ServletRegistration findMatchingServlet(String uri) {
        return SERVLET_REGISTRY.get(uri);
    }

    @Override
    public void process() throws ServletException {
        try {
            String uri = getFirstSegment(request.getPathInfo().replaceAll(DELEGATED_SERVLET_PATH, ""));
            String servletName;
            try {
                servletName = findMatchingServlet(uri).getClassName();
            } catch (NullPointerException e) {
                servletName = findMatchingServlet(request.getPathInfo()).getClassName();
            }

            HttpSecureAppServlet servlet = getOrCreateServlet(servletName);
            HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request);
            wrappedRequest.removeAttribute(STATELESS_REQUEST_PARAMETER);
            servlet.init(caller.getServletConfig());
            SessionManager.initializeSession(wrappedRequest);

            Method method = findMethod(servlet, getMethodName(wrappedRequest.getMethod()));
            if (method != null) {
                method.invoke(servlet, wrappedRequest, response);
            } else {
                Method doPostMethod = servlet.getClass().getMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
                doPostMethod.invoke(servlet, wrappedRequest, response);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NotFoundException("Invalid path: " + request.getPathInfo());
        } catch (NoSuchMethodException e) {
            throw new NotFoundException("Required servlet method not found: " + e.getMessage());
        }
    }
}
