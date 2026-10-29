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

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.secureApp.DefaultValidationException;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.LoginUtils.RoleDefaults;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.data.AuthData;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Resolves the role, organization, client and warehouse a user logs in with when the login request
 * does not name them, using exactly the same rule as the Classic login
 * ({@link LoginUtils#getLoginDefaults}): the user's configured defaults when they are valid,
 * otherwise the lowest role id the user has access to, the highest organization id of that role,
 * the role's client and the lowest warehouse id of that organization.
 * <p>
 * {@code com.smf.securewebservices} picks the first entry of an unordered role list instead, so the
 * same user could get a different role in the New UI than in Classic. Every New UI login entry
 * point that has no explicit role goes through this class so both UIs always agree.
 */
public final class LoginDefaultsResolver {

  /** Classic's way of asking {@link LoginUtils#getLoginDefaults} for the user's default role. */
  private static final String NO_REQUESTED_ROLE = "";

  private LoginDefaultsResolver() {
  }

  /**
   * Resolves the Classic login defaults of the given user.
   *
   * @param user the authenticated user
   * @return the defaults; the warehouse is {@code null} when the organization has none, as in
   *     Classic
   * @throws UnprocessableContentException if the user has no valid role, organization or client
   */
  public static AuthData resolve(User user) {
    RoleDefaults defaults = getClassicDefaults(user);

    return new AuthData(user, findById(Role.class, defaults.role),
        findById(Organization.class, defaults.org), findById(Warehouse.class, defaults.warehouse),
        findById(Client.class, defaults.client));
  }

  private static RoleDefaults getClassicDefaults(User user) {
    try {
      return LoginUtils.getLoginDefaults(user.getId(), NO_REQUESTED_ROLE, new DalConnectionProvider(false));
    } catch (ServletException | DefaultValidationException e) {
      throw new UnprocessableContentException(
          "No valid login defaults for user " + user.getUsername() + ": " + e.getMessage());
    }
  }

  private static <T extends BaseOBObject> T findById(Class<T> entityClass, String id) {
    if (StringUtils.isBlank(id)) {
      return null;
    }
    return OBDal.getInstance().get(entityClass, id);
  }
}
