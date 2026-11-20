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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.auth;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.secureApp.DefaultValidationException;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.CachedPreference;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.server.ServerControllerHandler;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Classic {@code RestrictErpAccessInStoreServer}: on a store server with the preference set to Y, the ERP is
 * only available with the System Administrator role ({@code LoginHandler} and {@code index.jsp}). The new UI
 * applies it where a session gets its role: the login (the user's default role), the role change and the
 * single sign-on login.
 */
public final class StoreServerAccess {

    /** AD_Role_ID of System Administrator, the only role available when the access is restricted. */
    static final String SYSTEM_ADMIN_ROLE_ID = "0";

    /** Classic message shown when the access is restricted. */
    static final String RESTRICTED_MESSAGE = "BACKEND_LOGIN_RESTRICTED";

    /** Login defaults are asked for without a requested role, as the new UI login does. */
    private static final String NO_REQUESTED_ROLE = "";

    private StoreServerAccess() {
    }

    /**
     * Whether the ERP is restricted to System Administrator, as classic
     * {@code LoginHandler.isErpAccessRestrictedInStoreServer} on a store server.
     *
     * @return true on a store server with RestrictErpAccessInStoreServer = Y
     */
    public static boolean isErpAccessRestricted() {
        ServerControllerHandler server = WeldUtils.getInstanceFromStaticBeanManager(ServerControllerHandler.class);
        if (!server.isThisAStoreServer()) {
            return false;
        }
        CachedPreference preferences = WeldUtils.getInstanceFromStaticBeanManager(CachedPreference.class);
        return StringUtils.equals(Preferences.YES,
                preferences.getPreferenceValue(CachedPreference.RESTRICT_ERP_ACCESS_IN_STORE_SERVER));
    }

    /**
     * Refuses a role other than System Administrator while the access is restricted.
     *
     * @param roleId the role the session would get
     * @throws UnauthorizedException with the classic message when the role is not allowed
     */
    public static void checkRole(String roleId) {
        if (!SYSTEM_ADMIN_ROLE_ID.equals(roleId) && isErpAccessRestricted()) {
            throw new UnauthorizedException(LoginAttemptGuard.readMessage(new DalConnectionProvider(false),
                    RESTRICTED_MESSAGE));
        }
    }

    /**
     * Refuses the login of a user whose default role is not System Administrator while the access is restricted:
     * the new UI login opens the session with that role.
     *
     * @param userId the authenticated user
     * @throws UnauthorizedException with the classic message when the default role is not allowed
     */
    public static void checkLogin(String userId) {
        if (!isErpAccessRestricted()) {
            return;
        }
        checkRole(defaultRoleOf(userId));
    }

    private static String defaultRoleOf(String userId) {
        try {
            return LoginUtils.getLoginDefaults(userId, NO_REQUESTED_ROLE, new DalConnectionProvider(false)).role;
        } catch (ServletException | DefaultValidationException e) {
            // Without valid defaults the login fails later on its own; only the role decides here
            return "";
        }
    }
}
