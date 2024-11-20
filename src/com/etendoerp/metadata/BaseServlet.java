package com.etendoerp.metadata;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.etendoerp.metadata.Utils.*;
import static com.smf.securewebservices.utils.SecureWebServicesUtils.decodeToken;

/**
 * @author luuchorocha
 */
public abstract class BaseServlet extends HttpBaseServlet {
    public final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            if (request.getMethod().equals("OPTIONS") || request.getMethod().equals("POST"))
                AllowedCrossDomainsHandler.getInstance().setCORSHeaders(request, response);

            if (request.getMethod().equals("OPTIONS")) return;
            if (!request.getMethod().equals("POST")) throw new MethodNotAllowedException();

            setContext(request, getDecodedToken(request));
            process(request, response);
        } catch (Exception e) {
            logger.error(e.toString(), e);
            response.getWriter().write(buildErrorJson(e));
            response.setStatus(getResponseStatus(e));
        }
    }

    protected DecodedJWT getDecodedToken(HttpServletRequest request) {
        try {
            String token = getToken(request);
            DecodedJWT decodedToken = decodeToken(token);

            if (decodedToken != null) {
                return decodedToken;
            } else {
                throw new UnauthorizedException();
            }
        } catch (Exception e) {
            logger.error(e.toString(), e);
            throw new UnauthorizedException();
        }
    }

    protected void setContext(HttpServletRequest request, DecodedJWT decodedToken) {
        String userId = decodedToken.getClaim("user").asString();
        String roleId = decodedToken.getClaim("role").asString();
        String orgId = decodedToken.getClaim("organization").asString();
        String warehouseId = decodedToken.getClaim("warehouse").asString();
        String clientId = decodedToken.getClaim("client").asString();
        String language = getLanguage(request);

        if (userId == null || userId.isEmpty() || roleId == null || roleId.isEmpty() || orgId == null || orgId.isEmpty() || warehouseId == null || warehouseId.isEmpty() || clientId == null || clientId.isEmpty())
            throw new UnauthorizedException();

        OBContext.setOBContext(userId, roleId, clientId, orgId, language, warehouseId);
    }

    protected int getResponseStatus(Exception e) {
        Object clazz = e.getClass();

        if (clazz.equals(OBSecurityException.class) || clazz.equals(UnauthorizedException.class)) return 401;
        else if (clazz.equals(MethodNotAllowedException.class)) return 405;
        else if (clazz.equals(UnprocessableContentException.class)) return 422;
        else return 500;
    }

    public abstract void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, JSONException;
}
