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
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

/**
 * Returns metadata for an AD_Process identified by the "processId" param.
 * Returns { "status": "success|error", "message": "...", "result": {} }.
 */
public class ProcessResolver implements WidgetDataResolver {
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String RESULT_KEY = "result";

    @Override public String getType() { return "PROCESS"; }

    private static final String HQL =
        "select p.id, p.name from AD_Process p where p.id = :id";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String processId = ctx.param("processId");
        if (processId == null) {
            return new JSONObject()
                    .put(STATUS_KEY,  "error")
                    .put(MESSAGE_KEY, "processId param missing")
                    .put(RESULT_KEY,  new JSONObject());
        }

        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(HQL, Object[].class);
        q.setParameter("id", processId);
        Object[] row = q.uniqueResult();

        if (row == null) {
            return new JSONObject()
                    .put(STATUS_KEY,  "error")
                    .put(MESSAGE_KEY, "Process not found: " + processId)
                    .put(RESULT_KEY,  new JSONObject());
        }

        String name = (String) row[1];
        return new JSONObject()
                .put(STATUS_KEY,  "success")
                .put(MESSAGE_KEY, "Process '" + name + "' is ready to execute.")
                .put(RESULT_KEY,  new JSONObject()
                        .put("processId", processId)
                        .put("name",      name));
    }
}
