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

import java.util.Date;
import java.util.List;

/** Returns the current user's recently viewed records from ETMETA_RECENT_DOCUMENT. */
public class RecentDocsResolver implements WidgetDataResolver {

    private static final int MAX_RECENT_DOCUMENTS = 10;

    private static final String HQL =
        "select rd.recordID, rd.identifier, rd.window.id, rd.window.name, rd.tab.id, rd.tabLevel, rd.viewedAt " +
        "from etmeta_Recent_Document rd " +
        "where rd.userContact.id = :userId and rd.role.id = :roleId and rd.active = true " +
        "and exists (select 1 from ADWindowAccess wa where wa.window.id = rd.window.id " +
        "and wa.role.id = :roleId and wa.active = true) " +
        "order by rd.viewedAt desc";

    @Override public String getType() { return "RECENT_DOCS"; }

    @Override
    public boolean isAvailable() {
        try {
            OBDal.getInstance().getSession()
                .createQuery("select 1 from etmeta_Recent_Document rd where 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String userId = ctx.getObContext().getUser().getId();
        String roleId = ctx.getObContext().getRole().getId();

        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(HQL, Object[].class);
        q.setParameter("userId", userId);
        q.setParameter("roleId", roleId);
        q.setMaxResults(MAX_RECENT_DOCUMENTS);
        List<Object[]> rows = q.list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                    .put("recordId", row[0])
                    .put("identifier", row[1])
                    .put("windowId", row[2])
                    .put("windowTitle", row[3])
                    .put("tabId", row[4])
                    .put("tabLevel", row[5])
                    .put("viewedAt", ((Date) row[6]).getTime()));
        }
        return new JSONObject().put("items", items);
    }
}
