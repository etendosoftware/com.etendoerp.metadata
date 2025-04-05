package com.etendoerp.metadata;

import com.etendoerp.metadata.service.ServiceFactory;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
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
public class MetadataServlet extends HttpSecureAppServlet {
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
        String exceptionName = e.getClass().getSimpleName();

        switch (exceptionName) {
            case "OBSecurityException":
            case "UnauthorizedException":
                return HttpStatus.SC_UNAUTHORIZED;
            case "MethodNotAllowedException":
                return HttpStatus.SC_METHOD_NOT_ALLOWED;
            case "UnprocessableContentException":
                return HttpStatus.SC_UNPROCESSABLE_ENTITY;
            default:
                return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
    }

    private void process(HttpServletRequest request, HttpServletResponse response) {
        ServiceFactory factory = new ServiceFactory(this);
        factory.getService(request.getPathInfo(), request, response).process();
    }
}

