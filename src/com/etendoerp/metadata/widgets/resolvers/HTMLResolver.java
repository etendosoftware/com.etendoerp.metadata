package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONObject;

/** Resolves widget data for HTML-type widgets by returning stored HTML content. */
public class HTMLResolver implements WidgetDataResolver {
    @Override public String getType() { return "HTML"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String content = ctx.classString("4"); // DESCRIPTION column holds HTML
        return new JSONObject().put("content", content != null ? content : "");
    }
}
