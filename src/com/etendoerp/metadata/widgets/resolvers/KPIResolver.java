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
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

import java.util.Map;

/**
 * Executes HQL_QUERY from ETMETA_WIDGET_CLASS, expects a single scalar result.
 * Named params in HQL (e.g. :period) are filled from the instance params map.
 */
public class KPIResolver implements WidgetDataResolver {
    private static final String CHART_TYPE = "chartType";

    @Override public String getType() { return "KPI"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String hql = ctx.classString("4"); // HQL_QUERY
        if (hql == null) return new JSONObject().put("value", JSONObject.NULL);

        Query<Object> q = OBDal.getInstance().getSession().createQuery(hql, Object.class);
        for (Map.Entry<String, Object> entry : ctx.getParams().entrySet()) {
            try { q.setParameter(entry.getKey(), entry.getValue()); } catch (Exception ignored) { /* param not in HQL, skip */ }
        }
        Object value = q.uniqueResult();

        return new JSONObject()
                .put("value",     value)
                .put("unit",      ctx.param("unit"))
                .put("label",     ctx.param("label"))
                .put("trend",     ctx.param("trend"))
                .put(CHART_TYPE, ctx.param(CHART_TYPE) != null ? ctx.param(CHART_TYPE) : "number");
    }
}
