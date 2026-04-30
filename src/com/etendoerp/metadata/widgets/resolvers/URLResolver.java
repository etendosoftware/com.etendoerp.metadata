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

/** Resolves widget data for URL-type widgets by returning the iframe source URL. */
public class URLResolver implements WidgetDataResolver {
    @Override public String getType() { return "URL"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        // Instance param "src" mirrors Classic OBUrlWidget behaviour (parameters.src)
        String url = ctx.param("src");
        if (url == null || url.isEmpty()) {
            url = ctx.classString("3"); // EXTERNAL_DATA_URL fallback
        }
        return new JSONObject()
                .put("url",     url != null ? url : "")
                .put("sandbox", true);
    }
}
