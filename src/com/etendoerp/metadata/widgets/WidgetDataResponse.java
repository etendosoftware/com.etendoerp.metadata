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

    public static JSONObject build(String instanceId, String type, JSONObject data)
            throws JSONException {
        return build(instanceId, type, data, null);
    }
}
