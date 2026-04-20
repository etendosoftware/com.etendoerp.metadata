package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONObject;

public class URLResolver implements WidgetDataResolver {
    @Override public String getType() { return "URL"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String url = ctx.classString("3"); // EXTERNAL_DATA_URL
        return new JSONObject()
                .put("url",     url != null ? url : "")
                .put("sandbox", true);
    }
}
