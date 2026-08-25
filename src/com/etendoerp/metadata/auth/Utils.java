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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
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

import javax.servlet.http.HttpServletRequest;

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
   * Message key {@code SecureWebServicesUtils} raises (wrapped in an {@code OBException}) when
   * the selected organization has no warehouses at all - see its {@code pickFallbackWarehouse}.
   */
  private static final String ORG_HAS_NO_WAREHOUSES_MESSAGE_KEY = "SMFSWS_OrgHasNoRole";

  /**
   * Picks a fallback warehouse to offer {@code SecureWebServicesUtils#getWarehouse} as its
   * {@code defaultWarehouse} parameter, but only if it actually belongs to the resolved
   * organization.
   * <p>
   * That method returns whatever non-null {@code defaultWarehouse} it's given as-is, with no
   * validation of its own - it's meant purely as a last-resort value once the requested warehouse
   * didn't match the organization. Passing {@code user.getDefaultWarehouse()} unconditionally (as
   * this method used to) meant that once a user saved warehouse X as their default while on one
   * role, switching to any OTHER role/organization that doesn't have X would silently carry it
   * over anyway - e.g. a role whose organization has no warehouses at all would still end up with
   * X as its warehouse instead of correctly having none. Discarding it here when it doesn't
   * belong lets {@code getWarehouse} fall through to picking a real warehouse for the
   * organization, or to none if it has none.
   *
   * @param defaultWarehouse the user's stored default warehouse, or {@code null}
   * @param warehouse        the explicitly requested warehouse, or {@code null}
   * @param selectedOrg      the resolved organization the fallback must belong to
   * @return {@code defaultWarehouse} or {@code warehouse}, whichever is non-null and belongs to
   *         {@code selectedOrg}, checked in that order; {@code null} if neither does
   */
  private static Warehouse resolveWarehouseFallback(Warehouse defaultWarehouse, Warehouse warehouse,
      Organization selectedOrg) {
    if (defaultWarehouse != null && belongsToOrganization(defaultWarehouse, selectedOrg)) {
      return defaultWarehouse;
    }
    if (warehouse != null && belongsToOrganization(warehouse, selectedOrg)) {
      return warehouse;
    }
    return null;
  }

  /**
   * Checks whether a warehouse is among the organization's own warehouses.
   *
   * @param warehouse    the warehouse to check
   * @param organization the organization to check against
   * @return {@code true} if the warehouse belongs to the organization
   */
  private static boolean belongsToOrganization(Warehouse warehouse, Organization organization) {
    return SecureWebServicesUtils.getOrganizationWarehouses(organization).stream()
        .anyMatch(orgWarehouse -> orgWarehouse.getId().equals(warehouse.getId()));
  }

  /**
   * Retrieves the appropriate warehouse for the user based on the provided parameters.
   * <p>
   * Classic tolerates an organization with no warehouses at all - the session's warehouse is
   * simply left empty (see {@code LoginUtils#fillSessionArguments}, which accepts an empty
   * warehouse id). {@code SecureWebServicesUtils#getWarehouse} does not: it throws when the
   * organization has none. Since that class cannot be modified here, that specific failure is
   * caught and treated as "no warehouse" instead, matching Classic's behavior.
   *
   * @param warehouse         The warehouse to evaluate.
   * @param selectedOrg       The selected organization.
   * @param defaultWarehouse  The default warehouse.
   * @param selectedRole      The selected role.
   * @return The selected warehouse, or {@code null} if the organization has no warehouses.
   */
  private static Warehouse getWarehouse(Warehouse warehouse, Organization selectedOrg,
      Warehouse defaultWarehouse, Role selectedRole) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("getWarehouse", Warehouse.class,
          Organization.class, Warehouse.class, Role.class);
      method.setAccessible(true);

      return (Warehouse) method.invoke(null, warehouse, selectedOrg, defaultWarehouse, selectedRole);
    } catch (InvocationTargetException e) {
      if (isOrgHasNoWarehousesError(e.getCause())) {
        return null;
      }
      throw new OBException(e);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new OBException(e);
    }
  }

  /**
   * Checks whether a throwable is the "organization has no warehouses" error that
   * {@code SecureWebServicesUtils#pickFallbackWarehouse} raises.
   *
   * @param cause the throwable to check, typically an {@code InvocationTargetException}'s cause
   * @return {@code true} if it is that specific error
   */
  private static boolean isOrgHasNoWarehousesError(Throwable cause) {
    return cause != null && cause.getMessage() != null
        && cause.getMessage().contains(ORG_HAS_NO_WAREHOUSES_MESSAGE_KEY);
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
   * Extracts and decodes the {@code Authorization: Bearer <token>} header off a request.
   * Returns {@code null} — never throws — if the header is missing, isn't a Bearer header, the
   * token is blank, or {@link #decodeToken} rejects it as malformed.
   *
   * @param request the HTTP request
   * @return the decoded token, or {@code null}
   */
  public static DecodedJWT decodeBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7).trim();
    if (token.isEmpty()) {
      return null;
    }
    try {
      return decodeToken(token);
    } catch (OBException e) {
      return null;
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

      Role selectedRole = getRole(role, userRoleList, defaultWsRole, defaultRole);
      Organization selectedOrg = getOrganization(org, selectedRole, defaultRole, defaultOrg);
      // Only offer a fallback warehouse if it actually belongs to the resolved organization -
      // see resolveWarehouseFallback for why this check is required.
      Warehouse warehouseFallback = resolveWarehouseFallback(defaultWarehouse, warehouse, selectedOrg);
      Warehouse selectedWarehouse = getWarehouse(warehouse, selectedOrg, warehouseFallback, selectedRole);

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
   * Sentinel warehouse id meaning "no warehouse", used the same way {@code "0"} already means
   * "no organization" elsewhere in this codebase. Unlike the organization case, there is no real
   * {@code M_Warehouse} row with id {@code "0"} - that's precisely why it works as a sentinel
   * here: {@code BaseSecureWebServiceServlet} requires the {@code warehouse} claim to be present
   * and non-empty on every authenticated request (not just login), so the claim can never be
   * omitted; but {@code OBContext#initialize} looks the id up and silently leaves the context's
   * warehouse {@code null} when nothing matches (see its warehouse query), so this id is accepted
   * everywhere while still resolving to "no warehouse" - exactly Classic's tolerance for an
   * organization with none.
   */
  private static final String NO_WAREHOUSE_SENTINEL_ID = "0";

  /**
   * Builds a JWT token with the provided user, role, organization, warehouse, and session ID.
   * <p>
   * {@code selectedWarehouse} may be {@code null} for an organization that has none (Classic
   * tolerates this - see {@link #getWarehouse}) - in that case the {@code warehouse} claim is
   * set to {@link #NO_WAREHOUSE_SENTINEL_ID} rather than omitted, since every other request also
   * requires this claim to be present.
   *
   * @param user             The authenticated user.
   * @param selectedRole     The selected role.
   * @param selectedOrg      The selected organization.
   * @param selectedWarehouse The selected warehouse, or {@code null} if the organization has none.
   * @param sessionId        The session ID.
   * @return The JWT builder.
   */
  private static JWTCreator.Builder getJwtBuilder(User user, Role selectedRole, Organization selectedOrg,
      Warehouse selectedWarehouse, String sessionId) {
    String warehouseId = selectedWarehouse != null ? selectedWarehouse.getId() : NO_WAREHOUSE_SENTINEL_ID;

    return JWT.create().withIssuer("sws").withAudience("sws")
        .withClaim("user", user.getId())
        .withClaim("client", selectedRole.getClient().getId())
        .withClaim("role", selectedRole.getId())
        .withClaim("organization", selectedOrg.getId())
        .withClaim("warehouse", warehouseId)
        .withClaim("jti", sessionId)
        .withIssuedAt(new Date());
  }
}
