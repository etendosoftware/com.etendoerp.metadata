package com.etendoerp.metadata;

import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.SessionInfo;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

import static com.etendoerp.metadata.Utils.buildErrorJson;
import static com.etendoerp.metadata.Utils.getWSInactiveInterval;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpBaseServlet {
    public static final String APPLICATION_JSON = "application/json";
    public final Logger logger = LogManager.getLogger(this.getClass());

    private static void setContext(HttpServletRequest request, DecodedJWT decodedToken) {
        String userId = decodedToken.getClaim("user").asString();
        String roleId = decodedToken.getClaim("role").asString();
        String orgId = decodedToken.getClaim("organization").asString();
        String warehouseId = decodedToken.getClaim("warehouse").asString();
        String clientId = decodedToken.getClaim("client").asString();

        validate(userId, roleId, orgId, warehouseId, clientId);

        OBContext.setOBContext(userId, roleId, clientId, orgId, null, warehouseId);
        OBContext.setOBContextInSession(request, OBContext.getOBContext());

        SessionInfo.setUserId(userId);
        SessionInfo.setProcessType("WS");
        SessionInfo.setProcessId("DAL");
    }

    private static void validate(String userId, String roleId, String orgId, String warehouseId, String clientId) {
        if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty()) {
            throw new UnauthorizedException();
        }
    }

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);

        if (request.getMethod().equals("OPTIONS")) {
            return;
        }

        if (!request.getMethod().equals("POST")) {
            throw new MethodNotAllowedException("Method not allowed");
        }

        String token = Utils.getToken(request);

        try {
            DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(token);

            if (decodedToken != null) {
                setContext(request, decodedToken);

                try {
                    this.process(request, response);
                } finally {
                    clearSession(request);
                }
            } else {
                throw new OBException("SWS - Token is not valid");
            }

        } catch (SignatureVerificationException | UnauthorizedException | OBSecurityException e) {
            logger.error(e.getMessage());
            response.setStatus(401);
            response.getWriter().write(buildErrorJson(e));
        } catch (MethodNotAllowedException e) {
            logger.error(e.getMessage());
            response.setStatus(405);
            response.getWriter().write(buildErrorJson(e));
        } catch (InternalServerException e) {
            logger.error(e.getMessage());
            response.setStatus(500);
            response.getWriter().write(buildErrorJson(e));
        } catch (Exception e) {
            logger.error(e.getMessage());
            response.setStatus(500);
        }
    }

    private void clearSession(HttpServletRequest request) {
        final boolean sessionExists = request.getSession(false) != null;
        final boolean sessionCreated = !sessionExists && null != request.getSession(false);
        if (sessionCreated && AuthenticationManager.isStatelessRequest(request)) {
            logger.warn("Stateless request, still a session was created ".concat(request.getRequestURL().toString()).concat(" ").concat(request.getQueryString()));
        }

        HttpSession session = request.getSession(false);

        if (session != null) {
            // HttpSession for WS should typically expire fast
            int maxExpireInterval = getWSInactiveInterval();
            if (maxExpireInterval == 0) {
                session.invalidate();
            } else {
                session.setMaxInactiveInterval(maxExpireInterval);
            }
        }
    }


    public abstract void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException;
}
