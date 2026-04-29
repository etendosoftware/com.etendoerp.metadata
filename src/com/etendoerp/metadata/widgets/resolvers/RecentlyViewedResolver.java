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

package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONObject;

/**
 * Returns recently viewed windows and entities.
 *
 * NOTE: Data currently lives in browser localStorage (tracked by the frontend navigation layer).
 * The resolver signals this to the frontend via source="localStorage" so it can read local data
 * instead of expecting items from the backend.
 *
 * TODO: Migrate to Option A — create POST /meta/navigation/track endpoint, store in
 * ETMETA_NAV_LOG, and query here. See docs/adr/widget-navigation-data-source.md.
 */
public class RecentlyViewedResolver implements WidgetDataResolver {
    @Override public String getType() { return "RECENTLY_VIEWED"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        return new JSONObject().put("source", "localStorage");
    }
}
