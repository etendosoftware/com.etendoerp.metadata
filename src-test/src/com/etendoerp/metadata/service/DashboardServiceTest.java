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

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.etendoerp.metadata.widgets.DashboardLayoutResolver;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext obContext;
    @Mock OBDal     obDal;
    @Mock Session   session;
    @Mock Query<Object[]> enrichQuery;

    @Test
    void getLayoutReturnsWidgetArray() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/dashboard/layout");

        JSONArray mockWidgets = new JSONArray();
        mockWidgets.put(new JSONObject().put("instanceId", "id1").put("widgetClassId", "cls1"));

        // enrichWithClassData query returns null (no class data needed for this assertion)
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(enrichQuery);
        when(enrichQuery.setParameter(anyString(), any())).thenReturn(enrichQuery);
        when(enrichQuery.uniqueResult()).thenReturn(null);

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal>     dalStatic = mockStatic(OBDal.class);
             MockedConstruction<DashboardLayoutResolver> resolverMock =
                     mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                             when(m.resolve()).thenReturn(mockWidgets))) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            String output = sw.toString();
            assertTrue(output.contains("id1"));
        }
    }
}
