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
