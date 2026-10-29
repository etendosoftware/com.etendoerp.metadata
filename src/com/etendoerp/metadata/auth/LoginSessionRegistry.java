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

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.HttpBaseUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.security.SessionLogin;
import org.openbravo.model.ad.access.Session;

/**
 * Records New UI login attempts in the Classic session registry ({@code AD_Session}) and closes
 * them on logout, the same way the Classic login does.
 * <p>
 * {@code AuthenticationManager#createDBSession} and {@code #updateDBSession} hold this logic in
 * Classic, but both are {@code protected final}, so it is reproduced here on top of the public
 * {@link SessionLogin}. The rows also feed {@code UserLock}: it counts the {@link #FAILED_STATUS}
 * rows of a username since its last other status, so every attempt must carry the username.
 */
public final class LoginSessionRegistry {

  /** {@code LOGIN_STATUS} of a successful login. */
  public static final String SUCCESS_STATUS = "S";

  /** {@code LOGIN_STATUS} of a failed login: wrong password or locked user. */
  public static final String FAILED_STATUS = "F";

  /** Response and request key carrying the {@code AD_Session_ID} of a login. */
  public static final String SESSION_ID = "sessionId";

  /** Client, organization and user Classic stores for an attempt without a known user. */
  private static final String SYSTEM_ID = "0";

  private static final Logger log = LogManager.getLogger(LoginSessionRegistry.class);

  private LoginSessionRegistry() {
  }

  /**
   * Records a login attempt. Without a user id the attempt is recorded as
   * {@link #FAILED_STATUS}; otherwise with the given success status.
   * <p>
   * Like Classic, a failure to record never blocks the login: it is logged and {@code null} is
   * returned.
   *
   * @param request       the login request, used for the remote address, host and web session
   * @param username      the username the attempt was made with
   * @param userId        the authenticated user id, or {@code null} for a failed attempt
   * @param successStatus the status recorded when the attempt succeeded
   * @return the {@code AD_Session_ID} of the new record, or {@code null} when nothing was recorded
   */
  public static String register(HttpServletRequest request, String username, String userId,
      String successStatus) {
    try {
      if (userId == null && StringUtils.isEmpty(username)) {
        return null;
      }

      if (request == null) {
        throw new OBException("Request object is null, cannot create DB session");
      }

      SessionLogin sessionLogin = new SessionLogin(request, SYSTEM_ID, SYSTEM_ID,
          StringUtils.defaultString(userId, SYSTEM_ID));
      sessionLogin.setStatus(resolveStatus(userId, successStatus));
      sessionLogin.setUserName(username);
      sessionLogin.setServerUrl(HttpBaseUtils.getLocalAddress(request));
      sessionLogin.save();
      return sessionLogin.getSessionID();
    } catch (Exception e) {
      log.error("Error creating DB session", e);
      return null;
    }
  }

  /**
   * Marks a session of the given user as no longer active, as Classic does when its HTTP session
   * ends. A session that does not exist or belongs to another user is left untouched, so a caller
   * can only close its own sessions.
   * <p>
   * Best-effort: logout must never fail because of the registry.
   *
   * @param sessionId the {@code AD_Session_ID} to close
   * @param userId    the id of the user closing it
   */
  public static void close(String sessionId, String userId) {
    if (StringUtils.isBlank(sessionId)) {
      return;
    }

    try {
      OBContext.setAdminMode(true);
      Session session = OBDal.getInstance().get(Session.class, sessionId);
      if (!isOwnedBy(session, userId)) {
        log.warn("Session {} not closed: it does not belong to the current user", sessionId);
        return;
      }

      session.setSessionActive(false);
      OBDal.getInstance().flush();
    } catch (Exception e) {
      log.warn("Could not close session {}", sessionId, e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private static String resolveStatus(String userId, String successStatus) {
    if (userId == null) {
      return FAILED_STATUS;
    }
    return successStatus;
  }

  private static boolean isOwnedBy(Session session, String userId) {
    if (session == null || session.getCreatedBy() == null) {
      return false;
    }
    return StringUtils.equals(session.getCreatedBy().getId(), userId);
  }
}
