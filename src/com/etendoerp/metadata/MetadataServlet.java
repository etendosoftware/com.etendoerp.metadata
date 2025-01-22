package com.etendoerp.metadata;

import com.etendoerp.metadata.builders.*;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.KernelServlet;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static com.etendoerp.metadata.Utils.sendSuccessResponse;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
    public static final String TOOLBAR_PATH = "/toolbar";
    public static final String SESSION_PATH = "/session";
    public static final String MENU_PATH = "/menu";
    public static final String WINDOW_PATH = "/window/";
    public static final String LANGUAGE_PATH = "/language";
    public static final String DELEGATED_SERVLET_PATH = "/servlets";
    private static final Logger logger = LogManager.getLogger(MetadataServlet.class);
    private static final String KERNEL_CLIENT_PATH = "/org.openbravo.client.kernel";

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getPathInfo();

        if (path.startsWith(WINDOW_PATH)) {
            handleWindowRequest(request, response);
        } else if (path.equals(MENU_PATH)) {
            handleMenuRequest(request, response);
        } else if (path.equals(SESSION_PATH)) {
            handleSessionRequest(request, response);
        } else if (path.startsWith(TOOLBAR_PATH)) {
            handleToolbarRequest(request, response);
        } else if (path.startsWith(KERNEL_CLIENT_PATH)) {
            handleKernelRequest(request, response);
        } else if (path.startsWith(DELEGATED_SERVLET_PATH)) {
            handleDelegatedServletRequest(request, response);
        } else if (path.startsWith(LANGUAGE_PATH)) {
            handleLanguageRequest(request, response);
        } else {
            throw new NotFoundException();
        }
    }

    private void handleWindowRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchWindow(request.getPathInfo().substring(8)).toString());
    }

    private void handleMenuRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchMenu().toString());
    }

    private void handleSessionRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write(this.fetchSession().toString());
    }

    private void handleKernelRequest(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        KernelServlet servlet = WeldUtils.getInstanceFromStaticBeanManager(KernelServlet.class);
        initializeServlet(servlet, request);
        servlet.doGet(request, response);
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
            HttpSecureAppServlet servlet;
            String[] path = request.getPathInfo().split("/servlets/");
            String servletName = path[path.length - 1];
            List<?> servlets = WeldUtils.getInstances(Class.forName(servletName));

            if (servlets.isEmpty()) {
                servlet = (HttpSecureAppServlet) Class.forName(servletName).getDeclaredConstructor().newInstance();
            } else {
                servlet = (HttpSecureAppServlet) servlets.get(0);
            }

            initializeServlet(servlet, request);
            servlet.service(request, response);
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);

            throw new NotFoundException();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException();
        }
    }

    private void initializeServlet(HttpSecureAppServlet servlet, HttpServletRequest request) throws ServletException {
        servlet.init(this.getServletConfig());
        OBContext context = OBContext.getOBContext();
        String userId = context.getUser().getId();
        String clientId = context.getCurrentClient().getId();
        String orgId = context.getCurrentOrganization().getId();
        String roleId = context.getRole().getId();
        String warehouseId = context.getWarehouse().getId();
        String languageCode = context.getLanguage().getLanguage();
        VariablesSecureApp vars = new VariablesSecureApp(userId, clientId, orgId, roleId, languageCode);
        RequestContext requestContext = RequestContext.get();
        requestContext.setRequest(request);
        requestContext.setVariableSecureApp(vars);
        boolean success = LoginUtils.fillSessionArguments(myPool,
                                                          vars,
                                                          userId,
                                                          languageCode,
                                                          OBContext.isRightToLeft() ? "Y" : "N",
                                                          roleId,
                                                          clientId,
                                                          orgId,
                                                          warehouseId);
        if (!success) {
            log4j.error("LoginUtils.fillSessionArguments error");
        }
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
}