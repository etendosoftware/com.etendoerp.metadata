package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

import java.util.List;

/**
 * Queries AD_Note for the current user's notifications.
 * Priority mapping: 0 = normal, 1 = high, 2 = success.
 */
public class NotificationResolver implements WidgetDataResolver {
    @Override public String getType() { return "NOTIFICATION"; }

    @Override
    public boolean isAvailable() {
        try {
            org.openbravo.dal.service.OBDal.getInstance().getSession()
                .createQuery("select 1 from AN_Note n where 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String ITEMS_HQL =
        "select n.note, n.priority, n.creationDate " +
        "from AN_Note n where n.userContact.id = :userId " +
        "and n.isactive = 'Y' order by n.creationDate desc";

    private static final String COUNT_HQL =
        "select count(n) from AN_Note n where n.userContact.id = :userId and n.isactive = 'Y'";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        try {
        String userId = ctx.getObContext().getUser().getId();
        int limit = parseIntParam(ctx.param("rowsNumber"), 10);

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(ITEMS_HQL, Object[].class);
        q.setParameter("userId", userId);
        q.setMaxResults(limit);
        List<Object[]> rows = q.list();

        Query<Long> countQ = OBDal.getInstance().getSession()
                .createQuery(COUNT_HQL, Long.class);
        countQ.setParameter("userId", userId);
        Long total = countQ.uniqueResult();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                    .put("text",     row[0])
                    .put("priority", mapPriority(row[1]))
                    .put("time",     row[2] != null ? row[2].toString() : null));
        }
        return new JSONObject().put("items", items).put("totalCount", total != null ? total : 0);
        } catch (Exception e) {
            // AN_Note may not be mapped if the notifications module is not installed
            return new JSONObject().put("items", new JSONArray()).put("totalCount", 0);
        }
    }

    private String mapPriority(Object raw) {
        if (raw == null) return "normal";
        String s = raw.toString();
        if ("1".equals(s) || "high".equalsIgnoreCase(s)) return "high";
        if ("2".equals(s) || "success".equalsIgnoreCase(s)) return "success";
        return "normal";
    }

    private int parseIntParam(String val, int def) {
        try { return val != null ? Integer.parseInt(val) : def; } catch (Exception e) { return def; }
    }
}
