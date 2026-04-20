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

        // Enrich each entry with widget class metadata (title, type, name, refreshInterval)
        for (int i = 0; i < widgets.length(); i++) {
            JSONObject w = widgets.getJSONObject(i);
            enrichWithClassData(w);
        }

        write(new JSONObject().put("widgets", widgets));
    }

    private void enrichWithClassData(JSONObject widget) throws Exception {
        String classId = widget.getString("widgetClassId");
        String hql = "select wc.name, wc.type, wc.title, wc.refreshInterval " +
                     "from EtmetaWidgetClass wc where wc.id = :id";
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
        String userId = OBContext.getOBContext().getUser().getId();
        boolean isAdmin = isDashboardAdmin();

        for (int i = 0; i < widgets.length(); i++) {
            JSONObject w = widgets.getJSONObject(i);
            String instanceId = w.getString("instanceId");
            updateLayoutRecord(instanceId, w, userId, isAdmin);
        }
        write(new JSONObject().put("status", "ok"));
    }

    private void updateLayoutRecord(String instanceId, JSONObject w,
                                    String userId, boolean isAdmin) throws Exception {
        String updateHql =
            "update EtmetaDashboardWidget dw set " +
            "dw.colPosition = :col, dw.rowPosition = :row, " +
            "dw.width = :width, dw.height = :height, dw.isvisible = :visible " +
            "where dw.id = :id " +
            (isAdmin ? "" : "and dw.adUserId = :userId");

        Query<?> q = OBDal.getInstance().getSession().createQuery(updateHql);
        q.setParameter("col",     w.optInt("col", 0));
        q.setParameter("row",     w.optInt("row", 0));
        q.setParameter("width",   w.optInt("width", 2));
        q.setParameter("height",  w.optInt("height", 1));
        q.setParameter("visible", w.optBoolean("isVisible", true) ? "Y" : "N");
        q.setParameter("id",      instanceId);
        if (!isAdmin) q.setParameter("userId", userId);
        q.executeUpdate();
    }

    private void handlePostWidget() throws Exception {
        JSONObject body   = parseJsonBody();
        String classId    = body.getString("widgetClassId");
        String layer      = isDashboardAdmin() ? "CLIENT" : "USER";
        String userId     = OBContext.getOBContext().getUser().getId();
        String clientId   = OBContext.getOBContext().getCurrentClient().getId();
        String newId      = UUID.randomUUID().toString().replace("-", "");

        String insertHql =
            "insert into EtmetaDashboardWidget " +
            "(id, etmetaWidgetClassId, layer, adClientId, adUserId, " +
            " colPosition, rowPosition, width, height, isvisible, seqno, parametersJson, isactive) " +
            "values (:id, :classId, :layer, :clientId, :userId, " +
            " :col, :row, :width, :height, 'Y', :seqno, :params, 'Y')";

        Query<?> q = OBDal.getInstance().getSession().createQuery(insertHql);
        q.setParameter("id",       newId);
        q.setParameter("classId",  classId);
        q.setParameter("layer",    layer);
        q.setParameter("clientId", clientId);
        q.setParameter("userId",   "USER".equals(layer) ? userId : null);
        q.setParameter("col",      body.optInt("col", 0));
        q.setParameter("row",      body.optInt("row", 0));
        q.setParameter("width",    body.optInt("width", 2));
        q.setParameter("height",   body.optInt("height", 1));
        q.setParameter("seqno",    10);
        q.setParameter("params",   body.has("parameters") ? body.getJSONObject("parameters").toString() : null);
        q.executeUpdate();

        write(new JSONObject().put("instanceId", newId).put("status", "created"));
    }

    private void handleDeleteWidget(String instanceId) throws Exception {
        String layer = getInstanceLayer(instanceId);
        if (layer == null) throw new NotFoundException();

        if ("USER".equals(layer)) {
            Query<?> q = OBDal.getInstance().getSession()
                    .createQuery("delete from EtmetaDashboardWidget dw where dw.id = :id");
            q.setParameter("id", instanceId);
            q.executeUpdate();
        } else {
            // Insert USER shadow record with ISVISIBLE=N to hide SYSTEM/CLIENT widget for this user
            String classId  = getInstanceClassId(instanceId);
            String userId   = OBContext.getOBContext().getUser().getId();
            String clientId = OBContext.getOBContext().getCurrentClient().getId();
            String shadowId = UUID.randomUUID().toString().replace("-", "");

            String insertHql =
                "insert into EtmetaDashboardWidget " +
                "(id, etmetaWidgetClassId, layer, adClientId, adUserId, " +
                " colPosition, rowPosition, width, height, isvisible, seqno, isactive) " +
                "values (:id, :classId, 'USER', :clientId, :userId, 0, 0, 0, 0, 'N', 0, 'Y')";
            Query<?> q = OBDal.getInstance().getSession().createQuery(insertHql);
            q.setParameter("id",       shadowId);
            q.setParameter("classId",  classId);
            q.setParameter("clientId", clientId);
            q.setParameter("userId",   userId);
            q.executeUpdate();
        }
        write(new JSONObject().put("status", "deleted"));
    }

    private String getInstanceLayer(String instanceId) {
        Query<String> q = OBDal.getInstance().getSession()
                .createQuery("select dw.layer from EtmetaDashboardWidget dw where dw.id = :id", String.class);
        q.setParameter("id", instanceId);
        return q.uniqueResult();
    }

    private String getInstanceClassId(String instanceId) {
        Query<String> q = OBDal.getInstance().getSession()
                .createQuery("select dw.etmetaWidgetClassId from EtmetaDashboardWidget dw where dw.id = :id", String.class);
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
}
