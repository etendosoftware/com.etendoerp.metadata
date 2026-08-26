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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

/**
 * Revocation blacklist backed by {@code ETMETA_REVOKED_TOKEN}, keyed by a SHA-256 hash of the
 * full raw token rather than a {@code jti} claim - not every token issuer sets one (classic
 * {@code /sws/login} doesn't), but every token has a raw string regardless.
 * <p>
 * Isolated from {@code LogoutService} (which writes) and {@code BaseWebService} (which reads)
 * so both depend on two plain static methods instead of duplicating Hibernate query code —
 * mirrors how {@code PasswordExpirationUtils} already isolates the password-expiry check.
 * <p>
 * This is a system-level security table, not business data scoped to a caller's role — every
 * access runs in admin mode (same pattern as {@code LoginService}/{@code auth.Utils#generateToken})
 * so a role without an explicit table grant (the common case, since this table has no window)
 * can still be checked and written to.
 */
public class TokenRevocationStore {

    private static final String COUNT_BY_HASH_HQL =
            "select count(r) from ETMETA_Revoked_Token r where r.tokenHash = :tokenHash";

    private static final String DELETE_EXPIRED_HQL =
            "delete from ETMETA_Revoked_Token r where r.expiresAt is not null and r.expiresAt < :now";

    private static final String SHA_256 = "SHA-256";

    private TokenRevocationStore() { }

    /**
     * @param rawToken the full raw token string
     * @return {@code true} if this token has been revoked; {@code false} for a blank/null token too
     */
    public static boolean isRevoked(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return false;
        }
        try {
            OBContext.setAdminMode(true);
            Query<Long> query = OBDal.getInstance().getSession().createQuery(COUNT_BY_HASH_HQL, Long.class);
            query.setParameter("tokenHash", hash(rawToken));
            return query.uniqueResult() > 0;
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Revokes a token (idempotent) and opportunistically purges expired entries so the table
     * stays bounded without a scheduled cleanup process.
     *
     * @param rawToken  the full raw token string
     * @param expiresAt the token's original expiration, or {@code null} if it never expires
     */
    public static void revoke(String rawToken, Date expiresAt) {
        try {
            OBContext.setAdminMode(true);

            OBDal.getInstance().getSession().createQuery(DELETE_EXPIRED_HQL)
                    .setParameter("now", new Date())
                    .executeUpdate();

            if (isRevoked(rawToken)) {
                return;
            }

            try {
                RevokedToken revoked = OBProvider.getInstance().get(RevokedToken.class);
                revoked.setTokenHash(hash(rawToken));
                revoked.setExpiresAt(expiresAt);
                OBDal.getInstance().save(revoked);
                OBDal.getInstance().flush();
            } catch (ConstraintViolationException concurrentDoubleLogout) {
                // Another request revoked the same token between the isRevoked() check above and
                // this insert's flush. The DB's unique constraint is the real safety net for that
                // race — either way, the token ends up revoked, so there's nothing left to do here.
            }
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * @param rawToken the full raw token string, never {@code null} (callers already guard that)
     * @return the hex-encoded SHA-256 digest of {@code rawToken}
     */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a guaranteed JDK algorithm (JLS MessageDigest spec) - this can't
            // actually happen, but the checked exception has to go somewhere.
            throw new OBException(e);
        }
    }
}
