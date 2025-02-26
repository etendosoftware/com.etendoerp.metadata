package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.*;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.etendoerp.metadata.Utils.getLanguage;
import static com.etendoerp.metadata.Utils.sendSuccessResponse;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    public static final String TOOLBAR_PATH = "/toolbar";
    public static final String SESSION_PATH = "/session";
    public static final String MENU_PATH = "/menu";
    public static final String WINDOW_PATH = "/window/";
    public static final String LANGUAGE_PATH = "/language";
    public static final String DELEGATED_SERVLET_PATH = "/servlets";
    private static final Logger logger = LogManager.getLogger(MetadataServlet.class);
    private final static String csrf = "123";
    protected HttpSession session;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setHeaders(request, response);
            setContext(request, response);
            process(request, response);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            response.setStatus(Utils.getResponseStatus(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
        } finally {
            OBContext.restorePreviousMode();

            if (session != null) {
                session.invalidate();
            }
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doPost(request, response);
    }

    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        throw new MethodNotAllowedException();
    }

    private void setContext(HttpServletRequest request, HttpServletResponse response) {
        OBContext context = OBContext.getOBContext();
        Language language = getLanguage(request);

        if (language != null) {
            context.setLanguage(getLanguage(request));
        }

        OBContext.setOBContextInSession(request, context);
    }

    private void setHeaders(HttpServletRequest request, HttpServletResponse response) {
        setCorsHeaders(request, response);
        setContentHeaders(response);
    }

    private void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
    }

    private void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    private void handleWindowRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchWindow(request.getPathInfo().substring(8)).toString());
    }

    private void handleMenuRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchMenu().toString());
    }

    private void handleSessionRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionUtils.initializeSession(request, response);
        response.getWriter().write(this.fetchSession().toString());
    }

    private void handleToolbarRequest(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        String[] pathParts = path.split("/");
        if (pathParts.length < 3) {
            throw new UnprocessableContentException("Invalid toolbar path");
        }
        try {
            String windowId = pathParts[2];
            String tabId = (pathParts.length >= 4 && !"undefined".equals(pathParts[3])) ? pathParts[3] : null;
            JSONObject toolbar = fetchToolbar(windowId, tabId);
            sendSuccessResponse(response, toolbar);
        } catch (IllegalArgumentException e) {
            throw new UnprocessableContentException(e.getMessage());
        } catch (Exception e) {
            throw new InternalServerException();
        }
    }

    private void handleDelegatedServletRequest(HttpServletRequest request, HttpServletResponse response) {
        try {
            String method = request.getMethod();
            String pathInfo = request.getPathInfo();
            String[] path = pathInfo.split("/servlets/");

            if (pathInfo.isBlank() || path.length < 2 || path[1].isEmpty()) {
                throw new NotFoundException("Invalid servlet name: " + pathInfo);
            }

            String servletName = path[1].split("/")[0];
            HttpSecureAppServlet servlet = getOrCreateServlet(servletName);
            String servletMethodName = getMethodName(servlet, method);
            Method delegatedMethod = findMethod(servlet, servletMethodName);
            HttpServletRequest wrappedRequest = wrapRequestWithRemainingPath(request, pathInfo, servletName);
            SessionUtils.initializeSession(wrappedRequest, response);
            delegatedMethod.invoke(servlet, wrappedRequest, response);
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);

            throw new NotFoundException();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private String getMethodName(HttpSecureAppServlet servlet, String method) {
        return Map.of(Constants.HTTP_METHOD_GET,
                      Constants.SERVLET_DO_GET_METHOD,
                      Constants.HTTP_METHOD_POST,
                      Constants.SERVLET_DO_POST_METHOD).getOrDefault(method, "service");
    }

    private HttpSecureAppServlet getOrCreateServlet(String servletName) throws Exception {
        List<?> servlets = WeldUtils.getInstances(Class.forName(servletName));

        return (HttpSecureAppServlet) (servlets.isEmpty() ? getInstanceOf(servletName) : servlets.get(0));
    }

    private HttpSecureAppServlet getInstanceOf(String servletName) throws Exception {
        return Class.forName(servletName).asSubclass(HttpSecureAppServlet.class).getDeclaredConstructor().newInstance();
    }

    private HttpServletRequest wrapRequestWithRemainingPath(HttpServletRequest request, String pathInfo,
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

    private Method findMethod(HttpSecureAppServlet servlet, String methodName) throws MethodNotAllowedException {
        return Arrays.stream(servlet.getClass().getDeclaredMethods())
                     .filter(m -> m.getName().equals(methodName))
                     .findFirst()
                     .orElseThrow(MethodNotAllowedException::new);
    }

    private void handleLanguageRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchLanguages().toString());
    }

    private JSONObject fetchToolbar(String windowId, String tabId) {
        try {
            String language = OBContext.getOBContext().getLanguage().getLanguage();
            boolean isNew = false;
            ToolbarBuilder toolbarBuilder = new ToolbarBuilder(language, windowId, tabId, isNew);
            return toolbarBuilder.toJSON();
        } catch (Exception e) {
            logger.error("Error creating toolbar for window: {}", windowId, e);
            throw new RuntimeException("Error creating toolbar", e);
        }
    }

    private JSONObject fetchWindow(String id) {
        return new WindowBuilder(id).toJSON();
    }

    private JSONArray fetchMenu() {
        return new MenuBuilder().toJSON();
    }

    private JSONObject fetchSession() {
        return new SessionBuilder().toJSON();
    }

    private JSONArray fetchLanguages() {
        return new LanguageBuilder().toJSON();
    }

    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();

        if (path.startsWith(WINDOW_PATH)) {
            handleWindowRequest(request, response);
        } else if (path.equals(MENU_PATH)) {
            handleMenuRequest(request, response);
        } else if (path.equals(SESSION_PATH)) {
            handleSessionRequest(request, response);
        } else if (path.startsWith(TOOLBAR_PATH)) {
            handleToolbarRequest(request, response);
        } else if (path.startsWith(DELEGATED_SERVLET_PATH)) {
            handleDelegatedServletRequest(request, response);
        } else if (path.startsWith(LANGUAGE_PATH)) {
            handleLanguageRequest(request, response);
        } else if (path.isBlank()) {
            response.getWriter().close();
        } else {
            throw new NotFoundException();
        }
    }
}
