/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.widgets;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import java.time.Instant;

/**
 * Builds the standard response envelope for GET /meta/widget/{id}/data.
 *
 * {
 *   "widgetInstanceId": "...",
 *   "type": "KPI",
 *   "data": { ... },
 *   "meta": { "lastUpdate": "...", "totalRows": null, "hasMore": false }
 * }
 */
public class WidgetDataResponse {

    private WidgetDataResponse() {
    }

    /**
     * Builds the standard response envelope for a widget data request.
     *
     * @param instanceId the widget instance identifier
     * @param type       the widget type
     * @param data       the resolved data payload
     * @param totalRows  total row count, or {@code null} if unknown
     * @return the JSON envelope
     * @throws JSONException if JSON construction fails
     */
    public static JSONObject build(String instanceId, String type, JSONObject data,
                                   Integer totalRows) throws JSONException {
        JSONObject meta = new JSONObject()
                .put("lastUpdate", Instant.now().toString())
                .put("totalRows", totalRows != null ? totalRows : JSONObject.NULL)
                .put("hasMore", false);

        return new JSONObject()
                .put("widgetInstanceId", instanceId)
                .put("type", type)
                .put("data", data)
                .put("meta", meta);
    }

    /**
     * Builds the standard response envelope with no total row count.
     *
     * @param instanceId the widget instance identifier
     * @param type       the widget type
     * @param data       the resolved data payload
     * @return the JSON envelope
     * @throws JSONException if JSON construction fails
     */
    public static JSONObject build(String instanceId, String type, JSONObject data)
            throws JSONException {
        return build(instanceId, type, data, null);
    }

    /**
     * Returns an envelope signaling the widget is unavailable (required module not installed).
     * The frontend should hide or disable this widget instead of rendering it.
     *
     * @param instanceId the widget instance identifier
     * @param type       the widget type
     * @return a JSON envelope with {@code available: false}
     * @throws JSONException if the JSON object cannot be constructed
     */
    public static JSONObject unavailable(String instanceId, String type) throws JSONException {
        return new JSONObject()
                .put("widgetInstanceId", instanceId)
                .put("type", type)
                .put("available", false)
                .put("data", JSONObject.NULL)
                .put("meta", JSONObject.NULL);
    }
}
