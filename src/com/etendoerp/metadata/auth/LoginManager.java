package com.etendoerp.metadata.auth;

import static com.smf.securewebservices.utils.SecureWebServicesUtils.generateToken;

import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.secureApp.DefaultValidationException;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.service.OBDal;
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
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * @author luuchorocha
 */
public class LoginManager {
  private final Logger logger = LogManager.getLogger(LoginManager.class);
  private final OBDal entityProvider = OBDal.getReadOnlyInstance();
  private final DalConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();

  public JSONObject processLogin(HttpServletRequest request) throws Exception {
    return generateLoginResult(addDefaults(authenticate(request)));
  }

  private String extractToken(String authorization) {
    return authorization != null ? authorization.substring(7) : null;
  }

  private AuthData authenticate(HttpServletRequest request) {
    JSONObject data = Utils.getRequestData(request);
    AuthData result;

    if (hasCredentials(data)) {
      return getAuthData(data);
    }

    String token = extractToken(request.getHeader("Authorization"));

    if (token != null) {
      result = getAuthData(data, token);
    } else {
      throw new UnauthorizedException(Constants.SWS_INVALID_CREDENTIALS);
    }

    return result;
  }

  private AuthData getAuthData(JSONObject data) {
    User user = authenticateUser(data);
    Role role = getEntity(data, "role", Role.class);
    Organization org = getEntity(data, "organization", Organization.class);
    Warehouse warehouse = getEntity(data, "warehouse", Warehouse.class);

    return new AuthData(user, role, org, warehouse);
  }

  private AuthData getAuthData(JSONObject data, String token) {
    DecodedJWT decoded = getDecodedJWT(token);
    User user = getClaimedEntity(data, decoded, "user", User.class);
    Role role = getClaimedEntity(data, decoded, "role", Role.class);
    Organization org = getClaimedEntity(data, decoded, "organization", Organization.class);
    Warehouse warehouse = getClaimedEntity(data, decoded, "warehouse", Warehouse.class);

    return new AuthData(user, role, org, warehouse);
  }

  private DecodedJWT getDecodedJWT(String token) {
    try {
      return Optional.of(SecureWebServicesUtils.decodeToken(token)).orElseThrow(UnauthorizedException::new);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean hasCredentials(JSONObject data) {
    return data.has("username") && data.has("password");
  }

  private User authenticateUser(JSONObject data) {
    String username = data.optString("username", null);
    String pass = data.optString("password", null);
    return PasswordHash.getUserWithPassword(username, pass).orElseThrow(UnauthorizedException::new);
  }

  private <T> T getEntity(JSONObject data, String key, Class<T> clazz) {
    try {
      return data.has(key) ? entityProvider.get(clazz, data.getString(key)) : null;
    } catch (JSONException e) {
      logger.error(e.getMessage(), e);

      return null;
    }
  }

  private <T> T getClaimedEntity(JSONObject data, DecodedJWT token, String key, Class<T> clazz) {
    try {
      return entityProvider.get(clazz, data.has(key) ? data.getString(key) : token.getClaim(key).asString());
    } catch (JSONException e) {
      logger.error(e.getMessage(), e);

      return null;
    }
  }

  private JSONObject generateLoginResult(AuthData authData) throws Exception {
    try {
      JSONObject result = new JSONObject();
      result.put("token", generateToken(authData.user, authData.role, authData.org, authData.warehouse));

      return result;
    } catch (JWTCreationException e) {
      logger.warn("SWS - Error creating token", e);

      throw new InternalServerException(e.getMessage());
    }
  }

  private AuthData addDefaults(AuthData authData) throws ServletException, DefaultValidationException {
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

    return authData;
  }
}
