package com.etendoerp.metadata.auth;

import static com.etendoerp.metadata.exceptions.Utils.getJsonObject;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.smf.securewebservices.SWSConfig;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * @author luuchorocha
 */
public class LoginManager {
    private static final Logger logger = LogManager.getLogger(LoginManager.class);
    private final ConnectionProvider conn;
    private final OBDal entityProvider;

    public LoginManager() {
        this.entityProvider = OBDal.getInstance();
        this.conn = new DalConnectionProvider();
    }

    public JSONObject processLogin(HttpServletRequest request) {
        try {
            validateSWSConfig();
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage());
        }

        AuthData authentication;

        try {
            authentication = authenticate(getRequestData(request), extractToken(request));
        } catch (Exception e) {
            throw new UnauthorizedException(e.getMessage());
        }

        try {
            return generateLoginResult(authentication, request);
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage());
        }
    }

    private JSONObject getRequestData(HttpServletRequest request) {
        try {
            return new JSONObject(request.getReader().lines().reduce("", String::concat));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authStr = request.getHeader("Authorization");
        return (authStr != null && authStr.startsWith("Bearer ")) ? authStr.substring(7) : null;
    }

    private void validateSWSConfig() {
        SWSConfig config = SWSConfig.getInstance();
        if (config.getPrivateKey() == null) {
            logger.warn("SWS - SWS are misconfigured");
            throw new InternalServerException(
                Utility.messageBD(conn, "SMFSWS_Misconfigured", OBContext.getOBContext().getLanguage().getLanguage()));
        }
    }

    private AuthData authenticate(JSONObject data,
        String token) throws JSONException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        User user;
        Role role;
        Organization org;
        Warehouse warehouse;

        if (data.has("username") && data.has("password")) {
            user = authenticateWithCredentials(data);
            role = getEntity(data, "role", Role.class);
            org = getEntity(data, "organization", Organization.class);
            warehouse = getEntity(data, "warehouse", Warehouse.class);
        } else if (token != null) {
            DecodedJWT decoded = SecureWebServicesUtils.decodeToken(token);
            if (decoded == null) {
                logger.warn("SWS - Token is not valid");
                throw new UnauthorizedException(Utility.messageBD(conn, "SMFSWS_InvalidToken",
                    OBContext.getOBContext().getLanguage().getLanguage()));
            }
            user = entityProvider.get(User.class, decoded.getClaim("user").asString());
            role = getClaimedEntity(data, decoded, "role", Role.class);
            org = getClaimedEntity(data, decoded, "organization", Organization.class);
            warehouse = getClaimedEntity(data, decoded, "warehouse", Warehouse.class);
        } else {
            logger.warn("SWS - You must specify a username and password or a valid token");
            throw new UnauthorizedException(Utility.messageBD(conn, "SMFSWS_PassOrTokenNeeded",
                OBContext.getOBContext().getLanguage().getLanguage()));
        }

        return new AuthData(user, role, org, warehouse);
    }

    private User authenticateWithCredentials(JSONObject data) {
        try {
            String username = data.getString("username");
            String pass = data.getString("password");
            Optional<User> opUser = PasswordHash.getUserWithPassword(username, pass);

            if (opUser.isPresent()) {
                return opUser.get();
            } else {
                throw new UnauthorizedException(Utility.messageBD(conn, "IDENTIFICATION_FAILURE_TITLE",
                    OBContext.getOBContext().getLanguage().getLanguage()));
            }
        } catch (JSONException e) {
            throw new UnprocessableContentException(e.getMessage());
        }
    }

    private <T> T getEntity(JSONObject data, String key, Class<T> clazz) throws JSONException {
        if (data.has(key)) {
            return entityProvider.get(clazz, data.getString(key));
        }
        return null;
    }

    private <T> T getClaimedEntity(JSONObject data, DecodedJWT token, String key, Class<T> clazz) throws JSONException {
        String id = data.has(key) ? data.getString(key) : token.getClaim(key).asString();
        return entityProvider.get(clazz, id);
    }

    private JSONObject generateLoginResult(AuthData authData, HttpServletRequest ignoredRequest) throws Exception {
        JSONObject result = new JSONObject();
        try {
            if (authData.role == null) {
                authData.role = authData.user.getDefaultRole();
            }

            String roleId = authData.role != null ? authData.role.getId() : "";
            LoginUtils.RoleDefaults defaults = LoginUtils.getLoginDefaults(authData.user.getId(), roleId, conn);

            if (authData.org == null && defaults.org != null) {
                authData.org = entityProvider.get(Organization.class, defaults.org);
            }
            if (authData.warehouse == null && defaults.warehouse != null) {
                authData.warehouse = entityProvider.get(Warehouse.class, defaults.warehouse);
            }

            String token = SecureWebServicesUtils.generateToken(authData.user, authData.role, authData.org,
                authData.warehouse);

            result.put("status", "success");
            result.put("token", token);

            JSONArray rolesAndOrgs = SecureWebServicesUtils.getUserRolesAndOrg(authData.user, true, true);
            result.put("roleList", rolesAndOrgs);
            result.put("user", getJsonObject(authData.user));
            result.put("currentRole", getJsonObject(authData.role));
            result.put("currentOrganization", getJsonObject(authData.org));
            result.put("currentClient", getJsonObject(authData.warehouse));
        } catch (JWTCreationException e) {
            logger.warn("SWS - Error creating token", e);
            throw new Exception(Utility.messageBD(conn, "SMFSWS_ErrorCreatingToken",
                OBContext.getOBContext().getLanguage().getLanguage()));
        }
        return result;
    }

    public JSONObject buildErrorResponse(Exception e) {
        JSONObject result = new JSONObject();
        try {
            result.put("status", "error");
            result.put("message",
                e.getMessage() != null ? e.getMessage() : Utility.messageBD(conn, "SMFSWS_GenericErrorLog",
                    OBContext.getOBContext().getLanguage().getLanguage()));
        } catch (JSONException jsonException) {
            logger.error("Error building error response", jsonException);
        }
        logger.error("Login failed", e);
        return result;
    }
}
