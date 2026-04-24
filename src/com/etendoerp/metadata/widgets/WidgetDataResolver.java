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
     * Returns true if the required modules/entities for this resolver are available.
     * Override to check for optional Hibernate entity mappings or external dependencies.
     * Default: true (assumes always available).
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Resolves and returns the data payload for the given widget instance.
     * Return value becomes the "data" field inside the standard response envelope.
     * Only called when isAvailable() returns true.
     */
    JSONObject resolve(WidgetDataContext context) throws Exception;
}
