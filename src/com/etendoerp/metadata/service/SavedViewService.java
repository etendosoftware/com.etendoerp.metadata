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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.data.EtmetaSavedView;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;

import static com.etendoerp.metadata.utils.Constants.SAVED_VIEW_PATH;
import java.io.IOException;
import java.util.List;

/**
 * Service for managing Saved Views.
 * Runs in admin mode to bypass entity-level security, but always scopes
 * reads and writes to the currently authenticated user.
 */
public class SavedViewService extends MetadataService {

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
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
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
            EtmetaSavedView view = OBDal.getInstance().get(EtmetaSavedView.class, id);
            if (view == null) {
                throw new NotFoundException();
            }
            write(wrapSingle(toJSON(view)));
        } else {
            String currentUserId = OBContext.getOBContext().getUser().getId();
            String tabId = getRequest().getParameter("tab");
            String isdefaultStr = getRequest().getParameter("isdefault");

            OBCriteria<EtmetaSavedView> crit = OBDal.getInstance().createCriteria(EtmetaSavedView.class);
            crit.add(Restrictions.eq(EtmetaSavedView.PROPERTY_USER + ".id", currentUserId));
            if (tabId != null && !tabId.isEmpty()) {
                crit.add(Restrictions.eq(EtmetaSavedView.PROPERTY_TAB + ".id", tabId));
            }
            if (isdefaultStr != null && !isdefaultStr.isEmpty()) {
                crit.add(Restrictions.eq(EtmetaSavedView.PROPERTY_ISDEFAULT, Boolean.parseBoolean(isdefaultStr)));
            }

            List<EtmetaSavedView> list = crit.list();
            JSONArray data = new JSONArray();
            for (EtmetaSavedView view : list) {
                data.put(toJSON(view));
            }
            write(wrapList(data));
        }
    }

    private void handlePost() throws Exception {
        JSONObject body = readBody();
        OBContext ctx = OBContext.getOBContext();

        EtmetaSavedView view = OBProvider.getInstance().get(EtmetaSavedView.class);
        view.setClient(OBDal.getInstance().get(Client.class, ctx.getCurrentClient().getId()));
        view.setOrganization(OBDal.getInstance().get(Organization.class, ctx.getCurrentOrganization().getId()));
        view.setUser(OBDal.getInstance().get(User.class, ctx.getUser().getId()));

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

        EtmetaSavedView view = OBDal.getInstance().get(EtmetaSavedView.class, id);
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

        EtmetaSavedView view = OBDal.getInstance().get(EtmetaSavedView.class, id);
        if (view == null) {
            throw new NotFoundException();
        }

        OBDal.getInstance().remove(view);
        OBDal.getInstance().flush();

        write(new JSONObject().put("response", new JSONObject().put("status", 0)));
    }

    private void applyBody(EtmetaSavedView view, JSONObject body) throws Exception {
        if (body.has("name")) {
            view.setName(body.getString("name"));
        }
        if (body.has("tab")) {
            view.setTab(OBDal.getInstance().get(Tab.class, body.getString("tab")));
        }
        if (body.has("isdefault")) {
            view.setDefault(body.getBoolean("isdefault"));
        }
        if (body.has("filterclause")) {
            String val = body.optString("filterclause", null);
            view.setFilterclause("null".equals(val) ? null : val);
        }
        if (body.has("gridconfiguration")) {
            String val = body.optString("gridconfiguration", null);
            view.setGridconfiguration("null".equals(val) ? null : val);
        }
    }

    private JSONObject toJSON(EtmetaSavedView view) throws Exception {
        JSONObject json = new JSONObject()
            .put("id", view.getId())
            .put("name", view.getName())
            .put("tab", view.getTab().getId())
            .put("user", view.getUser().getId())
            .put("isdefault", view.isDefault())
            .put("active", view.isActive());

        if (view.getFilterclause() != null) {
            json.put("filterclause", view.getFilterclause());
        } else {
            json.put("filterclause", JSONObject.NULL);
        }
        if (view.getGridconfiguration() != null) {
            json.put("gridconfiguration", view.getGridconfiguration());
        } else {
            json.put("gridconfiguration", JSONObject.NULL);
        }
        return json;
    }

    private JSONObject wrapSingle(JSONObject data) throws Exception {
        return new JSONObject().put("response",
            new JSONObject().put("status", 0).put("data", data));
    }

    private JSONObject wrapList(JSONArray data) throws Exception {
        return new JSONObject().put("response",
            new JSONObject()
                .put("status", 0)
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
        // Apply same normalization as ServiceFactory to remove module prefix
        String normalized = pathInfo.replace("/com.etendoerp.metadata.meta", "");
        // normalized: /saved-views or /saved-views/{id}
        // Strip the /saved-views prefix to get the remainder
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
}
