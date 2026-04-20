package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;
import java.util.List;

/**
 * Returns recently viewed entities using AD_ChangeLog navigation log.
 */
public class RecentlyViewedResolver implements WidgetDataResolver {
    @Override public String getType() { return "RECENTLY_VIEWED"; }

    private static final String HQL =
        "select cl.table.name, cl.recordId, cl.updated " +
        "from AD_ChangeLog cl " +
        "where cl.updatedBy.id = :userId and cl.isactive = 'Y' " +
        "order by cl.updated desc";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String userId = ctx.getObContext().getUser().getId();
        int limit = parseIntParam(ctx.param("rowsNumber"), 10);

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("userId", userId);
        q.setMaxResults(limit);

        JSONArray items = new JSONArray();
        for (Object[] row : q.list()) {
            items.put(new JSONObject()
                    .put("type",     row[0])
                    .put("recordId", row[1])
                    .put("label",    row[0])
                    .put("time",     row[2] != null ? row[2].toString() : null));
        }
        return new JSONObject().put("items", items);
    }

    private int parseIntParam(String val, int def) {
        try { return val != null ? Integer.parseInt(val) : def; } catch (Exception e) { return def; }
    }
}
