package com.etendoerp.metadata;

import org.apache.http.entity.ContentType;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.Utils.getLanguage;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpSecureAppServlet {
    protected abstract void process(HttpServletRequest request, HttpServletResponse response) throws Exception;

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setHeaders(request, response);
            setContext(request, response);
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
}
