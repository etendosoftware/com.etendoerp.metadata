package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.service.*;
import org.apache.http.entity.ContentType;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.Utils.getLanguage;
import static org.openbravo.authentication.AuthenticationManager.STATELESS_REQUEST_PARAMETER;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpSecureAppServlet {
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.setAttribute(STATELESS_REQUEST_PARAMETER, "true");
        super.service(request, response);
    }

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setContext(request);
            setContentHeaders(response);
            process(request, response);
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            response.setStatus(getResponseStatus(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    @Override
    public final void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public final void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    private void setContext(HttpServletRequest request) {
        OBContext context = OBContext.getOBContext();
        Language language = getLanguage(request);

        if (language != null) {
            context.setLanguage(language);
        }

        OBContext.setOBContextInSession(request, context);
    }

    private void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    protected int getResponseStatus(Exception e) {
        switch (e.getClass().getSimpleName()) {
            case "OBSecurityException":
            case "UnauthorizedException":
                return 401;
            case "MethodNotAllowedException":
                return 405;
            case "UnprocessableContentException":
                return 422;
            default:
                return 500;
        }
    }

    private void process(HttpServletRequest request, HttpServletResponse response) {
        BaseService service;
        String path = request.getPathInfo();

        if (path.startsWith(Constants.WINDOW_PATH)) {
            service = new WindowService(request, response);
        } else if (path.startsWith(Constants.TAB_PATH)) {
            service = new TabService(request, response);
        } else if (path.startsWith(Constants.TOOLBAR_PATH)) {
            service = new ToolbarService(request, response);
        } else if (path.startsWith(Constants.DELEGATED_SERVLET_PATH)) {
            service = new ServletService(this, request, response);
        } else if (path.startsWith(Constants.LANGUAGE_PATH)) {
            service = new LanguageService(request, response);
        } else if (Constants.MENU_PATH.equals(path)) {
            service = new MenuService(request, response);
        } else if (Constants.SESSION_PATH.equals(path)) {
            service = new SessionService(request, response);
        } else {
            throw new NotFoundException("Invalid URL: " + path);
        }

        service.process();
    }
}

