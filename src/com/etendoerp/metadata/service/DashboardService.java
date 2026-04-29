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

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.widgets.DashboardLayoutResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

import static com.etendoerp.metadata.utils.Constants.*;

/**
 * Handles all /meta/dashboard/* routes.
 *
 * GET    /dashboard/layout        → resolved widget layout for current user
 * PUT    /dashboard/layout        → save position/size/visibility changes
 * POST   /dashboard/widget        → add a new widget instance
 * DELETE /dashboard/widget/{id}   → remove or shadow-hide a widget instance
 */
public class DashboardService extends MetadataService {

    private static final String INSTANCE_ID = "instanceId";
    private static final String STATUS = "status";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String IS_VISIBLE = "isVisible";
    private static final String VISIBLE = "visible";
    private static final String USER_ID = "userId";
    private static final String ROLE_ID = "roleId";
    private static final String CLASS_ID = "classId";
    private static final String CLIENT_ID = "clientId";
    private static final String SEQNO = "seqno";
    private static final String PARAMS = "params";
    private static final String PARAMETERS = "parameters";
    private static final String INVALID_VALUE_FOR_PARAM = "Invalid value for parameter '";

    /**
     * Creates a new DashboardService.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public DashboardService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String method = getRequest().getMethod();
        String path   = getRequest().getPathInfo();

        try {
            OBContext.setAdminMode(true);
            if (GET.equals(method) && path.endsWith("/layout")) {
                handleGetLayout();
            } else if (PUT.equals(method) && path.endsWith("/layout")) {
                handlePutLayout();
            } else if (POST.equals(method) && path.endsWith("/widget")) {
                handlePostWidget();
            } else if (DELETE.equals(method) && path.contains("/widget/")) {
                handleDeleteWidget(extractLastSegment(path));
            } else if (PATCH.equals(method) && path.contains("/widget/") && path.endsWith("/params")) {
                handlePatchWidgetParams(extractSecondToLastSegment(path));
            } else {
                throw new NotFoundException();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleGetLayout() throws Exception {
        JSONArray widgets = new DashboardLayoutResolver().resolve();

        // Enrich each entry with widget class metadata (title, type, name, refreshInterval, available)
        com.etendoerp.metadata.widgets.WidgetResolverRegistry registry =
            com.etendoerp.metadata.widgets.WidgetResolverRegistryHolder.getInstance();
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject w = widgets.getJSONObject(i);
            enrichWithClassData(w);
            String type = w.optString("type", "");
            com.etendoerp.metadata.widgets.WidgetDataResolver resolver =
                registry != null ? registry.getResolver(type) : null;
            w.put("available", resolver == null || resolver.isAvailable());
        }

        write(new JSONObject().put("widgets", widgets));
    }

    private void enrichWithClassData(JSONObject widget) throws Exception {
        String classId = widget.getString("widgetClassId");
        String hql = "select wc.name, wc.type, wc.title, wc.refreshInterval " +
                     "from etmeta_Widget_Class wc where wc.id = :id";
        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(hql, Object[].class);
        q.setParameter("id", classId);
        Object[] row = q.uniqueResult();
        if (row != null) {
            widget.put("name",            row[0]);
            widget.put("type",            row[1]);
            widget.put("title",           row[2]);
            widget.put("refreshInterval", row[3]);
        }
    }

    private void handlePutLayout() throws Exception {
        JSONObject body = parseJsonBody();
        JSONArray widgets = body.getJSONArray("widgets");
        String userId  = OBContext.getOBContext().getUser().getId();
        String roleId  = OBContext.getOBContext().getRole().getId();
        boolean isAdmin = isDashboardAdmin();

        for (int i = 0; i < widgets.length(); i++) {
            JSONObject w = widgets.getJSONObject(i);
            String instanceId = w.getString(INSTANCE_ID);
            updateLayoutRecord(instanceId, w, userId, roleId, isAdmin);
        }
        write(new JSONObject().put(STATUS, "ok"));
    }

    private void updateLayoutRecord(String instanceId, JSONObject w,
                                    String userId, String roleId, boolean isAdmin) throws Exception {
        String updateHql =
            "update etmeta_Dashboard_Widget dw set " +
            "dw.columnPosition = :col, dw.rowPosition = :row, " +
            "dw.width = :width, dw.height = :height, dw.visible = :visible " +
            "where dw.id = :id " +
            (isAdmin ? "" : "and dw.user.id = :userId and dw.role.id = :roleId");

        Query<?> q = OBDal.getInstance().getSession().createQuery(updateHql);
        q.setParameter("col",     java.math.BigDecimal.valueOf(w.optDouble("col", 0)));
        q.setParameter("row",     java.math.BigDecimal.valueOf(w.optDouble("row", 0)));
        q.setParameter(WIDTH,   java.math.BigDecimal.valueOf(w.optDouble(WIDTH, 2)));
        q.setParameter(HEIGHT,  java.math.BigDecimal.valueOf(w.optDouble(HEIGHT, 1)));
        q.setParameter(VISIBLE, w.optBoolean(IS_VISIBLE, true));
        q.setParameter("id",      instanceId);
        if (!isAdmin) {
            q.setParameter(USER_ID, userId);
            q.setParameter(ROLE_ID, roleId);
        }
        int updated = q.executeUpdate();

        // If no rows matched, the instance is a SYSTEM/CLIENT layer record (no user/role FK).
        // Persist changes by upserting a USER-layer override for this user+role.
        if (updated == 0 && !isAdmin) {
            upsertUserLayerOverride(instanceId, w, userId, roleId);
        }

        OBDal.getInstance().getSession().flush();
    }

    private void upsertUserLayerOverride(String instanceId, JSONObject w,
                                         String userId, String roleId) throws Exception {
        // Fetch the source (SYSTEM/CLIENT) record metadata
        Query<Object[]> lookupQ = OBDal.getInstance().getSession()
            .createQuery(
                "select dw.widgetClass.id, dw.client.id, dw.sequence, dw.parametersJSON " +
                "from etmeta_Dashboard_Widget dw where dw.id = :id",
                Object[].class);
        lookupQ.setParameter("id", instanceId);
        Object[] source = lookupQ.uniqueResult();
        if (source == null) return;

        String classId  = (String) source[0];
        String clientId = (String) source[1];
        Number seqno    = (Number) source[2];
        String params   = (String) source[3];

        // Try to update an existing USER override for this classId+user+role
        int userUpdated = OBDal.getInstance().getSession()
            .createQuery(
                "update etmeta_Dashboard_Widget dw set " +
                "dw.columnPosition = :col, dw.rowPosition = :row, " +
                "dw.width = :width, dw.height = :height, dw.visible = :visible " +
                "where dw.widgetClass.id = :classId and dw.layer = 'USER' " +
                "and dw.user.id = :userId and dw.role.id = :roleId")
            .setParameter("col",     java.math.BigDecimal.valueOf(w.optDouble("col", 0)))
            .setParameter("row",     java.math.BigDecimal.valueOf(w.optDouble("row", 0)))
            .setParameter(WIDTH,   java.math.BigDecimal.valueOf(w.optDouble(WIDTH, 2)))
            .setParameter(HEIGHT,  java.math.BigDecimal.valueOf(w.optDouble(HEIGHT, 1)))
            .setParameter(VISIBLE, w.optBoolean(IS_VISIBLE, true))
            .setParameter(CLASS_ID, classId)
            .setParameter(USER_ID,  userId)
            .setParameter(ROLE_ID,  roleId)
            .executeUpdate();

        if (userUpdated > 0) return;

        // No USER override yet — insert one
        String newId = UUID.randomUUID().toString().replace("-", "");
        String isVisible = w.optBoolean(IS_VISIBLE, true) ? "Y" : "N";
        String insertSql =
            "INSERT INTO etmeta_dashboard_widget " +
            "(etmeta_dashboard_widget_id, etmeta_widget_class_id, layer, ad_client_id, ad_org_id, " +
            " ad_user_id, ad_role_id, col_position, row_position, width, height, isvisible, seqno, " +
            " parameters_json, isactive, created, createdby, updated, updatedby) " +
            "VALUES (:id, :classId, 'USER', :clientId, '0', " +
            " :userId, :roleId, :col, :row, :width, :height, :visible, :seqno, " +
            " :params, 'Y', NOW(), :uid, NOW(), :uid)";

        org.hibernate.query.NativeQuery<?> ins =
            OBDal.getInstance().getSession().createNativeQuery(insertSql);
        ins.setParameter("id",       newId);
        ins.setParameter(CLASS_ID,  classId);
        ins.setParameter(CLIENT_ID, clientId);
        ins.setParameter(USER_ID,   userId);
        ins.setParameter(ROLE_ID,   roleId);
        ins.setParameter("col",      w.optDouble("col", 0));
        ins.setParameter("row",      w.optDouble("row", 0));
        ins.setParameter(WIDTH,    w.optDouble(WIDTH, 2));
        ins.setParameter(HEIGHT,   w.optDouble(HEIGHT, 1));
        ins.setParameter(VISIBLE,  isVisible);
        ins.setParameter(SEQNO,    seqno != null ? seqno.intValue() : 10);
        ins.setParameter(PARAMS,   params);
        ins.setParameter("uid",      userId);
        ins.executeUpdate();
    }

    private void handlePostWidget() throws Exception {
        JSONObject body   = parseJsonBody();
        String classId    = body.getString("widgetClassId");
        String layer      = isDashboardAdmin() ? "CLIENT" : "USER";
        String userId     = OBContext.getOBContext().getUser().getId();
        String roleId     = OBContext.getOBContext().getRole().getId();
        String clientId   = OBContext.getOBContext().getCurrentClient().getId();

        // Remove USER shadow record (isvisible=N) so the new widget is not hidden
        OBDal.getInstance().getSession()
            .createQuery("delete from etmeta_Dashboard_Widget dw " +
                         "where dw.widgetClass.id = :classId " +
                         "and dw.layer = 'USER' and dw.user.id = :userId and dw.role.id = :roleId " +
                         "and dw.visible = false")
            .setParameter(CLASS_ID, classId)
            .setParameter(USER_ID,  userId)
            .setParameter(ROLE_ID,  roleId)
            .executeUpdate();

        // Skip insert if an active record already exists for this classId and layer
        Query<Long> existsQ = OBDal.getInstance().getSession()
            .createQuery("select count(dw) from etmeta_Dashboard_Widget dw " +
                         "where dw.widgetClass.id = :classId and dw.layer = :layer " +
                         "and dw.active = true " +
                         (layer.equals("USER") ? "and dw.user.id = :userId and dw.role.id = :roleId "
                                               : "and dw.client.id = :clientId "),
                         Long.class);
        existsQ.setParameter(CLASS_ID, classId);
        existsQ.setParameter("layer",   layer);
        if (layer.equals("USER")) {
            existsQ.setParameter(USER_ID, userId);
            existsQ.setParameter(ROLE_ID, roleId);
        } else {
            existsQ.setParameter(CLIENT_ID, clientId);
        }
        Long existing = existsQ.uniqueResult();
        if (existing != null && existing > 0) {
            write(new JSONObject().put(INSTANCE_ID, "").put(STATUS, "exists"));
            return;
        }

        String newId      = UUID.randomUUID().toString().replace("-", "");

        String insertSql =
            "INSERT INTO etmeta_dashboard_widget " +
            "(etmeta_dashboard_widget_id, etmeta_widget_class_id, layer, ad_client_id, ad_org_id, " +
            " ad_user_id, ad_role_id, col_position, row_position, width, height, isvisible, seqno, " +
            " parameters_json, isactive, created, createdby, updated, updatedby) " +
            "VALUES (:id, :classId, :layer, :clientId, '0', " +
            " :userId, :roleId, :col, :row, :width, :height, 'Y', :seqno, " +
            " :params, 'Y', NOW(), :uid, NOW(), :uid)";

        org.hibernate.query.NativeQuery<?> q = OBDal.getInstance().getSession().createNativeQuery(insertSql);
        q.setParameter("id",       newId);
        q.setParameter(CLASS_ID,  classId);
        q.setParameter("layer",    layer);
        q.setParameter(CLIENT_ID, clientId);
        q.setParameter(USER_ID,   "USER".equals(layer) ? userId : null);
        q.setParameter(ROLE_ID,   "USER".equals(layer) ? roleId : null);
        q.setParameter("col",      body.optInt("col", 0));
        q.setParameter("row",      body.optInt("row", 0));
        q.setParameter(WIDTH,    body.optInt(WIDTH, 2));
        q.setParameter(HEIGHT,   body.optInt(HEIGHT, 1));
        q.setParameter(SEQNO,    10);
        if (body.has(PARAMETERS)) {
            validateParams(body.getJSONObject(PARAMETERS));
        }
        q.setParameter(PARAMS,   body.has(PARAMETERS) ? body.getJSONObject(PARAMETERS).toString() : null);
        q.setParameter("uid",      userId);
        q.executeUpdate();
        OBDal.getInstance().getSession().flush();

        write(new JSONObject().put(INSTANCE_ID, newId).put(STATUS, "created"));
    }

    private void handlePatchWidgetParams(String instanceId) throws Exception {
        JSONObject body   = parseJsonBody();
        JSONObject params = body.getJSONObject(PARAMETERS);
        validateParams(params);
        String userId     = OBContext.getOBContext().getUser().getId();
        String roleId     = OBContext.getOBContext().getRole().getId();

        // Try updating directly if this is a USER-layer record owned by this user
        int updated = OBDal.getInstance().getSession()
            .createQuery("update etmeta_Dashboard_Widget dw set dw.parametersJSON = :params " +
                         "where dw.id = :id and dw.layer = 'USER' " +
                         "and dw.user.id = :userId and dw.role.id = :roleId")
            .setParameter(PARAMS,  params.toString())
            .setParameter("id",      instanceId)
            .setParameter(USER_ID,  userId)
            .setParameter(ROLE_ID,  roleId)
            .executeUpdate();

        if (updated == 0) {
            // SYSTEM/CLIENT instance — upsert a USER-layer override carrying the new params
            upsertUserLayerOverrideWithParams(instanceId, params.toString(), userId, roleId);
        }

        OBDal.getInstance().getSession().flush();
        write(new JSONObject().put(STATUS, "ok"));
    }

    private void upsertUserLayerOverrideWithParams(String instanceId, String newParams,
                                                   String userId, String roleId) throws Exception {
        Query<Object[]> lookupQ = OBDal.getInstance().getSession()
            .createQuery(
                "select dw.widgetClass.id, dw.client.id, dw.sequence, " +
                "dw.columnPosition, dw.rowPosition, dw.width, dw.height " +
                "from etmeta_Dashboard_Widget dw where dw.id = :id",
                Object[].class);
        lookupQ.setParameter("id", instanceId);
        Object[] source = lookupQ.uniqueResult();
        if (source == null) throw new NotFoundException();

        String classId  = (String) source[0];
        String clientId = (String) source[1];
        Number seqno    = (Number) source[2];
        Number col      = (Number) source[3];
        Number row      = (Number) source[4];
        Number width    = (Number) source[5];
        Number height   = (Number) source[6];

        // Try updating existing USER override for this classId+user+role
        int userUpdated = OBDal.getInstance().getSession()
            .createQuery("update etmeta_Dashboard_Widget dw set dw.parametersJSON = :params " +
                         "where dw.widgetClass.id = :classId and dw.layer = 'USER' " +
                         "and dw.user.id = :userId and dw.role.id = :roleId")
            .setParameter(PARAMS,  newParams)
            .setParameter(CLASS_ID, classId)
            .setParameter(USER_ID,  userId)
            .setParameter(ROLE_ID,  roleId)
            .executeUpdate();

        if (userUpdated > 0) return;

        // No USER override yet — insert one
        String newId     = UUID.randomUUID().toString().replace("-", "");
        String insertSql =
            "INSERT INTO etmeta_dashboard_widget " +
            "(etmeta_dashboard_widget_id, etmeta_widget_class_id, layer, ad_client_id, ad_org_id, " +
            " ad_user_id, ad_role_id, col_position, row_position, width, height, isvisible, seqno, " +
            " parameters_json, isactive, created, createdby, updated, updatedby) " +
            "VALUES (:id, :classId, 'USER', :clientId, '0', " +
            " :userId, :roleId, :col, :row, :width, :height, 'Y', :seqno, " +
            " :params, 'Y', NOW(), :uid, NOW(), :uid)";
        OBDal.getInstance().getSession().createNativeQuery(insertSql)
            .setParameter("id",       newId)
            .setParameter(CLASS_ID,  classId)
            .setParameter(CLIENT_ID, clientId)
            .setParameter(USER_ID,   userId)
            .setParameter(ROLE_ID,   roleId)
            .setParameter("col",      col != null ? col.intValue() : 0)
            .setParameter("row",      row != null ? row.intValue() : 0)
            .setParameter(WIDTH,    width != null ? width.intValue() : 2)
            .setParameter(HEIGHT,   height != null ? height.intValue() : 1)
            .setParameter(SEQNO,    seqno != null ? seqno.intValue() : 10)
            .setParameter(PARAMS,   newParams)
            .setParameter("uid",      userId)
            .executeUpdate();
    }

    private void handleDeleteWidget(String instanceId) throws Exception {
        String layer = getInstanceLayer(instanceId);
        if (layer == null) throw new NotFoundException();

        if ("USER".equals(layer)) {
            Query<?> q = OBDal.getInstance().getSession()
                    .createQuery("delete from etmeta_Dashboard_Widget dw where dw.id = :id");
            q.setParameter("id", instanceId);
            q.executeUpdate();
        } else {
            // Insert USER shadow record with ISVISIBLE=N to hide SYSTEM/CLIENT widget for this user+role
            String classId  = getInstanceClassId(instanceId);
            String userId   = OBContext.getOBContext().getUser().getId();
            String roleId   = OBContext.getOBContext().getRole().getId();
            String clientId = OBContext.getOBContext().getCurrentClient().getId();
            String shadowId = UUID.randomUUID().toString().replace("-", "");

            String insertSql =
                "INSERT INTO etmeta_dashboard_widget " +
                "(etmeta_dashboard_widget_id, etmeta_widget_class_id, layer, ad_client_id, ad_org_id, " +
                " ad_user_id, ad_role_id, col_position, row_position, width, height, isvisible, seqno, " +
                " isactive, created, createdby, updated, updatedby) " +
                "VALUES (:id, :classId, 'USER', :clientId, '0', " +
                " :userId, :roleId, 0, 0, 0, 0, 'N', 0, 'Y', NOW(), :uid, NOW(), :uid)";
            org.hibernate.query.NativeQuery<?> q = OBDal.getInstance().getSession().createNativeQuery(insertSql);
            q.setParameter("id",       shadowId);
            q.setParameter(CLASS_ID,  classId);
            q.setParameter(CLIENT_ID, clientId);
            q.setParameter(USER_ID,   userId);
            q.setParameter(ROLE_ID,   roleId);
            q.setParameter("uid",      userId);
            q.executeUpdate();
        }
        write(new JSONObject().put(STATUS, "deleted"));
    }

    private String getInstanceLayer(String instanceId) {
        Query<String> q = OBDal.getInstance().getSession()
                .createQuery("select dw.layer from etmeta_Dashboard_Widget dw where dw.id = :id", String.class);
        q.setParameter("id", instanceId);
        return q.uniqueResult();
    }

    private String getInstanceClassId(String instanceId) {
        Query<String> q = OBDal.getInstance().getSession()
                .createQuery("select dw.widgetClass.id from etmeta_Dashboard_Widget dw where dw.id = :id", String.class);
        q.setParameter("id", instanceId);
        return q.uniqueResult();
    }

    private boolean isDashboardAdmin() {
        return OBContext.getOBContext().getRole().isClientAdmin();
    }

    private JSONObject parseJsonBody() throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        java.io.BufferedReader reader = getRequest().getReader();
        while ((line = reader.readLine()) != null) sb.append(line);
        return new JSONObject(sb.toString());
    }

    private String extractLastSegment(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Validates widget parameters before persisting.
     * Any value that looks like a URL must use https:// to prevent javascript: and data: injection.
     * The host is parsed via {@link java.net.URI} to block bypass patterns such as
     * {@code https://evil.com\@google.com}.
     */
    private void validateParams(JSONObject params) throws Exception {
        java.util.Iterator<String> keys = params.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = params.get(key);
            if (!(val instanceof String)) continue;
            String strVal = ((String) val).trim();
            if (strVal.isEmpty()) continue;
            // Detect URL-like values: anything containing ":" that isn't a plain word
            if (!strVal.contains(":")) continue;
            if (!strVal.startsWith("https://")) {
                throw new com.etendoerp.metadata.exceptions.UnprocessableContentException(
                    INVALID_VALUE_FOR_PARAM + key + "': only https:// URLs are allowed");
            }
            // Validate the host to block bypass patterns like https://evil.com\@google.com
            try {
                java.net.URI uri = new java.net.URI(strVal);
                String host = uri.getHost();
                if (host == null || host.contains("@") || host.contains("\\")) {
                    throw new com.etendoerp.metadata.exceptions.UnprocessableContentException(
                        INVALID_VALUE_FOR_PARAM + key + "': malformed URL host");
                }
            } catch (java.net.URISyntaxException e) {
                throw new com.etendoerp.metadata.exceptions.UnprocessableContentException(
                    INVALID_VALUE_FOR_PARAM + key + "': malformed URL");
            }
        }
    }

    private String extractSecondToLastSegment(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 2];
    }
}
