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

import java.util.Date;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.data.RevokedToken;

/**
 * Per-{@code jti} JWT revocation blacklist backed by {@code ETMETA_REVOKED_TOKEN}.
 * <p>
 * Isolated from {@code LogoutService} (which writes) and {@code BaseWebService} (which reads)
 * so both depend on two plain static methods instead of duplicating Hibernate query code —
 * mirrors how {@code PasswordExpirationUtils} already isolates the password-expiry check.
 */
public class TokenRevocationStore {

    private static final String COUNT_BY_JTI_HQL =
            "select count(r) from etmeta_Revoked_Token r where r.jti = :jti";

    private static final String DELETE_EXPIRED_HQL =
            "delete from etmeta_Revoked_Token r where r.expiresAt is not null and r.expiresAt < :now";

    private TokenRevocationStore() { }

    /**
     * @param jti the token's {@code jti} claim
     * @return {@code true} if this jti has been revoked; {@code false} for a blank/null jti too
     */
    public static boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Query<Long> query = OBDal.getInstance().getSession().createQuery(COUNT_BY_JTI_HQL, Long.class);
        query.setParameter("jti", jti);
        return query.uniqueResult() > 0;
    }

    /**
     * Revokes a jti (idempotent) and opportunistically purges expired entries so the table stays
     * bounded without a scheduled cleanup process.
     *
     * @param jti       the token's {@code jti} claim
     * @param expiresAt the token's original expiration, or {@code null} if it never expires
     */
    public static void revoke(String jti, Date expiresAt) {
        OBDal.getInstance().getSession().createQuery(DELETE_EXPIRED_HQL)
                .setParameter("now", new Date())
                .executeUpdate();

        if (isRevoked(jti)) {
            return;
        }

        try {
            RevokedToken revoked = OBProvider.getInstance().get(RevokedToken.class);
            revoked.setJti(jti);
            revoked.setExpiresAt(expiresAt);
            OBDal.getInstance().save(revoked);
            OBDal.getInstance().flush();
        } catch (ConstraintViolationException concurrentDoubleLogout) {
            // Another request revoked the same jti between the isRevoked() check above and this
            // insert's flush. The DB's unique constraint is the real safety net for that race —
            // either way, the jti ends up revoked, so there's nothing left to do here.
        }
    }
}
