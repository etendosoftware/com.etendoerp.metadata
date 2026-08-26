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

package com.etendoerp.metadata.http;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.utils.Utils;

/**
 * Detects whether a buffered datasource save response reports a stale-object (optimistic-lock)
 * conflict, and rewrites it as a distinct, structured HTTP 409 body.
 *
 * <p>Shared by every entry point that can see an add/update save response before it reaches the
 * real client -- currently {@link ForwarderServlet} (the {@code sws/.../forward} path used for
 * reads) and {@link StaleObjectConflictFilter} (the direct {@code org.openbravo.service.datasource}
 * path used for add/update/remove operations, see {@code getDatasourceEndpoint} in the frontend's
 * {@code endpoints.ts}, which deliberately bypasses the forward path for those operations).</p>
 */
final class StaleObjectConflictDetector {

    private static final Logger log4j = LogManager.getLogger(StaleObjectConflictDetector.class);

    /** AD_Message code the core throws when an update/save loses an optimistic-lock check. */
    private static final String STALE_MARKER_JSON = "@OBJSON_StaleDate@";
    /** AD_Message code used by payment-related action handlers for the same kind of conflict. */
    private static final String STALE_MARKER_APRM = "@APRM_StaleDate@";
    private static final String STALE_CODE_JSON = "OBJSON_StaleDate";
    private static final String STALE_CODE_APRM = "APRM_StaleDate";
    private static final String STALE_OBJECT_CODE = "STALE_OBJECT";

    /** Caches the translated AD_Message text per "language|code" so repeated failed saves don't re-query the DB. */
    private static final Map<String, String> messageCache = new ConcurrentHashMap<>();

    private StaleObjectConflictDetector() {
    }

    /**
     * Returns {@link #STALE_MARKER_JSON} or {@link #STALE_MARKER_APRM} if {@code body} reports a
     * stale-object conflict for that AD_Message code, or {@code null} if it reports no conflict.
     *
     * <p>The core throws {@code OBStaleObjectException("@OBJSON_StaleDate@")} internally, but by
     * the time the response reaches this servlet, {@code JsonUtils.convertExceptionToJson} has
     * already resolved that {@code @CODE@} placeholder into its translated {@code AD_Message}
     * text (via {@code OBMessageUtils.translateError} / {@code ErrorTextParserPOSTGRE}) — the raw
     * marker is never actually present in a real response. The raw-marker check below is kept as
     * a cheap first pass (and covers any caller that skips that translation step); the fallback
     * resolves the current session language's translated text for both codes and matches on that
     * instead, since that is what a real conflict response actually contains.</p>
     */
    static String resolveStaleMarker(String body) {
        if (body.contains(STALE_MARKER_JSON)) {
            return STALE_MARKER_JSON;
        }
        if (body.contains(STALE_MARKER_APRM)) {
            return STALE_MARKER_APRM;
        }
        // Successful saves never carry an "error" object; skip the AD_Message lookups for them.
        if (!body.contains("\"error\"")) {
            return null;
        }
        String translatedJson = translateMessage(STALE_CODE_JSON);
        if (translatedJson != null && !translatedJson.isEmpty() && body.contains(translatedJson)) {
            return STALE_MARKER_JSON;
        }
        String translatedAprm = translateMessage(STALE_CODE_APRM);
        if (translatedAprm != null && !translatedAprm.isEmpty() && body.contains(translatedAprm)) {
            return STALE_MARKER_APRM;
        }
        return null;
    }

    /**
     * Resolves the current session language's translated text for an {@code AD_Message} code,
     * caching the result per "language|code" pair, or {@code null} if it can't be resolved
     * (e.g. no active {@link OBContext}).
     */
    private static String translateMessage(String code) {
        try {
            String language = OBContext.getOBContext().getLanguage().getLanguage();
            // No flush: this is a read-only AD_Message lookup on a save-error response path,
            // it must not force-persist whatever partial state the failed save left in the session.
            return messageCache.computeIfAbsent(language + "|" + code,
                    k -> Utility.messageBD(new DalConnectionProvider(false), code, language));
        } catch (Exception e) {
            log4j.warn("Could not resolve AD_Message text for code {}: {}", code, e.getMessage());
            return null;
        }
    }

    /**
     * Writes a structured HTTP 409 conflict body ({@code {error, code: "STALE_OBJECT", cid}}) to
     * {@code response}, where {@code error} is the raw {@code marker} returned by
     * {@link #resolveStaleMarker(String)} (so the frontend's own {@code isStaleObjectError}
     * substring check keeps matching, regardless of which AD_Message text triggered detection).
     */
    static void writeConflictResponse(HttpServletResponse response, String correlationId, String marker)
            throws IOException {
        JSONObject json = new JSONObject();
        try {
            json.put("error", marker);
            json.put("code", STALE_OBJECT_CODE);
            json.put("cid", correlationId);
        } catch (JSONException e) {
            log4j.error("Error building conflict response JSON", e);
        }
        Utils.writeJsonResponse(response, HttpStatus.SC_CONFLICT, json.toString());
    }

    /** Generates a fresh correlation id for a detected conflict, for logging/response purposes. */
    static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
