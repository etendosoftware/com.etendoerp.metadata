package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

/**
 * Returns metadata for an AD_Process identified by the "processId" param.
 * Returns { "status": "success|error", "message": "...", "result": {} }.
 */
public class ProcessResolver implements WidgetDataResolver {
    @Override public String getType() { return "PROCESS"; }

    private static final String HQL =
        "select p.id, p.name from AD_Process p where p.id = :id";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String processId = ctx.param("processId");
        if (processId == null) {
            return new JSONObject()
                    .put("status",  "error")
                    .put("message", "processId param missing")
                    .put("result",  new JSONObject());
        }

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("id", processId);
        Object[] row = q.uniqueResult();

        if (row == null) {
            return new JSONObject()
                    .put("status",  "error")
                    .put("message", "Process not found: " + processId)
                    .put("result",  new JSONObject());
        }

        String name = (String) row[1];
        return new JSONObject()
                .put("status",  "success")
                .put("message", "Process '" + name + "' is ready to execute.")
                .put("result",  new JSONObject()
                        .put("processId", processId)
                        .put("name",      name));
    }
}
