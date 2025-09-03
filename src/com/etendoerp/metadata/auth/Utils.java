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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.AuthData;
import com.smf.securewebservices.SWSConfig;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Utility class for handling authentication-related operations such as token generation,
 * decoding, and retrieving user-related entities.
 *
 * This class provides methods to interact with JWT tokens, retrieve roles, organizations,
 * and warehouses, and manage private keys for secure web services.
 */
public class Utils {
  private static final long ONE_MINUTE_IN_MILLIS = 60000;
  private static final String HS256_ALGORITHM = "HS256";

  /**
   * Private constructor to prevent instantiation of the utility class.
   *
   * @throws InstantiationException Always thrown to prevent instantiation.
   */
  private Utils() throws InstantiationException {
    throw new InstantiationException();
  }

  /**
   * Retrieves the appropriate role for the user based on the provided parameters.
   *
   * @param role           The role to evaluate.
   * @param userRoleList   The list of user roles.
   * @param defaultWsRole  The default web service role.
   * @param defaultRole    The default role.
   * @return The selected role.
   */
  private static Role getRole(Role role, List<UserRoles> userRoleList, Role defaultWsRole, Role defaultRole) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("getRole", Role.class, List.class, Role.class,
          Role.class);
      method.setAccessible(true);

      return (Role) method.invoke(null, role, userRoleList, defaultWsRole, defaultRole);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Retrieves the appropriate organization for the user based on the provided parameters.
   *
   * @param org           The organization to evaluate.
   * @param selectedRole  The selected role.
   * @param defaultRole   The default role.
   * @param defaultOrg    The default organization.
   * @return The selected organization.
   */
  private static Organization getOrganization(Organization org, Role selectedRole, Role defaultRole,
      Organization defaultOrg) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("getOrganization", Organization.class, Role.class,
          Role.class, Organization.class);
      method.setAccessible(true);

      return (Organization) method.invoke(null, org, selectedRole, defaultRole, defaultOrg);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Retrieves the appropriate warehouse for the user based on the provided parameters.
   *
   * @param warehouse         The warehouse to evaluate.
   * @param selectedOrg       The selected organization.
   * @param defaultWarehouse  The default warehouse.
   * @return The selected warehouse.
   */
  private static Warehouse getWarehouse(Warehouse warehouse, Organization selectedOrg, Warehouse defaultWarehouse) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("getWarehouse", Warehouse.class,
          Organization.class, Warehouse.class);
      method.setAccessible(true);

      return (Warehouse) method.invoke(null, warehouse, selectedOrg, defaultWarehouse);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Cleans the private key from the provided configuration.
   *
   * @param config The secure web services configuration.
   * @return The cleaned private key.
   */
  private static String cleanPrivateKey(SWSConfig config) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("cleanPrivateKey", SWSConfig.class);
      method.setAccessible(true);

      return (String) method.invoke(null, config);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Retrieves the encoder algorithm based on the private key content and algorithm used.
   *
   * @param privateKeyContent The private key content.
   * @param algorithmUsed     The algorithm to use.
   * @return The encoder algorithm.
   */
  private static Algorithm getEncoderAlgorithm(String privateKeyContent, String algorithmUsed) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("getEncoderAlgorithm", String.class, String.class);
      method.setAccessible(true);

      return (Algorithm) method.invoke(null, privateKeyContent, algorithmUsed);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Decodes and verifies a JWT token using the appropriate algorithm.
   * This method extracts the token header, determines the signing algorithm (either ES256 or HS256),
   * and verifies the token using the configured public key for ES256 or the private key for HS256.
   *
   * @param token The JWT token to be decoded and verified.
   * @return The decoded {@link DecodedJWT} object containing the claims from the token.
   */
  public static DecodedJWT decodeToken(String token) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("decodeToken", String.class);
      method.setAccessible(true);

      return (DecodedJWT) method.invoke(null, token);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

  /**
   * Generates a JWT token for the authenticated user.
   *
   * @param authData  The authentication data of the user.
   * @param sessionId The session ID.
   * @return The generated JWT token.
   * @throws Exception If an error occurs during token generation.
   */
  public static String generateToken(AuthData authData, String sessionId) throws Exception {
    try {
      OBContext.setAdminMode(true);

      Algorithm algorithm;
      SWSConfig config = SWSConfig.getInstance();
      String privateKey = config.getPrivateKey();

      User user = authData.getUser();
      Role role = authData.getRole();
      Organization org = authData.getOrg();
      Warehouse warehouse = authData.getWarehouse();

      List<UserRoles> userRoleList = user.getADUserRolesList();
      Role defaultWsRole = user.getSmfswsDefaultWsRole();
      Role defaultRole = user.getDefaultRole();
      Organization defaultOrg = user.getDefaultOrganization();
      Warehouse defaultWarehouse = user.getDefaultWarehouse();
      // If there is no default warehouse, use the provided warehouse
      Warehouse warehouseFallback = defaultWarehouse != null ? defaultWarehouse : warehouse;

      Role selectedRole = getRole(role, userRoleList, defaultWsRole, defaultRole);
      Organization selectedOrg = getOrganization(org, selectedRole, defaultRole, defaultOrg);
      Warehouse selectedWarehouse = getWarehouse(warehouse, selectedOrg, warehouseFallback);

      if (SecureWebServicesUtils.isNewVersionPrivKey(privateKey)) {
        String algorithmUsed = getAlgorithmUsed();
        privateKey = cleanPrivateKey(config);
        algorithm = getEncoderAlgorithm(privateKey, algorithmUsed);
      } else {
        algorithm = getEncoderAlgorithm(privateKey, HS256_ALGORITHM);
      }

      if (sessionId == null) {
        sessionId = UUID.randomUUID().toString();
      }

      JWTCreator.Builder jwtBuilder = getJwtBuilder(user, selectedRole, selectedOrg, selectedWarehouse, sessionId);

      if (config.getExpirationTime() > 0) {
        jwtBuilder.withExpiresAt(getExpirationDate(config));
      }

      return jwtBuilder.sign(algorithm);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Retrieves the algorithm used for encryption from preferences.
   *
   * @return The encryption algorithm.
   * @throws PropertyException If an error occurs while retrieving the preference.
   */
  private static String getAlgorithmUsed() throws PropertyException {
    return Preferences.getPreferenceValue("SMFSWS_EncryptionAlgorithm", true,
        OBContext.getOBContext().getCurrentClient(), OBContext.getOBContext().getCurrentOrganization(),
        OBContext.getOBContext().getUser(), OBContext.getOBContext().getRole(), null);
  }

  /**
   * Calculates the expiration date for the JWT token based on the configuration.
   *
   * @param config The secure web services configuration.
   * @return The expiration date.
   */
  private static Date getExpirationDate(SWSConfig config) {
    Calendar date = Calendar.getInstance();
    long t = date.getTimeInMillis();

    return new Date(t + (config.getExpirationTime() * ONE_MINUTE_IN_MILLIS));
  }

  /**
   * Builds a JWT token with the provided user, role, organization, warehouse, and session ID.
   *
   * @param user             The authenticated user.
   * @param selectedRole     The selected role.
   * @param selectedOrg      The selected organization.
   * @param selectedWarehouse The selected warehouse.
   * @param sessionId        The session ID.
   * @return The JWT builder.
   */
  private static JWTCreator.Builder getJwtBuilder(User user, Role selectedRole, Organization selectedOrg,
      Warehouse selectedWarehouse, String sessionId) {
    return JWT.create().withIssuer("sws").withAudience("sws").withClaim("user", user.getId()).withClaim("client",
        selectedRole.getClient().getId()).withClaim("role", selectedRole.getId()).withClaim("organization",
        selectedOrg.getId()).withClaim("warehouse", selectedWarehouse.getId()).withClaim("jti", sessionId).withIssuedAt(
        new Date());
  }
}
