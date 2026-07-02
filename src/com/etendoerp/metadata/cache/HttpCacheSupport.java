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

package com.etendoerp.metadata.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Language;

import com.etendoerp.metadata.utils.Constants;

/**
 * Computes lightweight, DB-metadata-driven ETags for the "metadata-definition" endpoints
 * ({@code /meta/menu}, {@code /meta/session}, {@code /meta/labels}) and resolves conditional
 * GET requests ({@code If-None-Match}) so that unchanged responses can be answered with a
 * {@code 304 Not Modified} without running the expensive service/builder logic.
 * <p>
 * The fingerprint for each endpoint is derived from cheap, already-authenticated context
 * (role/user/language) plus a {@code max(updated)} aggregate over the relevant AD entity,
 * rather than hashing the full response body. This avoids buffering the JSON payload.
 * <p>
 * {@code /meta/labels} has no single existing "module version" concept that is cheaply
 * queryable per-request; {@code max(AD_Module.updated)} across enabled modules is used as
 * the practical equivalent, since any module install/update touches that column.
 *
 * @author Futit Services S.L.
 */
public final class HttpCacheSupport {
    private static final Logger logger = LogManager.getLogger(HttpCacheSupport.class);

    private static final Set<String> CACHEABLE_PATHS = Set.of(
            Constants.SESSION_PATH, Constants.MENU_PATH, Constants.LABELS_PATH);

    private static final Set<String> CACHEABLE_METHODS = Set.of(Constants.GET, Constants.HEAD);

    private static final String MENU_MAX_UPDATED_HQL =
            "select max(m." + Constants.UPDATED + ") from ADMenu m where m.module.enabled = true";
    private static final String ROLE_MAX_UPDATED_HQL =
            "select max(r." + Constants.UPDATED + ") from ADRole r where r.id = :roleId";
    private static final String MODULE_MAX_UPDATED_HQL =
            "select max(m." + Constants.UPDATED + ") from ADModule m where m.enabled = true";

    private HttpCacheSupport() {
    }

    /**
     * Determines whether the given request targets one of the allowlisted, GET/HEAD-only
     * metadata-definition endpoints eligible for ETag-based caching.
     *
     * @param req            the incoming HTTP request
     * @param normalizedPath the request path already normalized by {@code ServiceFactory.normalizePath}
     * @return true if the request may be answered with a conditional GET check
     */
    public static boolean isCacheable(HttpServletRequest req, String normalizedPath) {
        String method = req.getMethod();

        return method != null && CACHEABLE_METHODS.contains(method) && CACHEABLE_PATHS.contains(normalizedPath);
    }

    /**
     * Computes the ETag for the given cacheable path, or {@code null} if it cannot be
     * computed safely (missing authenticated context, unexpected error). A {@code null}
     * return means the caller must proceed without any caching for this request.
     *
     * @param normalizedPath one of the allowlisted cacheable paths
     * @return a quoted, strong ETag value, or {@code null}
     */
    public static String computeETag(String normalizedPath) {
        try {
            String fingerprint;

            if (Constants.MENU_PATH.equals(normalizedPath)) {
                fingerprint = fingerprintMenu();
            } else if (Constants.SESSION_PATH.equals(normalizedPath)) {
                fingerprint = fingerprintSession();
            } else if (Constants.LABELS_PATH.equals(normalizedPath)) {
                fingerprint = fingerprintLabels();
            } else {
                return null;
            }

            return fingerprint != null ? sha256Hex(fingerprint) : null;
        } catch (Exception e) {
            logger.warn("Could not compute ETag for {}: {}", normalizedPath, e.getMessage());

            return null;
        }
    }

    /**
     * Checks whether the given {@code If-None-Match} header value matches the computed ETag,
     * per RFC 7232 semantics (wildcard and comma-separated multi-value support).
     *
     * @param ifNoneMatchHeader the raw {@code If-None-Match} request header, may be null
     * @param etag              the freshly computed ETag for the current response
     * @return true if the client's cached copy is still valid
     */
    public static boolean matches(String ifNoneMatchHeader, String etag) {
        if (ifNoneMatchHeader == null || ifNoneMatchHeader.isBlank() || etag == null) {
            return false;
        }

        String trimmed = ifNoneMatchHeader.trim();

        if ("*".equals(trimmed)) {
            return true;
        }

        for (String candidate : trimmed.split(",")) {
            String token = candidate.trim();

            if (token.startsWith("W/")) {
                token = token.substring(2);
            }

            if (token.equals(etag)) {
                return true;
            }
        }

        return false;
    }

    private static String fingerprintMenu() {
        OBContext context = OBContext.getOBContext();
        Role role = context != null ? context.getRole() : null;
        Language language = context != null ? context.getLanguage() : null;

        if (role == null || language == null) {
            return null;
        }

        Date maxUpdated = executeMaxUpdated(MENU_MAX_UPDATED_HQL);

        return role.getId() + "|" + language.getId() + "|" + timestampOf(maxUpdated);
    }

    private static String fingerprintSession() {
        OBContext context = OBContext.getOBContext();
        User user = context != null ? context.getUser() : null;
        Role role = context != null ? context.getRole() : null;

        if (user == null || role == null) {
            return null;
        }

        Query<Date> query = OBDal.getInstance().getSession().createQuery(ROLE_MAX_UPDATED_HQL, Date.class);
        query.setParameter("roleId", role.getId());
        Date maxUpdated = query.uniqueResult();

        return user.getId() + "|" + role.getId() + "|" + timestampOf(maxUpdated);
    }

    private static String fingerprintLabels() {
        OBContext context = OBContext.getOBContext();
        Language language = context != null ? context.getLanguage() : null;

        if (language == null) {
            return null;
        }

        Date maxUpdated = executeMaxUpdated(MODULE_MAX_UPDATED_HQL);

        return language.getId() + "|" + timestampOf(maxUpdated);
    }

    private static Date executeMaxUpdated(String hql) {
        Query<Date> query = OBDal.getInstance().getSession().createQuery(hql, Date.class);

        return query.uniqueResult();
    }

    private static long timestampOf(Date date) {
        return date != null ? date.getTime() : 0L;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2 + 2);
            hex.append('"');

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            hex.append('"');

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 not available", e);

            return null;
        }
    }
}
