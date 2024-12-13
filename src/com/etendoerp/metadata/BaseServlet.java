package com.etendoerp.metadata;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.db.DalConnectionProvider;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.etendoerp.metadata.Utils.*;
import static com.smf.securewebservices.utils.SecureWebServicesUtils.decodeToken;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpBaseServlet {
    public static final String INVALID_OR_MISSING_TOKEN = "Invalid or missing token";
    public static final String ONLY_POST_METHOD_IS_ALLOWED = "Only POST method is allowed";
    public static final String MISSING_REQUIRED_CLAIMS = "Missing required claims: userId={}, roleId={}, orgId={}, warehouseId={}, clientId={}";
    public static final String ERROR_PROCESSING_REQUEST = "Error processing request";
    public static final String FAILED_TO_DECODE_TOKEN = "Failed to decode token";
    private static final Logger logger = LogManager.getLogger(BaseServlet.class);
    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_METHOD_OPTIONS = "OPTIONS";

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            setCorsHeaders(request, response);
            setContentHeaders(response);

            if (HTTP_METHOD_OPTIONS.equals(request.getMethod())) {
                return;
            }

            checkHttpMethod(request);
            setContext(request);
            setSession(request);
            process(request, response);
        } catch (Exception e) {
            logger.error(ERROR_PROCESSING_REQUEST, e);
            response.getWriter().write(buildErrorJson(e));
            response.setStatus(getResponseStatus(e));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void checkHttpMethod(HttpServletRequest request) {
        if (!HTTP_METHOD_POST.equals(request.getMethod())) {
            throw new MethodNotAllowedException(ONLY_POST_METHOD_IS_ALLOWED);
        }
    }

    private void setCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (HTTP_METHOD_OPTIONS.equals(request.getMethod()) || HTTP_METHOD_POST.equals(request.getMethod())) {
            AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);
        }
    }

    private void setContentHeaders(HttpServletResponse response) {
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    private void setSession(HttpServletRequest request) throws ServletException {
        OBContext context = OBContext.getOBContext();
        LoginUtils.fillSessionArguments(new DalConnectionProvider(), new VariablesSecureApp(request), context.getUser().getId(), context.getLanguage().getLanguage(), context.isRTL() ? "Y" : "N", context.getRole().getId(), context.getCurrentClient().getId(), context.getCurrentOrganization().getId(), context.getWarehouse().getId());
    }

    protected DecodedJWT getDecodedToken(HttpServletRequest request) {
        try {
            String token = getToken(request);
            DecodedJWT decodedToken = decodeToken(token);

            if (decodedToken == null) {
                throw new UnauthorizedException(INVALID_OR_MISSING_TOKEN);
            }
            return decodedToken;
        } catch (Exception e) {
            logger.error(FAILED_TO_DECODE_TOKEN, e);
            throw new UnauthorizedException(INVALID_OR_MISSING_TOKEN);
        }
    }

    protected void setContext(HttpServletRequest request) {
        DecodedJWT decodedToken = getDecodedToken(request);

        String userId = decodedToken.getClaim("user").asString();
        String roleId = decodedToken.getClaim("role").asString();
        String orgId = decodedToken.getClaim("organization").asString();
        String warehouseId = decodedToken.getClaim("warehouse").asString();
        String clientId = decodedToken.getClaim("client").asString();

        if (isNullOrEmpty(userId) || isNullOrEmpty(roleId) || isNullOrEmpty(orgId) || isNullOrEmpty(warehouseId) || isNullOrEmpty(clientId)) {
            logger.error(MISSING_REQUIRED_CLAIMS, userId, roleId, orgId, warehouseId, clientId);
            throw new UnauthorizedException(INVALID_OR_MISSING_TOKEN);
        }

        OBContext.setOBContext(userId, roleId, clientId, orgId, null, warehouseId);
        OBContext.setAdminMode();
    }

    protected int getResponseStatus(Exception e) {
        String exceptionName = e.getClass().getSimpleName();

        switch (exceptionName) {
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

    public abstract void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
}
