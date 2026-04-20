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
    @Override public String getType() { return "KPI"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String hql = ctx.classString("4"); // HQL_QUERY
        if (hql == null) return new JSONObject().put("value", JSONObject.NULL);

        Query<Object> q = OBDal.getInstance().getSession().createQuery(hql, Object.class);
        for (Map.Entry<String, Object> entry : ctx.getParams().entrySet()) {
            try { q.setParameter(entry.getKey(), entry.getValue()); } catch (Exception ignored) {}
        }
        Object value = q.uniqueResult();

        return new JSONObject()
                .put("value",     value)
                .put("unit",      ctx.param("unit"))
                .put("label",     ctx.param("label"))
                .put("trend",     ctx.param("trend"))
                .put("chartType", ctx.param("chartType") != null ? ctx.param("chartType") : "number");
    }
}
