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

import com.etendoerp.metadata.data.RecentDocument;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Handles GET/POST /meta/recent-documents
 *
 * GET  -> {"items": [{"recordId","identifier","windowId","windowTitle","tabId","tabLevel","viewedAt"}, ...]}
 * POST body: {"windowId","tabId","recordId","identifier","tabLevel"} -> {"status": "ok"}
 *
 * Tracks the records a user opens in form view, per user + role, capped at MAX_RECENT_DOCUMENTS
 * and filtered to windows the current role still has access to (AD_Window_Access).
 */
public class RecentDocumentsService extends MetadataService {

    private static final int MAX_RECENT_DOCUMENTS = 10;

    private static final String USER_ID = "userId";
    private static final String ROLE_ID = "roleId";
    private static final String WINDOW_ID = "windowId";
    private static final String TAB_ID = "tabId";
    private static final String RECORD_ID = "recordId";
    private static final String IDENTIFIER = "identifier";
    private static final String TAB_LEVEL = "tabLevel";

    private static final String LIST_HQL =
        "select rd.recordID, rd.identifier, rd.window.id, rd.window.name, rd.tab.id, rd.tabLevel, rd.viewedAt " +
        "from etmeta_Recent_Document rd " +
        "where rd.userContact.id = :userId and rd.role.id = :roleId and rd.active = true " +
        "and exists (select 1 from ADWindowAccess wa where wa.window.id = rd.window.id " +
        "and wa.role.id = :roleId and wa.active = true) " +
        "order by rd.viewedAt desc";

    private static final String FIND_EXISTING_HQL =
        "from etmeta_Recent_Document rd " +
        "where rd.userContact.id = :userId and rd.role.id = :roleId " +
        "and rd.window.id = :windowId and rd.tab.id = :tabId and rd.recordID = :recordId";

    private static final String IDS_TO_TRIM_HQL =
        "select rd.id from etmeta_Recent_Document rd " +
        "where rd.userContact.id = :userId and rd.role.id = :roleId " +
        "order by rd.viewedAt desc";

    private static final String DELETE_BY_IDS_HQL = "delete from etmeta_Recent_Document rd where rd.id in (:ids)";

    /**
     * Creates a new RecentDocumentsService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public RecentDocumentsService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String method = getRequest().getMethod();

        try {
            OBContext.setAdminMode(true);
            String userId = OBContext.getOBContext().getUser().getId();
            String roleId = OBContext.getOBContext().getRole().getId();

            if ("GET".equalsIgnoreCase(method)) {
                write(list(userId, roleId));
            } else if ("POST".equalsIgnoreCase(method)) {
                JSONObject body = new JSONObject(readBody());
                write(track(userId, roleId, body));
            } else {
                throw new NotFoundException();
            }
        } catch (IOException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject list(String userId, String roleId) throws Exception {
        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(LIST_HQL, Object[].class);
        q.setParameter(USER_ID, userId);
        q.setParameter(ROLE_ID, roleId);
        q.setMaxResults(MAX_RECENT_DOCUMENTS);
        List<Object[]> rows = q.list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                    .put(RECORD_ID, row[0])
                    .put(IDENTIFIER, row[1])
                    .put(WINDOW_ID, row[2])
                    .put("windowTitle", row[3])
                    .put(TAB_ID, row[4])
                    .put(TAB_LEVEL, row[5])
                    .put("viewedAt", ((Date) row[6]).getTime()));
        }
        return new JSONObject().put("items", items);
    }

    private JSONObject track(String userId, String roleId, JSONObject body) throws Exception {
        String windowId = body.getString(WINDOW_ID);
        String tabId = body.getString(TAB_ID);
        String recordId = body.getString(RECORD_ID);
        String identifier = body.getString(IDENTIFIER);
        long tabLevel = body.optLong(TAB_LEVEL, 0);

        Query<RecentDocument> existingQ = OBDal.getInstance().getSession()
                .createQuery(FIND_EXISTING_HQL, RecentDocument.class);
        existingQ.setParameter(USER_ID, userId);
        existingQ.setParameter(ROLE_ID, roleId);
        existingQ.setParameter(WINDOW_ID, windowId);
        existingQ.setParameter(TAB_ID, tabId);
        existingQ.setParameter(RECORD_ID, recordId);
        existingQ.setMaxResults(1);
        RecentDocument doc = existingQ.uniqueResult();

        if (doc == null) {
            doc = (RecentDocument) OBProvider.getInstance().get(RecentDocument.class);
            doc.setClient(OBContext.getOBContext().getCurrentClient());
            doc.setOrganization(OBContext.getOBContext().getCurrentOrganization());
            doc.setUserContact(OBDal.getInstance().get(User.class, userId));
            doc.setRole(OBDal.getInstance().get(Role.class, roleId));
            doc.setWindow(OBDal.getInstance().get(Window.class, windowId));
            doc.setTab(OBDal.getInstance().get(Tab.class, tabId));
            doc.setRecordID(recordId);
        }
        doc.setIdentifier(identifier);
        doc.setTabLevel(tabLevel);
        doc.setViewedAt(new Date());

        try {
            OBDal.getInstance().getSession().saveOrUpdate(doc);
            OBDal.getInstance().flush();
        } catch (Exception e) {
            OBDal.getInstance().getSession().evict(doc);
            throw e;
        }

        trim(userId, roleId);

        return new JSONObject().put("status", "ok");
    }

    /** Keeps only the MAX_RECENT_DOCUMENTS most recently viewed rows for this user + role. */
    private void trim(String userId, String roleId) {
        Query<String> idsQ = OBDal.getInstance().getSession().createQuery(IDS_TO_TRIM_HQL, String.class);
        idsQ.setParameter(USER_ID, userId);
        idsQ.setParameter(ROLE_ID, roleId);
        List<String> ids = idsQ.list();

        if (ids.size() <= MAX_RECENT_DOCUMENTS) {
            return;
        }

        List<String> idsToDelete = ids.subList(MAX_RECENT_DOCUMENTS, ids.size());
        OBDal.getInstance().getSession()
                .createQuery(DELETE_BY_IDS_HQL)
                .setParameterList("ids", idsToDelete)
                .executeUpdate();
        OBDal.getInstance().flush();
    }

    private String readBody() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (java.io.BufferedReader reader = getRequest().getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
