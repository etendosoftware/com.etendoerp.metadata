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
            OBDal.getInstance().getSession()
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

    private static final String PARAM_USER_ID = "userId";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        try {
            return fetchNotifications(ctx);
        } catch (Exception e) {
            // AN_Note may not be mapped if the notifications module is not installed
            return emptyResult();
        }
    }

    private JSONObject fetchNotifications(WidgetDataContext ctx) throws Exception {
        String userId = ctx.getObContext().getUser().getId();
        int limit = parseIntParam(ctx.param("rowsNumber"), 10);

        List<Object[]> rows = executeItemsQuery(userId, limit);
        Long total = executeCountQuery(userId);

        JSONArray items = buildItemsArray(rows);
        return new JSONObject().put("items", items).put("totalCount", total != null ? total : 0);
    }

    private List<Object[]> executeItemsQuery(String userId, int limit) {
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(ITEMS_HQL, Object[].class);
        q.setParameter(PARAM_USER_ID, userId);
        q.setMaxResults(limit);
        return q.list();
    }

    private Long executeCountQuery(String userId) {
        Query<Long> countQ = OBDal.getInstance().getSession()
                .createQuery(COUNT_HQL, Long.class);
        countQ.setParameter(PARAM_USER_ID, userId);
        return countQ.uniqueResult();
    }

    private JSONArray buildItemsArray(List<Object[]> rows) throws Exception {
        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                    .put("text",     row[0])
                    .put("priority", mapPriority(row[1]))
                    .put("time",     row[2] != null ? row[2].toString() : null));
        }
        return items;
    }

    private JSONObject emptyResult() throws Exception {
        return new JSONObject().put("items", new JSONArray()).put("totalCount", 0);
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
