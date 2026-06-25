/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
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

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import static com.etendoerp.metadata.utils.Constants.SAVED_VIEW_PATH;

/** Service for managing Saved Views. */
public class SavedViewService extends MetadataService {

    private static final String FIELD_ISDEFAULT = "isdefault";
    private static final String FIELD_FILTERCLAUSE = "filterclause";
    private static final String FIELD_GRIDCONFIGURATION = "gridconfiguration";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_RESPONSE = "response";
    private static final String SAVED_VIEW_TABLE = "ETMETA_SAVEDVIEW";

    public SavedViewService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        OBContext.setAdminMode(true);
        try {
            switch (getRequest().getMethod()) {
                case "GET":
                    handleGet();
                    break;
                case "POST":
                    handlePost();
                    break;
                case "PUT":
                    handlePut();
                    break;
                case "DELETE":
                    handleDelete();
                    break;
                default:
                    throw new MethodNotAllowedException();
            }
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleGet() throws Exception {
        String id = extractId();
        if (id != null) {
            BaseOBObject view = getSavedView(id);
            if (view == null) {
                throw new NotFoundException();
            }
            write(wrapSingle(toJSON(view)));
            return;
        }

        Entity entity = entity();
        StringBuilder hql = new StringBuilder("select v from ")
            .append(entity.getName())
            .append(" v where v.")
            .append(prop("AD_USER_ID"))
            .append(".id = :currentUserId");

        String tabId = getRequest().getParameter("tab");
        String isdefaultStr = getRequest().getParameter(FIELD_ISDEFAULT);
        if (tabId != null && !tabId.isEmpty()) {
            hql.append(" and v.").append(prop("AD_TAB_ID")).append(".id = :tabId");
        }
        if (isdefaultStr != null && !isdefaultStr.isEmpty()) {
            hql.append(" and v.").append(prop("ISDEFAULT")).append(" = :isdefault");
        }

        Query<BaseOBObject> query = OBDal.getInstance().getSession()
            .createQuery(hql.toString(), BaseOBObject.class)
            .setParameter("currentUserId", OBContext.getOBContext().getUser().getId());
        if (tabId != null && !tabId.isEmpty()) {
            query.setParameter("tabId", tabId);
        }
        if (isdefaultStr != null && !isdefaultStr.isEmpty()) {
            query.setParameter("isdefault", Boolean.parseBoolean(isdefaultStr));
        }

        JSONArray data = new JSONArray();
        List<BaseOBObject> list = query.list();
        for (BaseOBObject view : list) {
            data.put(toJSON(view));
        }
        write(wrapList(data));
    }

    private void handlePost() throws Exception {
        JSONObject body = readBody();
        OBContext ctx = OBContext.getOBContext();

        BaseOBObject view = newSavedView();
        view.set(prop("AD_CLIENT_ID"), OBDal.getInstance().get(Client.class, ctx.getCurrentClient().getId()));
        view.set(prop("AD_ORG_ID"), OBDal.getInstance().get(Organization.class, ctx.getCurrentOrganization().getId()));
        view.set(prop("AD_USER_ID"), OBDal.getInstance().get(User.class, ctx.getUser().getId()));
        applyBody(view, body);

        OBDal.getInstance().save(view);
        OBDal.getInstance().flush();
        write(wrapSingle(toJSON(view)));
    }

    private void handlePut() throws Exception {
        String id = extractId();
        if (id == null) {
            throw new NotFoundException();
        }

        BaseOBObject view = getSavedView(id);
        if (view == null) {
            throw new NotFoundException();
        }

        applyBody(view, readBody());
        OBDal.getInstance().save(view);
        OBDal.getInstance().flush();
        write(wrapSingle(toJSON(view)));
    }

    private void handleDelete() throws Exception {
        String id = extractId();
        if (id == null) {
            throw new NotFoundException();
        }

        BaseOBObject view = getSavedView(id);
        if (view == null) {
            throw new NotFoundException();
        }

        OBDal.getInstance().remove(view);
        OBDal.getInstance().flush();
        write(new JSONObject().put(FIELD_RESPONSE, new JSONObject().put(FIELD_STATUS, 0)));
    }

    private void applyBody(BaseOBObject view, JSONObject body) throws Exception {
        if (body.has("name")) {
            view.set(prop("NAME"), body.getString("name"));
        }
        if (body.has("tab")) {
            view.set(prop("AD_TAB_ID"), OBDal.getInstance().get(Tab.class, body.getString("tab")));
        }
        if (body.has(FIELD_ISDEFAULT)) {
            view.set(prop("ISDEFAULT"), body.getBoolean(FIELD_ISDEFAULT));
        }
        if (body.has(FIELD_FILTERCLAUSE)) {
            String val = body.optString(FIELD_FILTERCLAUSE, null);
            view.set(prop("FILTERCLAUSE"), "null".equals(val) ? null : val);
        }
        if (body.has(FIELD_GRIDCONFIGURATION)) {
            String val = body.optString(FIELD_GRIDCONFIGURATION, null);
            view.set(prop("GRIDCONFIGURATION"), "null".equals(val) ? null : val);
        }
    }

    private JSONObject toJSON(BaseOBObject view) throws Exception {
        BaseOBObject tab = (BaseOBObject) view.get(prop("AD_TAB_ID"));
        BaseOBObject user = (BaseOBObject) view.get(prop("AD_USER_ID"));
        JSONObject json = new JSONObject()
            .put("id", view.getId())
            .put("name", view.get(prop("NAME")))
            .put("tab", tab != null ? tab.getId() : JSONObject.NULL)
            .put("user", user != null ? user.getId() : JSONObject.NULL)
            .put(FIELD_ISDEFAULT, view.get(prop("ISDEFAULT")))
            .put("active", view.get(prop("ISACTIVE")));

        Object filter = view.get(prop("FILTERCLAUSE"));
        Object grid = view.get(prop("GRIDCONFIGURATION"));
        json.put(FIELD_FILTERCLAUSE, filter != null ? filter : JSONObject.NULL);
        json.put(FIELD_GRIDCONFIGURATION, grid != null ? grid : JSONObject.NULL);
        return json;
    }

    private JSONObject wrapSingle(JSONObject data) throws Exception {
        return new JSONObject().put(FIELD_RESPONSE,
            new JSONObject().put(FIELD_STATUS, 0).put("data", data));
    }

    private JSONObject wrapList(JSONArray data) throws Exception {
        return new JSONObject().put(FIELD_RESPONSE,
            new JSONObject()
                .put(FIELD_STATUS, 0)
                .put("data", data)
                .put("startRow", 0)
                .put("endRow", data.length() - 1)
                .put("totalRows", data.length()));
    }

    private String extractId() {
        String pathInfo = getRequest().getPathInfo();
        if (pathInfo == null) {
            return null;
        }
        String normalized = pathInfo
            .replace("/com.etendoerp.metadata.meta", "")
            .replace("/com.etendoerp.metadata.sws", "");
        String remainder = normalized.startsWith(SAVED_VIEW_PATH)
            ? normalized.substring(SAVED_VIEW_PATH.length())
            : normalized;
        if (remainder.isEmpty() || remainder.equals("/")) {
            return null;
        }
        return remainder.startsWith("/") ? remainder.substring(1) : remainder;
    }

    private JSONObject readBody() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = getRequest().getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        try {
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            throw new IOException("Invalid JSON body: " + e.getMessage(), e);
        }
    }

    private BaseOBObject getSavedView(String id) {
        return OBDal.getInstance().get(entity().getName(), id);
    }

    @SuppressWarnings("unchecked")
    private BaseOBObject newSavedView() {
        return (BaseOBObject) OBProvider.getInstance().get((Class<BaseOBObject>) entity().getMappingClass());
    }

    private String prop(String columnName) {
        return entity().getPropertyByColumnName(columnName, false).getName();
    }

    private Entity entity() {
        Entity entity = ModelProvider.getInstance().getEntityByTableName(SAVED_VIEW_TABLE);
        if (entity == null) {
            throw new IllegalStateException("Entity not found for table: " + SAVED_VIEW_TABLE);
        }
        return entity;
    }
}
