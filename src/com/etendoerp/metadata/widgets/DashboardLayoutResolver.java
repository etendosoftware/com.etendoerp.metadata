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

package com.etendoerp.metadata.widgets;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final int IDX_COL        = 4;
    private static final int IDX_ROW        = 5;
    private static final int IDX_WIDTH      = 6;
    private static final int IDX_HEIGHT     = 7;
    private static final int IDX_VISIBLE    = 8;
    private static final int IDX_SEQNO      = 9;
    private static final int IDX_PARAMS     = 10;

    private static final String HQL =
        "select dw.id, dw.widgetClass.id, dw.layer, dw.user.id, " +
        "dw.columnPosition, dw.rowPosition, dw.width, dw.height, dw.visible, " +
        "dw.sequence, dw.parametersJSON " +
        "from etmeta_Dashboard_Widget dw " +
        "where dw.active = true " +
        "  and (dw.layer = 'SYSTEM' " +
        "       or (dw.layer = 'CLIENT' and dw.client.id = :clientId) " +
        "       or (dw.layer = 'USER'   and dw.user.id   = :userId and dw.role.id = :roleId)) " +
        "order by dw.layer, dw.sequence";

    /**
     * Resolves the dashboard widget layout for the current user applying layer inheritance.
     *
     * @return a JSONArray of resolved widget instances
     * @throws JSONException if JSON construction fails
     */
    public JSONArray resolve() throws JSONException {
        OBContext ctx = OBContext.getOBContext();
        String userId   = ctx.getUser().getId();
        String clientId = ctx.getCurrentClient().getId();
        String roleId   = ctx.getRole().getId();

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("clientId", clientId);
        q.setParameter("userId",   userId);
        q.setParameter("roleId",   roleId);
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
            if (!Boolean.TRUE.equals(row[IDX_VISIBLE])) continue;
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
