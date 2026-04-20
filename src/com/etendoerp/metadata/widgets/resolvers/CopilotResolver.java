package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

/**
 * Proxies a request to the Copilot API with the user's bearer token and org context.
 * EXTERNAL_DATA_URL must point to the Copilot conversation/suggest endpoint.
 */
public class CopilotResolver implements WidgetDataResolver {
    @Override public String getType() { return "COPILOT"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String url   = ctx.classString("3"); // EXTERNAL_DATA_URL
        String token = ctx.getBearerToken();
        if (url == null) return new JSONObject().put("messages", new JSONArray());

        JSONObject payload = new JSONObject()
                .put("userId",   ctx.getObContext().getUser().getId())
                .put("clientId", ctx.getObContext().getCurrentClient().getId());

        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        if (token != null) post.setHeader("Authorization", token);
        post.setEntity(new StringEntity(payload.toString(), "UTF-8"));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(post)) {
            String body = EntityUtils.toString(resp.getEntity(), "UTF-8");
            return new JSONObject(body);
        }
    }
}
