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
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.SessionInfo;
import org.openbravo.service.json.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpBaseServlet {
    public static final Logger logger = LogManager.getLogger();
    public static final String APPLICATION_JSON = "application/json";
    private static final int DEFAULT_WS_INACTIVE_INTERVAL = 60;
    private static Integer wsInactiveInterval = null;

    private static void setContext(HttpServletRequest request, DecodedJWT decodedToken) {
        String userId = decodedToken.getClaim("user").asString();
        String roleId = decodedToken.getClaim("role").asString();
        String orgId = decodedToken.getClaim("organization").asString();
        String warehouseId = decodedToken.getClaim("warehouse").asString();
        String clientId = decodedToken.getClaim("client").asString();

        if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty()) {
            throw new UnauthorizedException();
        }

        OBContext.setOBContext(SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId));
        OBContext.setOBContextInSession(request, OBContext.getOBContext());

        SessionInfo.setUserId(userId);
        SessionInfo.setProcessType("WS");
        SessionInfo.setProcessId("DAL");
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

        String token = getToken(request);

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

    private String getToken(HttpServletRequest request) {
        String authStr = request.getHeader("Authorization");
        String token = null;

        if (authStr != null && authStr.startsWith("Bearer ")) {
            token = authStr.substring(7);
        }

        return token;
    }

    private int getWSInactiveInterval() {
        if (wsInactiveInterval == null) {
            try {
                wsInactiveInterval = Integer.parseInt(OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty("ws.maxInactiveInterval", Integer.toString(DEFAULT_WS_INACTIVE_INTERVAL)));
            } catch (Exception e) {
                wsInactiveInterval = DEFAULT_WS_INACTIVE_INTERVAL;
            }
            logger.info("Sessions for WS calls expire after ".concat(wsInactiveInterval.toString()).concat(" seconds. This can be configured with ws.maxInactiveInterval property."));
        }

        return wsInactiveInterval;
    }

    public JSONObject getBody(HttpServletRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            return new JSONObject(sb.toString());
        } catch (JSONException | IOException e) {
            logger.warn(e.getMessage());

            return new JSONObject();
        }
    }

    private String buildErrorJson(Exception e) {
        try {
            return new JSONObject(JsonUtils.convertExceptionToJson(e)).toString();
        } catch (Exception err) {
            logger.warn(err.getMessage());

            return "";
        }
    }

    public abstract void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException;
}
