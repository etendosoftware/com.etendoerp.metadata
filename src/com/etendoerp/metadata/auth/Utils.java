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

public class Utils {
  private static final long ONE_MINUTE_IN_MILLIS = 60000;
  private static final String HS256_ALGORITHM = "HS256";

  private Utils() throws InstantiationException {
    throw new InstantiationException();
  }

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

  private static String cleanPrivateKey(SWSConfig config) {
    try {
      Method method = SecureWebServicesUtils.class.getDeclaredMethod("cleanPrivateKey", SWSConfig.class);
      method.setAccessible(true);

      return (String) method.invoke(null, config);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new OBException(e);
    }
  }

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

  public static String generateToken(AuthData authData, String sessionId) throws Exception {
    try {
      OBContext.setAdminMode(true);

      Algorithm algorithm;
      SWSConfig config = SWSConfig.getInstance();
      String privateKey = config.getPrivateKey();

      User user = authData.user;
      Role role = authData.role;
      Organization org = authData.org;
      Warehouse warehouse = authData.warehouse;

      List<UserRoles> userRoleList = user.getADUserRolesList();
      Role defaultWsRole = user.getSmfswsDefaultWsRole();
      Role defaultRole = user.getDefaultRole();
      Organization defaultOrg = user.getDefaultOrganization();
      Warehouse defaultWarehouse = user.getDefaultWarehouse();
      Role selectedRole = getRole(role, userRoleList, defaultWsRole, defaultRole);
      Organization selectedOrg = getOrganization(org, selectedRole, defaultRole, defaultOrg);
      Warehouse selectedWarehouse = getWarehouse(warehouse, selectedOrg, defaultWarehouse);

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

  private static String getAlgorithmUsed() throws PropertyException {
    return Preferences.getPreferenceValue("SMFSWS_EncryptionAlgorithm", true,
        OBContext.getOBContext().getCurrentClient(), OBContext.getOBContext().getCurrentOrganization(),
        OBContext.getOBContext().getUser(), OBContext.getOBContext().getRole(), null);
  }

  private static Date getExpirationDate(SWSConfig config) {
    Calendar date = Calendar.getInstance();
    long t = date.getTimeInMillis();

    return new Date(t + (config.getExpirationTime() * ONE_MINUTE_IN_MILLIS));
  }

  private static JWTCreator.Builder getJwtBuilder(User user, Role selectedRole, Organization selectedOrg,
      Warehouse selectedWarehouse, String sessionId) {
    return JWT.create().withIssuer("sws").withAudience("sws").withClaim("user", user.getId()).withClaim("client",
        selectedRole.getClient().getId()).withClaim("role", selectedRole.getId()).withClaim("organization",
        selectedOrg.getId()).withClaim("warehouse", selectedWarehouse.getId()).withClaim("jti", sessionId).withIssuedAt(
        new Date());
  }
}
