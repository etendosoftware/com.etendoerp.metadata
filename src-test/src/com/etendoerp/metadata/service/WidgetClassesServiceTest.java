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

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.hibernate.Session;
import org.hibernate.query.Query;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WidgetClassesServiceTest {

    private static final String CLASSES = CLASSES;

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext            obContext;
    @Mock OBDal                obDal;
    @Mock Session              session;
    @Mock Query<Object[]>      query;

    @Test
    void getClassesReturnsClassesArray() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        Object[] classRow = { "classId1", "my-widget", "KPI", "My Widget",
                              "A test widget", 2, 1, 30 };
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetClass")), eq(Object[].class)))
                .thenReturn(query);
        when(query.list()).thenReturn(Collections.singletonList(classRow));

        // params query returns empty list
        Query<Object[]> paramQuery = mock();
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetParam")), eq(Object[].class)))
                .thenReturn(paramQuery);
        when(paramQuery.list()).thenReturn(Collections.emptyList());

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal>     dalStatic = mockStatic(OBDal.class)) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            WidgetClassesService svc = new WidgetClassesService(request, response);
            svc.process();

            String output = sw.toString();
            JSONObject result = new JSONObject(output);
            assertTrue(result.has(CLASSES));
            assertEquals(1, result.getJSONArray(CLASSES).length());
            assertEquals("KPI", result.getJSONArray(CLASSES).getJSONObject(0).getString("type"));
        }
    }
}
