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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.query.Query;
import org.openbravo.model.ad.access.Role;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WidgetClassesServiceTest extends AbstractMockedContextTest {

    private static final String CLASSES = "classes";
    private static final String ROLE_ID = "role-y";

    @Mock Query<Object[]> query;
    @Mock Role role;

    private void stubCurrentRole() {
        when(obContext.getRole()).thenReturn(role);
        when(role.getId()).thenReturn(ROLE_ID);
    }

    @Test
    void getClassesReturnsClassesArray() throws Exception {
        stubCurrentRole();
        Object[] classRow = { "classId1", "my-widget", "KPI", "My Widget",
                              "A test widget", 2, 1, 30 };
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(query);
        when(query.list()).thenReturn(Collections.singletonList(classRow));

        Query<Object[]> paramQuery = mock();
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Param")), eq(Object[].class)))
                .thenReturn(paramQuery);
        when(paramQuery.setParameter(anyString(), any())).thenReturn(paramQuery);
        when(paramQuery.list()).thenReturn(Collections.emptyList());

        runWithMockedContext(() -> {
            WidgetClassesService svc = new WidgetClassesService(request, response);
            svc.process();

            JSONObject result = new JSONObject(responseCapture.toString());
            assertTrue(result.has(CLASSES));
            assertEquals(1, result.getJSONArray(CLASSES).length());
            assertEquals("KPI", result.getJSONArray(CLASSES).getJSONObject(0).getString("type"));
        });
    }

    @Test
    void catalogQueryIncludesRoleAccessFilter() throws Exception {
        stubCurrentRole();
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(query);
        when(query.list()).thenReturn(Collections.emptyList());

        runWithMockedContext(() -> {
            WidgetClassesService svc = new WidgetClassesService(request, response);
            svc.process();
        });

        ArgumentCaptor<String> hqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(session).createQuery(hqlCaptor.capture(), eq(Object[].class));
        String hql = hqlCaptor.getValue();
        assertTrue(hql.contains("etmeta_Widget_Class_Access"),
                "catalog HQL must join the access-control table so restricted types can be filtered out");
        assertTrue(hql.contains(":roleId"),
                "catalog HQL must be parameterized by the current role");
        verify(query).setParameter("roleId", ROLE_ID);
    }
}
