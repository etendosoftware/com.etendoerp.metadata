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
