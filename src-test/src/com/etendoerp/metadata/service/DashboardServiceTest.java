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
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.access.User;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.widgets.DashboardLayoutResolver;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest extends AbstractMockedContextTest {

    private static final String RESTRICTED_CLASS_ID = "class-a";
    private static final String OTHER_ROLE_ID = "role-y";

    @Mock Query<Object[]> enrichQuery;
    @Mock Role role;
    @Mock User user;
    @Mock Client client;

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

    @Test
    void handlePostWidgetRejectsRestrictedTypeForOtherRole() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/dashboard/widget");
        when(request.getReader()).thenReturn(new BufferedReader(
                new StringReader("{\"widgetClassId\":\"" + RESTRICTED_CLASS_ID + "\"}")));

        when(obContext.getRole()).thenReturn(role);
        when(role.getId()).thenReturn(OTHER_ROLE_ID);
        when(role.isClientAdmin()).thenReturn(false);
        when(obContext.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user-1");
        when(obContext.getCurrentClient()).thenReturn(client);
        when(client.getId()).thenReturn("client-1");

        // Class A has 1 active access row (restricted) but not for OTHER_ROLE_ID (not granted)
        Query<Object[]> accessCountsQuery = mock();
        when(accessCountsQuery.setParameter(anyString(), any())).thenReturn(accessCountsQuery);
        when(accessCountsQuery.uniqueResult()).thenReturn(new Object[] { 1L, 0L });

        when(session.createQuery(
                argThat(s -> s != null && s.contains("etmeta_Widget_Class_Access")),
                eq(Object[].class)))
                .thenReturn(accessCountsQuery);

        runWithMockedContext(() -> {
            DashboardService svc = new DashboardService(request, response);
            InternalServerException ex = assertThrows(InternalServerException.class, svc::process);
            assertInstanceOf(UnauthorizedException.class, ex.getCause());
        });

        verify(session, never()).createQuery(contains("delete from etmeta_Dashboard_Widget"));
    }
}
