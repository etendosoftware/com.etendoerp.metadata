package com.etendoerp.metadata;

import org.apache.http.entity.ContentType;
import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.Constants.ERROR_PROCESSING_REQUEST;
import static com.etendoerp.metadata.Utils.getLanguageCode;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpBaseServlet {
    protected abstract void process(HttpServletRequest request, HttpServletResponse response) throws Exception;

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            OBContext.setAdminMode();
            setHeaders(request, response);
            authenticate(request);
            process(request, response);
        } catch (Exception e) {
            log4j.error(ERROR_PROCESSING_REQUEST, e);
            response.setStatus(getResponseStatus(e));
            response.getWriter().write(JsonUtils.convertExceptionToJson(e));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    protected void authenticate(HttpServletRequest request) throws ServletException {
        AuthenticationManager.getAuthenticationManager(this).webServiceAuthenticate(request);
        String languageCode = getLanguageCode(request);
        OBContext context = OBContext.getOBContext();
        OBContext.setOBContextInSession(request, context);
        String userId = context.getUser().getId();
        String clientId = context.getCurrentClient().getId();
        String orgId = context.getCurrentOrganization().getId();
        String roleId = context.getRole().getId();
        String warehouseId = context.getWarehouse().getId();
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
