package com.etendoerp.metadata.widgets;

import org.codehaus.jettison.json.JSONObject;

/**
 * Contract for all widget data resolvers.
 * Each resolver handles one widget TYPE value from ETMETA_WIDGET_CLASS.
 */
public interface WidgetDataResolver {
    /** Must match ETMETA_WIDGET_CLASS.TYPE exactly (e.g. "KPI", "HTML"). */
    String getType();

    /**
     * Resolves and returns the data payload for the given widget instance.
     * Return value becomes the "data" field inside the standard response envelope.
     */
    JSONObject resolve(WidgetDataContext context) throws Exception;
}
