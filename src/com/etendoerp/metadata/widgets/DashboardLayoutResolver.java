package com.etendoerp.metadata.widgets;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import java.util.*;

/**
 * Queries ETMETA_DASHBOARD_WIDGET and applies SYSTEM → CLIENT → USER layer inheritance.
 * Returns a JSONArray where each entry is one resolved widget instance for the current user.
 *
 * Layer resolution rules (from spec):
 * 1. Load SYSTEM + CLIENT (matching clientId) + USER (matching userId) rows.
 * 2. Group by ETMETA_WIDGET_CLASS_ID. USER beats CLIENT beats SYSTEM.
 * 3. A SYSTEM record with no override is used as-is.
 * 4. Rows with ISVISIBLE='N' are excluded from output.
 */
public class DashboardLayoutResolver {

    // Column positions in HQL result tuple
    private static final int IDX_ID         = 0;
    private static final int IDX_CLASS_ID   = 1;
    private static final int IDX_LAYER      = 2;
    private static final int IDX_USER_ID    = 3;
    private static final int IDX_COL        = 4;
    private static final int IDX_ROW        = 5;
    private static final int IDX_WIDTH      = 6;
    private static final int IDX_HEIGHT     = 7;
    private static final int IDX_VISIBLE    = 8;
    private static final int IDX_SEQNO      = 9;
    private static final int IDX_PARAMS     = 10;

    private static final String HQL =
        "select dw.id, dw.etmetaWidgetClassId, dw.layer, dw.adUserId, " +
        "dw.colPosition, dw.rowPosition, dw.width, dw.height, dw.isvisible, " +
        "dw.seqno, dw.parametersJson " +
        "from EtmetaDashboardWidget dw " +
        "where dw.isactive = 'Y' " +
        "  and (dw.layer = 'SYSTEM' " +
        "       or (dw.layer = 'CLIENT' and dw.adClientId = :clientId) " +
        "       or (dw.layer = 'USER'   and dw.adUserId   = :userId)) " +
        "order by dw.layer, dw.seqno";

    public JSONArray resolve() throws JSONException {
        OBContext ctx = OBContext.getOBContext();
        String userId   = ctx.getUser().getId();
        String clientId = ctx.getCurrentClient().getId();

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("clientId", clientId);
        q.setParameter("userId",   userId);
        List<Object[]> rows = q.list();

        // Layer priority: USER(3) > CLIENT(2) > SYSTEM(1)
        Map<String, Object[]> best = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String classId = (String) row[IDX_CLASS_ID];
            int    priority = layerPriority((String) row[IDX_LAYER]);
            Object[] existing = best.get(classId);
            if (existing == null || priority > layerPriority((String) existing[IDX_LAYER])) {
                best.put(classId, row);
            }
        }

        JSONArray result = new JSONArray();
        for (Object[] row : best.values()) {
            if (!"Y".equals(row[IDX_VISIBLE])) continue;
            result.put(rowToJson(row));
        }
        return result;
    }

    private int layerPriority(String layer) {
        switch (layer) {
            case "USER":   return 3;
            case "CLIENT": return 2;
            default:       return 1;
        }
    }

    private JSONObject rowToJson(Object[] row) throws JSONException {
        return new JSONObject()
                .put("instanceId", row[IDX_ID])
                .put("widgetClassId", row[IDX_CLASS_ID])
                .put("layer", row[IDX_LAYER])
                .put("position", new JSONObject()
                        .put("col",    row[IDX_COL])
                        .put("row",    row[IDX_ROW])
                        .put("width",  row[IDX_WIDTH])
                        .put("height", row[IDX_HEIGHT]))
                .put("seqno", row[IDX_SEQNO])
                .put("parameters", row[IDX_PARAMS] != null ? new JSONObject((String) row[IDX_PARAMS]) : new JSONObject());
    }
}
