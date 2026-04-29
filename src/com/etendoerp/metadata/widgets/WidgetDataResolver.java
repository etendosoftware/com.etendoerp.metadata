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
     *
     * @param context the widget data context carrying instance, class, and parameter info
     * @return the resolved data as a JSON object
     * @throws Exception if resolution fails
     */
    @SuppressWarnings("java:S112")
    JSONObject resolve(WidgetDataContext context) throws Exception;
}
