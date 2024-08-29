package com.etendo.metadata;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.SessionInfo;
import org.openbravo.model.ad.ui.Window;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Writer;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends HttpBaseServlet {
    private static final String APPLICATION_JSON = "application/json";
    private static final Logger log = LogManager.getLogger();
    private static final int DEFAULT_WS_INACTIVE_INTERVAL = 60;
    private static Integer wsInactiveInterval = null;

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final boolean sessionExists = request.getSession(false) != null;

        AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);

        if (request.getMethod().equals("OPTIONS")) {
            return;
        }

        if (request.getMethod().equals("GET")) {
            response.setStatus(405);

            return;
        }

        String authStr = request.getHeader("Authorization");
        String token = null;

        if (authStr != null && authStr.startsWith("Bearer ")) {
            token = authStr.substring(7);
        }

        try {
            DecodedJWT decodedToken = SecureWebServicesUtils.decodeToken(token);

            if (decodedToken != null) {
                String userId = decodedToken.getClaim("user").asString();
                String roleId = decodedToken.getClaim("role").asString();
                String orgId = decodedToken.getClaim("organization").asString();
                String warehouseId = decodedToken.getClaim("warehouse").asString();
                String clientId = decodedToken.getClaim("client").asString();

                if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty()) {
                    throw new OBException("SWS - Token is not valid");
                }

                OBContext.setOBContext(SecureWebServicesUtils.createContext(userId, roleId, orgId, warehouseId, clientId));
                OBContext.setOBContextInSession(request, OBContext.getOBContext());
                SessionInfo.setUserId(userId);
                SessionInfo.setProcessType("WS");
                SessionInfo.setProcessId("DAL");
                try {
                    this.doPost(request, response);
                } finally {
                    final boolean sessionCreated = !sessionExists && null != request.getSession(false);
                    if (sessionCreated && AuthenticationManager.isStatelessRequest(request)) {
                        log.warn("Stateless request, still a session was created ".concat(request.getRequestURL().toString()).concat(" ").concat(request.getQueryString()));
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
            } else {
                throw new OBException("SWS - Token is not valid");
            }
        } catch (Exception e) {
            JSONObject result = new JSONObject();
            log.warn(e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Writer writer = response.getWriter();

            try {
                result.put("ok", false);
                result.put("error", e.getMessage());
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }

            writer.write(result.toString());
        }
    }

    private int getWSInactiveInterval() {
        if (wsInactiveInterval == null) {
            try {
                wsInactiveInterval = Integer.parseInt(OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty("ws.maxInactiveInterval", Integer.toString(DEFAULT_WS_INACTIVE_INTERVAL)));
            } catch (Exception e) {
                wsInactiveInterval = DEFAULT_WS_INACTIVE_INTERVAL;
            }
            log.info("Sessions for WS calls expire after ".concat(wsInactiveInterval.toString()).concat(" seconds. This can be configured with ws.maxInactiveInterval property."));
        }

        return wsInactiveInterval;
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getPathInfo() != null ? request.getPathInfo() : "";

        if (path.startsWith("/window/")) {
            this.fetchWindow(request, response);
        } else if (path.startsWith("/translations")) {
            this.fetchTranslations(request, response);
        } else if (path.equals("/menu")) {
            this.fetchMenu(request, response);
        } else {
            this.notFound(request, response);
        }
    }

    private void fetchTranslations(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(200);
    }

    private void fetchWindow(HttpServletRequest request, HttpServletResponse response) {
        try {
            OBContext.setAdminMode();
            String path = request.getPathInfo() != null ? request.getPathInfo() : "";
            String id = path.substring(8);
            Writer writer = response.getWriter();
            response.setContentType(APPLICATION_JSON);
            response.setCharacterEncoding("utf-8");
            Window window = OBDal.getInstance().get(Window.class, id);
            writer.write(new WindowBuilder(window).toJSON().toString());
            writer.close();
        } catch (Exception e) {
            log.warn(e.getMessage());
            response.setStatus(500);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void fetchMenu(HttpServletRequest request, HttpServletResponse response) {
        try {
            OBContext.setAdminMode();
            Writer writer = response.getWriter();
            response.setContentType(APPLICATION_JSON);
            writer.write(new MenuBuilder().toJSON().toString());
            writer.close();
        } catch (Exception e) {
            log.warn(e.getMessage());
            response.setStatus(500);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void notFound(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(404);
    }
}
