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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;

/**
 * Proxies a request to the Copilot API with the user's bearer token and org context.
 * EXTERNAL_DATA_URL must point to the Copilot conversation/suggest endpoint.
 */
public class CopilotResolver implements WidgetDataResolver {
    @Override public String getType() { return "COPILOT"; }

    @Override
    public boolean isAvailable() {
        Number count = (Number) OBDal.getInstance().getSession()
                .createNativeQuery(
                    "SELECT COUNT(*) FROM ad_module WHERE javapackage LIKE '%copilot%' AND isactive = 'Y'")
                .uniqueResult();
        return count != null && count.longValue() > 0;
    }

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
