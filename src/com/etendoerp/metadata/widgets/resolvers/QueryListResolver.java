package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

import java.util.List;

/**
 * Executes a multi-row HQL_QUERY. Column names are declared via the "columns" param
 * as a comma-separated list (e.g. "order,org,total,deliveryDate").
 */
public class QueryListResolver implements WidgetDataResolver {
    @Override public String getType() { return "QUERY_LIST"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String hql = ctx.classString("4"); // HQL_QUERY
        if (hql == null) return new JSONObject().put("rows", new JSONArray()).put("totalRows", 0);

        String columnsCsv = ctx.param("columns");
        String[] colNames = columnsCsv != null ? columnsCsv.split(",") : new String[0];

        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(hql, Object[].class);
        for (var param : q.getParameters()) {
            String name = param.getName();
            if (name != null) {
                q.setParameter(name, ctx.getParams().get(name));
            }
        }

        String rowsParam = ctx.param("rowsNumber");
        if (rowsParam != null) q.setMaxResults(Integer.parseInt(rowsParam));

        List<Object[]> rawRows = q.list();
        JSONArray rows = new JSONArray();
        for (Object[] raw : rawRows) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < raw.length; i++) {
                String col = i < colNames.length ? colNames[i].trim() : "col" + i;
                row.put(col, raw[i]);
            }
            rows.put(row);
        }

        JSONArray colDefs = new JSONArray();
        for (String col : colNames) {
            colDefs.put(new JSONObject().put("name", col.trim()).put("label", col.trim()));
        }

        return new JSONObject()
                .put("columns",   colDefs)
                .put("rows",      rows)
                .put("totalRows", rawRows.size());
    }
}
