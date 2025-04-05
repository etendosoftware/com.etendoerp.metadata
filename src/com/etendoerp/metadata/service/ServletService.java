package com.etendoerp.metadata.service;

import com.etendoerp.metadata.Constants;
import com.etendoerp.metadata.SessionManager;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;

import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.etendoerp.metadata.Constants.DELEGATED_SERVLET_PATH;
import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

public class ServletService extends BaseService {
    private static final Map<String, ServletRegistration> SERVLET_REGISTRY = new ConcurrentHashMap<>();
    private final HttpSecureAppServlet caller;

    public ServletService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        this.caller = caller;
        initializeServletRegistry();
    }

    private static String getMethodName(String method) {
        return Map.of(Constants.HTTP_METHOD_GET,
                      Constants.SERVLET_DO_GET_METHOD,
                      Constants.HTTP_METHOD_POST,
                      Constants.SERVLET_DO_POST_METHOD,
                      Constants.HTTP_METHOD_DELETE,
                      Constants.SERVLET_DO_DELETE_METHOD).getOrDefault(method, "service");
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) throws Exception {
        List<?> servlets = WeldUtils.getInstances(Class.forName(servletName));

        return (HttpSecureAppServlet) (servlets.isEmpty() ? getInstanceOf(servletName) : servlets.get(0));
    }

    private static HttpSecureAppServlet getInstanceOf(String servletName) throws Exception {
        return Class.forName(servletName).asSubclass(HttpSecureAppServlet.class).getDeclaredConstructor().newInstance();
    }

    public static Method findMethod(HttpSecureAppServlet servlet, String methodName) throws MethodNotAllowedException {
        return Arrays.stream(servlet.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(methodName))
                     .findFirst().orElseThrow(MethodNotAllowedException::new);
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
    public void process() throws Exception {
        String uri = getFirstSegment(request.getPathInfo().replaceAll(DELEGATED_SERVLET_PATH, ""));
        ServletRegistration servletRegistration = findMatchingServlet(uri);

        if (servletRegistration == null) {
            throw new NotFoundException("Invalid path: " + uri);
        }

        String servletName = servletRegistration.getClassName();
        HttpSecureAppServlet servlet = getOrCreateServlet(servletName);
        String servletMethodName = getMethodName(request.getMethod());
        Method delegatedMethod = findMethod(servlet, servletMethodName);
        HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request);
        wrappedRequest.removeAttribute(STATELESS_REQUEST_PARAMETER);
        servlet.init(caller.getServletConfig());
        SessionManager.initializeSession(wrappedRequest, true);
        delegatedMethod.invoke(servlet, wrappedRequest, response);
    }

}
