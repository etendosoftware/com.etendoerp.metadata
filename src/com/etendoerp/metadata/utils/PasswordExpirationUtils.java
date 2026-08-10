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

package com.etendoerp.metadata.utils;

import java.util.Calendar;
import java.util.Date;

import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;

/**
 * Resolves whether a user's password has expired, replicating the rule Etendo Classic applies at
 * login in {@code org.openbravo.authentication.basic.DefaultAuthenticationManager#checkIfPasswordExpired}.
 * <p>
 * A password is considered expired when either of these holds:
 * </p>
 * <ul>
 * <li>the administrator flagged it manually ({@code AD_User.Isexpiredpassword = 'Y'}), or</li>
 * <li>the client defines a validity window ({@code AD_Client.DaysToPasswordExpiration > 0}) and
 * {@code AD_User.LastPasswordUpdate} plus that many days has already been reached.</li>
 * </ul>
 * <p>
 * The new UI consumes the result through the {@code passwordExpired} flag of the session payload and
 * through the guard that blocks the metadata data plane while the password is expired.
 * </p>
 */
public class PasswordExpirationUtils {

    private PasswordExpirationUtils() { }

    /**
     * Tells whether the given user must change their password before being granted access.
     *
     * @param user the user to evaluate; {@code null} is treated as not expired
     * @return {@code true} when the password is expired, {@code false} otherwise
     */
    public static boolean isExpired(User user) {
        if (user == null) {
            return false;
        }

        OBContext.setAdminMode(true);
        try {
            return Boolean.TRUE.equals(user.isPasswordExpired()) || hasReachedValidityWindow(user);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Tells whether the client's password validity window has already elapsed for the given user.
     *
     * @param user the user to evaluate
     * @return {@code true} when the client defines a validity window and it has been reached
     */
    private static boolean hasReachedValidityWindow(User user) {
        Long validityDays = getValidityDays(user);
        Date lastPasswordUpdate = user.getLastPasswordUpdate();

        if (validityDays == null || validityDays <= 0 || lastPasswordUpdate == null) {
            return false;
        }

        Calendar expirationDate = Calendar.getInstance();
        expirationDate.setTimeInMillis(lastPasswordUpdate.getTime());
        expirationDate.add(Calendar.DATE, validityDays.intValue());

        return expirationDate.getTime().compareTo(new Date()) <= 0;
    }

    /**
     * Reads the password validity window configured on the user's client.
     *
     * @param user the user whose client is inspected
     * @return the configured days, or {@code null} when there is no client or no configuration
     */
    private static Long getValidityDays(User user) {
        Client client = user.getClient();

        return client != null ? client.getDaysToPasswordExpiration() : null;
    }
}
