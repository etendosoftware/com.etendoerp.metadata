/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.auth;

import static com.etendoerp.metadata.auth.Utils.decodeToken;
import static com.etendoerp.metadata.auth.Utils.generateToken;

import java.util.List;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.secureApp.DefaultValidationException;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.http.BaseServlet;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Manages the login process, including authentication, session initialization,
 * and JWT token generation.
 *
 * This class provides methods to authenticate users using credentials or JWT tokens,
 * retrieve authentication data, and generate login results.
 *
 * @author Etendo Software
 */
public class LoginManager {

  private static final String USER = "user";
  private static final String ROLE = "role";
  private static final String ORGANIZATION = "organization";
  private static final String WAREHOUSE = "warehouse";
  private static final String CLIENT = "client";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String TOKEN = "token";
  private final Logger logger = LogManager.getLogger(LoginManager.class);
  private final OBDal entityProvider = OBDal.getReadOnlyInstance();
  private final DalConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();

  /**
   * Extracts the JWT token from the "Authorization" header.
   *
   * This method checks if the provided authorization string is not null.
   * If it is not null, it extracts the token by removing the first 7 characters
   * (typically the "Bearer " prefix). If the authorization string is null,
   * it returns null.
   *
   * @param authorization The "Authorization" header value containing the token prefixed with "Bearer ".
   * @return              The extracted JWT token, or null if the authorization string is null.
   */
  public static String extractToken(String authorization) {
    return authorization != null ? authorization.substring(7) : null;
  }

  /**
   * Processes the login request and generates a JSON object containing the login result.
   *
   * This method authenticates the user based on the provided HTTP request, adds default values
   * to the authentication data if necessary, and generates a login result in the form of a JSON object.
   *
   * @param request The HTTP request containing the login information.
   * @return        A JSON object containing the login result, including a generated JWT token.
   * @throws Exception If an error occurs during authentication, adding defaults, or generating the login result.
   */
  public JSONObject processLogin(HttpServletRequest request) throws Exception {
    return generateLoginResult(request, addDefaults(authenticate(request)));
  }

  /**
   * Authenticates a user based on the provided HTTP request.
   *
   * This method retrieves authentication data from the HTTP request. It first checks if the request
   * contains user credentials (username and password). If credentials are present, it retrieves
   * the authentication data using the `getAuthData` method. If credentials are not present, it
   * attempts to extract a JWT token from the "Authorization" header of the request. If a token is
   * found, it retrieves the authentication data using the token. If neither credentials nor a token
   * are provided, an `UnauthorizedException` is thrown.
   *
   * @param request The HTTP request containing authentication information.
   * @return        An AuthData object containing the authenticated user's details.
   * @throws UnauthorizedException If neither credentials nor a valid token are provided.
   */
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

  /**
   * Retrieves authentication data using the provided JSON data.
   *
   * This method extracts user, role, organization, warehouse, and client entities
   * from the given JSON object. It uses helper methods to fetch each entity from
   * the database and constructs an AuthData object with the retrieved entities.
   *
   * @param data The JSON object containing the authentication data.
   * @return     An AuthData object containing the user, role, organization, warehouse, and client details.
   */
  private AuthData getAuthData(JSONObject data) {
    User user = authenticateUser(data);
    Role role = getEntity(data, ROLE, Role.class);
    if (role == null) {
      List<UserRoles> userRoleList = user.getADUserRolesList();
      if (userRoleList.size() > 0) {
        role = userRoleList.get(0).getRole();
      }
    }
    Organization org = getEntity(data, ORGANIZATION, Organization.class);
    Warehouse warehouse = getEntity(data, WAREHOUSE, Warehouse.class);
    Client client = getEntity(data, CLIENT, Client.class);
    return new AuthData(user, role, org, warehouse, client);
  }

  /**
   * Retrieves authentication data using the provided JSON data and JWT token.
   *
   * This method decodes the provided JWT token and retrieves the associated user, role,
   * organization, warehouse, and client entities. The retrieved entities are then used
   * to construct and return an AuthData object.
   *
   * @param data   The JSON object containing potential entity data.
   * @param token  The JWT token used to retrieve additional entity data.
   * @return       An AuthData object containing the user, role, organization, warehouse, and client details.
   */
  private AuthData getAuthData(JSONObject data, String token) {
    DecodedJWT decoded = getDecodedJWT(token);
    User user = getClaimedEntity(data, decoded, USER, User.class);
    Role role = getClaimedEntity(data, decoded, ROLE, Role.class);
    Organization org = getClaimedEntity(data, decoded, ORGANIZATION, Organization.class);
    Warehouse warehouse = getClaimedEntity(data, decoded, WAREHOUSE, Warehouse.class);
    Client client = getEntity(data, CLIENT, Client.class);
    return new AuthData(user, role, org, warehouse, client);
  }

  /**
   * Decodes a JWT token and returns the decoded token object.
   *
   * This method attempts to decode the provided JWT token using the `decodeToken` utility method.
   * If the decoding fails, it throws an `UnauthorizedException`. Any other exceptions encountered
   * during the process are wrapped and rethrown as a `RuntimeException`.
   *
   * @param token The JWT token to decode.
   * @return      The decoded JWT token object.
   * @throws UnauthorizedException If the token cannot be decoded or is invalid.
   * @throws RuntimeException       If any other exception occurs during decoding.
   */
  private DecodedJWT getDecodedJWT(String token) {
    try {
      return Optional.of(decodeToken(token)).orElseThrow(UnauthorizedException::new);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Checks if the provided JSON object contains user credentials.
   *
   * This method verifies the presence of both "username" and "password" keys
   * in the given JSON object. It returns true if both keys are present, indicating
   * that the credentials are available; otherwise, it returns false.
   *
   * @param data The JSON object containing potential user credentials.
   * @return     True if both "username" and "password" keys are present, false otherwise.
   */
  private boolean hasCredentials(JSONObject data) {
    return data.has(USERNAME) && data.has(PASSWORD);
  }

  /**
   * Authenticates a user using the provided JSON data.
   *
   * This method extracts the username and password from the given JSON object
   * and attempts to authenticate the user by verifying the credentials.
   * If the authentication fails, an UnauthorizedException is thrown.
   *
   * @param data The JSON object containing the user's credentials (username and password).
   * @return     The authenticated User object if the credentials are valid.
   * @throws UnauthorizedException If the username or password is invalid.
   */
  private User authenticateUser(JSONObject data) {
    String username = data.optString(USERNAME, null);
    String pass = data.optString(PASSWORD, null);
    return PasswordHash.getUserWithPassword(username, pass).orElseThrow(UnauthorizedException::new);
  }

  /**
   * Retrieves an entity of the specified type from the database using the provided JSON data.
   *
   * This method checks if the specified key exists in the JSON object. If the key exists,
   * it retrieves the value associated with the key and uses it to fetch the corresponding
   * entity of the specified class type from the database. If the key does not exist, it returns null.
   *
   * @param <T>    The type of the entity to retrieve.
   * @param data   The JSON object containing potential entity data.
   * @param key    The key to look for in the JSON object.
   * @param clazz  The class type of the entity to retrieve.
   * @return       The retrieved entity of the specified type, or null if the key does not exist
   *               or an error occurs during retrieval.
   */
  private <T> T getEntity(JSONObject data, String key, Class<T> clazz) {
    try {
      return data.has(key) ? entityProvider.get(clazz, data.getString(key)) : null;
    } catch (JSONException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }

  /**
   * Retrieves an entity of the specified type from the database using either the provided JSON data or a JWT token.
   *
   * This method attempts to fetch an entity of the given class type by first checking if the specified key exists
   * in the provided JSON object. If the key exists, it retrieves the value associated with the key from the JSON object.
   * Otherwise, it retrieves the value from the claims in the provided JWT token. The retrieved value is then used
   * to fetch the corresponding entity from the database.
   *
   * @param <T>    The type of the entity to retrieve.
   * @param data   The JSON object containing potential entity data.
   * @param token  The decoded JWT token containing claims for entity data.
   * @param key    The key to look for in the JSON object or the JWT token claims.
   * @param clazz  The class type of the entity to retrieve.
   * @return       The retrieved entity of the specified type, or null if an error occurs or the entity is not found.
   */
  private <T> T getClaimedEntity(JSONObject data, DecodedJWT token, String key, Class<T> clazz) {
    try {
      return entityProvider.get(clazz, data.has(key) ? data.getString(key) : token.getClaim(key).asString());
    } catch (JSONException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }

  /**
   * Generates a login result in the form of a JSON object.
   *
   * This method creates a new HTTP session, sets its timeout, and initializes the session context
   * with the provided authentication data. It also generates a JWT token for the session and includes
   * it in the resulting JSON object.
   *
   * @param request   The HTTP request containing session and user information.
   * @param authData  The authentication data containing user, role, organization, warehouse, and client details.
   * @return          A JSON object containing the generated JWT token.
   * @throws Exception If an error occurs during token creation or session initialization.
   */
  private JSONObject generateLoginResult(HttpServletRequest request, AuthData authData) throws Exception {
    try {
      JSONObject result = new JSONObject();
      HttpSession session = request.getSession(true);
      session.setMaxInactiveInterval(3600);
      OBContext.setOBContext(
          SecureWebServicesUtils.createContext(authData.getUser().getId(), authData.getRole().getId(), authData.getOrg().getId(),
              authData.getWarehouse() != null ? authData.getWarehouse().getId() : null, authData.getClient().getId()));
      OBContext.setOBContextInSession(request, OBContext.getOBContext());
      BaseServlet.initializeSession();
      result.put(TOKEN, generateToken(authData, session.getId()));
      return result;
    } catch (JWTCreationException e) {
      logger.warn("SWS - Error creating token", e);
      throw new InternalServerException(e.getMessage());
    }
  }

  /**
   * Adds default values to the provided authentication data if they are not already set.
   *
   * This method checks if the role, organization, warehouse, or client in the provided
   * authentication data is null. If any of these fields are null, it assigns default values
   * based on the user's default role or the login defaults retrieved from the system.
   *
   * @param authData The authentication data containing user, role, organization, warehouse, and client details.
   * @return         The updated authentication data with default values added where necessary.
   * @throws ServletException            If an error occurs while retrieving login defaults.
   * @throws DefaultValidationException  If validation of the default values fails.
   */
  private AuthData addDefaults(AuthData authData) throws ServletException, DefaultValidationException {
    if (authData.getRole() == null) {
      authData.setRole(authData.getUser().getDefaultRole());
    }
    String roleId = authData.getRole() != null ? authData.getRole().getId() : "";
    LoginUtils.RoleDefaults defaults = LoginUtils.getLoginDefaults(authData.getUser().getId(), roleId, conn);
    if (authData.getOrg() == null && defaults.org != null) {
      authData.setOrg(entityProvider.get(Organization.class, defaults.org));
    }
    if (authData.getWarehouse() == null && defaults.warehouse != null) {
      authData.setWarehouse(entityProvider.get(Warehouse.class, defaults.warehouse));
    }
    if (authData.getClient() == null && defaults.client != null) {
      authData.setClient(entityProvider.get(Client.class, defaults.client));
    }
    return authData;
  }
}

