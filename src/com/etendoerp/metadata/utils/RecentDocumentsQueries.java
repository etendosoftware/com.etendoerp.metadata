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

package com.etendoerp.metadata.utils;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

import java.util.Date;
import java.util.List;

/**
 * Shared query logic for reading {@code ETMETA_RECENT_DOCUMENT} rows, used by both
 * {@code RecentDocumentsService} (the {@code GET /meta/recent-documents} REST endpoint) and
 * {@code RecentDocsResolver} (the {@code RECENT_DOCS} dashboard-widget data source), so the two
 * entry points stay consistent without duplicating the HQL, query execution and row-to-JSON
 * mapping.
 */
public final class RecentDocumentsQueries {

    private RecentDocumentsQueries() {
    }

    public static final int MAX_RECENT_DOCUMENTS = 10;

    private static final String USER_ID = "userId";
    private static final String ROLE_ID = "roleId";

    /**
     * Selects, for a given user + role, the most recently viewed documents whose window the role
     * still has access to (excludes documents for windows the role's access was later revoked
     * for). Row shape: {@code [recordId, identifier, windowId, windowName, tabId, tabLevel, viewedAt]}.
     */
    private static final String LIST_HQL =
        "select rd.recordID, rd.identifier, rd.window.id, rd.window.name, rd.tab.id, rd.tabLevel, rd.viewedAt "
            + "from etmeta_Recent_Document rd "
            + "where rd.userContact.id = :userId and rd.role.id = :roleId and rd.active = true "
            + "and exists (select 1 from ADWindowAccess wa where wa.window.id = rd.window.id "
            + "and wa.role.id = :roleId and wa.active = true) "
            + "order by rd.viewedAt desc";

    /**
     * Runs {@link #LIST_HQL} for the given user + role and maps the result to the JSON item shape
     * shared by the {@code GET /meta/recent-documents} response and the {@code RECENT_DOCS}
     * dashboard-widget data.
     *
     * @param userId the AD_User_ID to filter by
     * @param roleId the AD_Role_ID to filter by (also used to check window access)
     * @return the matching recent documents, most recently viewed first, capped at
     *         {@link #MAX_RECENT_DOCUMENTS}
     * @throws JSONException if a result row cannot be serialized to JSON
     */
    public static JSONArray fetchItems(String userId, String roleId) throws JSONException {
        Query<Object[]> query = OBDal.getInstance().getSession().createQuery(LIST_HQL, Object[].class);
        query.setParameter(USER_ID, userId);
        query.setParameter(ROLE_ID, roleId);
        query.setMaxResults(MAX_RECENT_DOCUMENTS);
        List<Object[]> rows = query.list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(toItemJson(row));
        }
        return items;
    }

    private static JSONObject toItemJson(Object[] row) throws JSONException {
        return new JSONObject()
                .put("recordId", row[0])
                .put("identifier", row[1])
                .put("windowId", row[2])
                .put("windowTitle", row[3])
                .put("tabId", row[4])
                .put("tabLevel", row[5])
                .put("viewedAt", ((Date) row[6]).getTime());
    }
}
