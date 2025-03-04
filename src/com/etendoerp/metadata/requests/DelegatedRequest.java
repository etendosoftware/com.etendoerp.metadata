package com.etendoerp.metadata.requests;

import com.etendoerp.metadata.Constants;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.apache.commons.lang.StringUtils;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.etendoerp.metadata.SessionManager.initializeSession;

public class DelegatedRequest extends Request {
    private final ServletConfig config;

    public DelegatedRequest(HttpServletRequest request, HttpServletResponse response, ServletConfig config) {
        super(request, response);
        this.config = config;
    }

    private static String getMethodName(String method) {
        return Map.of(Constants.HTTP_METHOD_GET,
                      Constants.SERVLET_DO_GET_METHOD,
                      Constants.HTTP_METHOD_POST,
                      Constants.SERVLET_DO_POST_METHOD).getOrDefault(method, "service");
    }

    private static HttpSecureAppServlet getOrCreateServlet(String servletName) throws Exception {
        List<?> servlets = WeldUtils.getInstances(Class.forName(servletName));

        return (HttpSecureAppServlet) (servlets.isEmpty() ? getInstanceOf(servletName) : servlets.get(0));
    }

    private static HttpSecureAppServlet getInstanceOf(String servletName) throws Exception {
        return Class.forName(servletName).asSubclass(HttpSecureAppServlet.class).getDeclaredConstructor().newInstance();
    }

    private static HttpServletRequest wrapRequestWithRemainingPath(HttpServletRequest request, String pathInfo,
                                                                   String servletName) {
        String className = pathInfo.split("/servlets/")[1];
        String packageName = StringUtils.substring(className, 0, className.lastIndexOf('.'));

        return new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return request.getRequestURI().replaceFirst(servletName, packageName);
            }
        };
    }

    public static Method findMethod(HttpSecureAppServlet servlet, String methodName) throws MethodNotAllowedException {
        return Arrays.stream(servlet.getClass().getDeclaredMethods())
                     .filter(m -> m.getName().equals(methodName))
                     .findFirst()
                     .orElseThrow(MethodNotAllowedException::new);
    }

    public void process() {
        try {
            String method = request.getMethod();
            String pathInfo = request.getPathInfo();
            String[] path = pathInfo.split("/servlets/");

            if (pathInfo.isBlank() || path.length < 2 || path[1].isEmpty()) {
                throw new NotFoundException("Invalid servlet name: " + pathInfo);
            }

            String servletName = path[1].split("/")[0];
            HttpSecureAppServlet servlet = getOrCreateServlet(servletName);
            String servletMethodName = getMethodName(method);
            Method delegatedMethod = findMethod(servlet, servletMethodName);
            HttpServletRequest wrappedRequest = wrapRequestWithRemainingPath(request, pathInfo, servletName);

            initializeSession(config, servlet, wrappedRequest);
            delegatedMethod.invoke(servlet, wrappedRequest, response);
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);

            throw new NotFoundException();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

}
