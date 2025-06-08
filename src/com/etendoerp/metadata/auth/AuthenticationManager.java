package com.etendoerp.metadata.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.authentication.AuthenticationException;
import org.openbravo.authentication.basic.DefaultAuthenticationManager;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.SessionInfo;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * The default servlet which catches all requests for a webservice. This servlet finds the WebService
 * instance implementing the requested service by calling the {@link OBProvider} with the top segment
 * in the path. When the WebService implementation is found the request is forwarded to that service.
 */
public class AuthenticationManager extends DefaultAuthenticationManager {
    private static final Logger log4j = LogManager.getLogger(AuthenticationManager.class);

    /**
     * Default constructor.
     */
    public AuthenticationManager() {
        super();
    }

    private void validateToken(String userId, String roleId, String orgId, String warehouseId, String clientId) {
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(roleId) || StringUtils.isEmpty(
            orgId) || StringUtils.isEmpty(warehouseId) || StringUtils.isEmpty(clientId)) {
            throw new OBException("SWS - Token is not valid");
        }
    }

    private void setSessionInfo(String jti, String userId) {
        SessionInfo.setSessionId(jti);
        SessionInfo.setUserId(userId);
        SessionInfo.setProcessType("WS");
        SessionInfo.setProcessId("DAL");
    }

    private void setContext(HttpServletRequest request, String userId, String roleId, String orgId,
        String warehouseId, String clientId) {
        OBContext.setOBContext(SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId));
        OBContext.setOBContextInSession(request, OBContext.getOBContext());
    }

    @Override
    protected String doWebServiceAuthenticate(HttpServletRequest request) {
        String authStr = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.startsWith(authStr, "Bearer ")) {
            token = StringUtils.substring(authStr, 7);
        }
        if (token != null) {
            try {
                log4j.debug(" Decoding token {}", token);
                DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(token);
                if (decodedToken != null) {
                    String userId = decodedToken.getClaim("user").asString();
                    String roleId = decodedToken.getClaim("role").asString();
                    String orgId = decodedToken.getClaim("organization").asString();
                    String warehouseId = decodedToken.getClaim("warehouse").asString();
                    String clientId = decodedToken.getClaim("client").asString();
                    String jti = decodedToken.getClaim("jti").asString();

                    validateToken(userId, roleId, orgId, warehouseId, clientId);
                    validateSession(request, jti);

                    log4j.debug("SWS accessed by userId {}", userId);

                    setContext(request, userId, roleId, orgId, warehouseId, clientId);
                    setSessionInfo(jti, userId);

                    try {
                        OBContext.setAdminMode();

                        return userId;
                    } finally {
                        OBContext.restorePreviousMode();
                    }
                }
            } catch (AuthenticationException e) {
                throw e;
            } catch (Exception e) {
                throw new OBException(e);
            }
        }
        return super.doWebServiceAuthenticate(request);
    }

    private void validateSession(HttpServletRequest request, String jti) {
        HttpSession session = request.getSession(false);
        String sessionId = session != null ? session.getId() : null;

        if (!StringUtils.equalsIgnoreCase(sessionId, jti)) {
            if (session != null) {
                session.invalidate();
            }

            throw new AuthenticationException("SWS - Session no longer valid");
        }
    }
}