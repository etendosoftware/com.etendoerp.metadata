package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;
import java.util.List;

/** Returns the current user's bookmarked workspace elements (OBKMO_WorkspaceElement). */
public class FavoritesResolver implements WidgetDataResolver {
    @Override public String getType() { return "FAVORITES"; }

    private static final String HQL =
        "select we.name, we.icon, we.actionType, we.windowId " +
        "from OBKMO_WorkspaceElement we " +
        "where we.userContact.id = :userId and we.isactive = 'Y' " +
        "order by we.seqno";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        try {
            String userId = ctx.getObContext().getUser().getId();
            Query<Object[]> q = OBDal.getInstance().getSession()
                    .createQuery(HQL, Object[].class);
            q.setParameter("userId", userId);
            List<Object[]> rows = q.list();

            JSONArray items = new JSONArray();
            for (Object[] row : rows) {
                items.put(new JSONObject()
                        .put("label",    row[0])
                        .put("icon",     row[1])
                        .put("type",     row[2])
                        .put("windowId", row[3]));
            }
            return new JSONObject().put("items", items);
        } catch (Exception e) {
            // OBKMO_WorkspaceElement may not be mapped if the workspace module is not installed
            return new JSONObject().put("items", new JSONArray());
        }
    }
}
