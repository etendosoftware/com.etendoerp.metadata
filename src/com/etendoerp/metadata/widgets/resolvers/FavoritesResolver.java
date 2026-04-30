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

/** Returns the current user's favorite menu items from ETMETA_USER_FAVORITE. */
public class FavoritesResolver implements WidgetDataResolver {
    @Override public String getType() { return "FAVORITES"; }

    @Override
    public boolean isAvailable() {
        try {
            OBDal.getInstance().getSession()
                .createQuery("select 1 from etmeta_User_Favorite f where 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String HQL =
        "select m.name, m.action, m.id, m.window.id " +
        "from etmeta_User_Favorite f join f.menu m " +
        "where f.userContact.id = :userId and f.role.id = :roleId and f.active = true " +
        "order by f.sequenceNo asc";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String userId = ctx.getObContext().getUser().getId();
        String roleId = ctx.getObContext().getRole().getId();
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("userId", userId);
        q.setParameter("roleId", roleId);
        List<Object[]> rows = q.list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            items.put(new JSONObject()
                    .put("label",    row[0])
                    .put("action",   row[1])
                    .put("menuId",   row[2])
                    .put("windowId", row[3] != null ? row[3] : JSONObject.NULL));
        }
        return new JSONObject().put("items", items);
    }
}
