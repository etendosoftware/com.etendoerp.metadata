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
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import com.etendoerp.metadata.widgets.DashboardLayoutResolver;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest extends AbstractMockedContextTest {

    @Mock Query<Object[]> enrichQuery;

    @Test
    void getLayoutReturnsWidgetArray() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/dashboard/layout");

        JSONArray mockWidgets = new JSONArray();
        mockWidgets.put(new JSONObject().put("instanceId", "id1").put("widgetClassId", "cls1"));

        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(enrichQuery);
        when(enrichQuery.setParameter(anyString(), any())).thenReturn(enrichQuery);
        when(enrichQuery.uniqueResult()).thenReturn(null);

        try (MockedConstruction<DashboardLayoutResolver> resolverMock =
                     mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                             when(m.resolve()).thenReturn(mockWidgets))) {
            runWithMockedContext(() -> {
                DashboardService svc = new DashboardService(request, response);
                svc.process();
                assertTrue(responseCapture.toString().contains("id1"));
            });
        }
    }
}
