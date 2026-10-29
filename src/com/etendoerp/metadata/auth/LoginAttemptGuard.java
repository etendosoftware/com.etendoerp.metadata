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

import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Checks New UI login credentials with the same protections as the Classic login.
 * <p>
 * {@link LoginUtils#getValidUserId} is the call Classic's {@code DefaultAuthenticationManager}
 * makes: through {@code UserLock} it delays the answer by the failed attempts of the username
 * ({@code login.trial.delay.increment}, up to {@code login.trial.delay.max}), refuses a locked user
 * and locks the user once {@code login.trial.user.lock} attempts have failed. As in Classic, every
 * failed attempt is then recorded in {@code AD_Session} (see {@link LoginSessionRegistry}), which
 * is where {@code UserLock} counts them.
 */
public final class LoginAttemptGuard {

  /** Classic message for a wrong username or password. */
  static final String INVALID_CREDENTIALS_MESSAGE = "IDENTIFICATION_FAILURE_TITLE";

  /** Classic message for a locked user. */
  static final String LOCKED_USER_MESSAGE = "LOCKED_USER_MSG";

  /** Language of the messages when the request carries no context, as on a login request. */
  static final String DEFAULT_LANGUAGE = "en_US";

  private LoginAttemptGuard() {
  }

  /**
   * Authenticates the given credentials the way Classic does.
   *
   * @param request  the login request, used to record the attempt
   * @param username the username
   * @param password the password, as entered by the user
   * @return the id of the authenticated user
   * @throws UnauthorizedException with the Classic message when the password is wrong or the user
   *                               is locked
   */
  public static String authenticate(HttpServletRequest request, String username, String password) {
    ConnectionProvider connection = new DalConnectionProvider(false);
    String userId = LoginUtils.getValidUserId(connection, username, password);
    if (userId != null) {
      return userId;
    }

    LoginSessionRegistry.register(request, username, null, LoginSessionRegistry.FAILED_STATUS);
    String messageKey = resolveFailureMessageKey(connection, username, password);
    throw new UnauthorizedException(Utility.messageBD(connection, messageKey, resolveLanguage()));
  }

  /**
   * {@link LoginUtils#getValidUserId} answers {@code null} both for a wrong password and for a
   * locked user. Like Classic, a password that is right on its own means the user is locked.
   */
  private static String resolveFailureMessageKey(ConnectionProvider connection, String username,
      String password) {
    if (LoginUtils.checkUserPassword(connection, username, password) != null) {
      return LOCKED_USER_MESSAGE;
    }
    return INVALID_CREDENTIALS_MESSAGE;
  }

  private static String resolveLanguage() {
    OBContext context = OBContext.getOBContext();
    if (context == null || context.getLanguage() == null) {
      return DEFAULT_LANGUAGE;
    }
    return context.getLanguage().getLanguage();
  }
}
