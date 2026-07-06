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

package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.utils.Constants;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable value object that holds every parameter the frontend needs to build the Classic
 * iframe URL for a legacy (HTML-template) process button.
 *
 * <p><strong>Required fields</strong> — always serialized:
 * <ul>
 *   <li>{@code url} — the Classic HTML page path (e.g.
 *       {@code /FinancialAccount/Transaction_Edition.html}).</li>
 *   <li>{@code command} — the WAD {@code Command} query parameter (e.g.
 *       {@code BUTTONDocAction104} or {@code DEFAULT}).</li>
 *   <li>{@code keyColumnName} / {@code inpkeyColumnId} — both carry the DB name of the
 *       tab's primary-key column. Classic expects both query-string keys with identical
 *       values; keeping them as separate fields makes the intent explicit.</li>
 * </ul>
 *
 * <p><strong>Optional field</strong> — omitted when empty:
 * <ul>
 *   <li>{@code additionalParameters} — a snapshot of the {@code inp*} entries that the
 *       Classic {@code *_Edition.html} form would normally pre-format as hidden inputs via
 *       its XSQL template. One entry per active, includable column of the tab's table.
 *       Values use the placeholder grammar {@code $record.<jpaPropertyName>[!coercion]}
 *       (e.g. {@code $record.client}, {@code $record.processed!yn},
 *       {@code $record.accountingDate!date}) so the client resolves them against the
 *       SmartClient record JSON at button-click time without an extra round-trip.</li>
 * </ul>
 *
 * @see LegacyProcessResolver
 */
public final class LegacyProcessParams {

    private final String url;
    private final String command;
    private final String keyColumnName;
    private final String inpkeyColumnId;
    private final Map<String, String> additionalParameters;

    /**
     * Convenience constructor for processes that do not require per-column
     * additional parameters (e.g. simple DocAction buttons resolved by the
     * client-side {@code data.json} static mapping).
     *
     * <p>Equivalent to calling the full constructor with an empty map.
     *
     * @param url           the Classic HTML page URL (e.g.
     *                      {@code /SalesOrder/Header_Edition.html})
     * @param command       the WAD {@code Command} parameter value (e.g.
     *                      {@code BUTTONDocAction104})
     * @param keyColumnName the DB name of the tab's primary-key column sent as
     *                      {@code keyColumnName} in the URL query string
     * @param inpkeyColumnId the same PK column name sent as {@code inpkeyColumnId};
     *                       kept as a separate parameter because Classic expects both
     *                       query-string keys with identical values
     */
    public LegacyProcessParams(String url, String command, String keyColumnName, String inpkeyColumnId) {
        this(url, command, keyColumnName, inpkeyColumnId, Collections.emptyMap());
    }

    /**
     * Full constructor including the per-column {@code inp*} parameter snapshot.
     *
     * <p>The {@code additionalParameters} map entries follow the format
     * {@code "inp{camelCase(DBColumnName)}" → "$record.{jpaPropertyName}[!coercion]"} as
     * produced by {@link LegacyProcessResolver}. The client resolves the
     * {@code $record.*} placeholders against the SmartClient record JSON at click time.
     *
     * <p>The map is defensively copied via {@link Map#copyOf(Map)} and is therefore
     * immutable after construction. A {@code null} argument is treated as an empty map.
     *
     * @param url                  the Classic HTML page URL
     * @param command              the WAD {@code Command} parameter value
     * @param keyColumnName        the DB name of the primary-key column ({@code keyColumnName}
     *                             query parameter)
     * @param inpkeyColumnId       the DB name of the primary-key column ({@code inpkeyColumnId}
     *                             query parameter)
     * @param additionalParameters ordered map of {@code inp*} keys to {@code $record.*}
     *                             placeholder values; may be {@code null} or empty
     */
    public LegacyProcessParams(String url, String command, String keyColumnName, String inpkeyColumnId,
                               Map<String, String> additionalParameters) {
        this.url = url;
        this.command = command;
        this.keyColumnName = keyColumnName;
        this.inpkeyColumnId = inpkeyColumnId;
        this.additionalParameters = additionalParameters == null
                ? Collections.emptyMap()
                : Map.copyOf(additionalParameters);
    }

    /**
     * Serializes these parameters into a {@link JSONObject} using the shared key constants
     * defined in {@link Constants}.
     *
     * <p>The four required fields ({@code url}, {@code command}, {@code keyColumnName},
     * {@code inpkeyColumnId}) are always present in the output. The
     * {@code additionalParameters} object is included only when the map is non-empty, so
     * consumers that pre-date this feature continue to receive the same JSON structure they
     * already handle.
     *
     * <p>Example output with additional parameters:
     * <pre>{@code
     * {
     *   "url": "/FinancialAccount/Transaction_Edition.html",
     *   "command": "BUTTONEM_Aprm_ProcessedF68F2890E96D4D85A1DEF0274D105BCE",
     *   "keyColumnName": "Fin_Finacc_Transaction_ID",
     *   "inpkeyColumnId": "Fin_Finacc_Transaction_ID",
     *   "additionalParameters": {
     *     "inpadClientId":              "$record.client",
     *     "inpadOrgId":                 "$record.organization",
     *     "inpemAprmProcessed":         "$record.emAprmProcessed",
     *     "inpprocessed":               "$record.processed!yn",
     *     "inpdateacct":                "$record.accountingDate!date"
     *   }
     * }
     * }</pre>
     *
     * @return a {@link JSONObject} representation of these parameters; never {@code null}
     * @throws JSONException if the underlying JSON library fails to construct the object
     *                       (in practice this should never happen for string values)
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(Constants.LEGACY_URL, url);
        json.put(Constants.LEGACY_COMMAND, command);
        json.put(Constants.LEGACY_KEY_COLUMN_NAME, keyColumnName);
        json.put(Constants.LEGACY_INP_KEY_COLUMN_ID, inpkeyColumnId);
        if (!additionalParameters.isEmpty()) {
            JSONObject additional = new JSONObject();
            for (Map.Entry<String, String> entry : additionalParameters.entrySet()) {
                additional.put(entry.getKey(), entry.getValue());
            }
            json.put(Constants.LEGACY_ADDITIONAL_PARAMETERS, additional);
        }
        return json;
    }
}
