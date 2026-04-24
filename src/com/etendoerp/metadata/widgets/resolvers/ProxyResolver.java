package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONObject;

/**
 * Fallback resolver: proxies GET to EXTERNAL_DATA_URL with the caller's bearer token.
 * Used when no typed resolver is found but EXTERNAL_DATA_URL is set on the widget class.
 */
public class ProxyResolver implements WidgetDataResolver {
    @Override public String getType() { return "PROXY"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String url   = ctx.classString("3"); // EXTERNAL_DATA_URL
        String token = ctx.getBearerToken();
        if (url == null) return new JSONObject().put("available", false).put("reason", "no_external_url");

        HttpGet get = new HttpGet(url);
        if (token != null) get.setHeader("Authorization", token);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(get)) {
            String body = EntityUtils.toString(resp.getEntity(), "UTF-8");
            try {
                return new JSONObject(body);
            } catch (Exception e) {
                return new JSONObject().put("result", body);
            }
        }
    }
}
