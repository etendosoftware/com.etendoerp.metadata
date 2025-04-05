package com.etendoerp.metadata.service;

import com.etendoerp.metadata.Constants;
import com.etendoerp.metadata.SessionManager;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import org.jboss.weld.module.web.servlet.SessionHolder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;

import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.etendoerp.metadata.Constants.DELEGATED_SERVLET_PATH;
import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

public class ServletService extends BaseService {
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

        if (firstSlash == -1) return path; // Si no hay "/", devolver tal cual
        if (secondSlash == -1) return path; // Si solo hay un "/", devolver todo

        return path.substring(0, secondSlash); // Tomar hasta el segundo "/"
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
            request.removeAttribute(STATELESS_REQUEST_PARAMETER);
            servlet.init(caller.getServletConfig());
            SessionManager.initializeSession(request, true);
            SessionHolder.requestInitialized(request);
            findMethod(servlet, getMethodName(request.getMethod())).invoke(servlet, request, response);
        } catch (Exception e) {
            logger.error("Internal server error: {}", e.getMessage(), e);
            throw new InternalServerException(e.getMessage());
        }
    }
}
