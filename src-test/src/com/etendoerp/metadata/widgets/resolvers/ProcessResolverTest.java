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
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessResolverTest {
    private static final String PROCESS_ID = "processId";

    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void resolveMissingProcessIdReturnsError() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param(PROCESS_ID)).thenReturn(null);

        JSONObject result = new ProcessResolver().resolve(ctx);
        assertEquals("error", result.getString("status"));
        assertTrue(result.getString("message").contains(PROCESS_ID));
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveValidProcessIdReturnsSuccess() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param(PROCESS_ID)).thenReturn("proc1");

        Object[] row = { "proc1", "My Process" };
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.uniqueResult()).thenReturn(row);
        when(session.createQuery(anyString(), eq(Object[].class))).thenReturn(q);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new ProcessResolver().resolve(ctx);
            assertEquals("success", result.getString("status"));
            assertEquals("My Process", result.getJSONObject("result").getString("name"));
        }
    }
}
