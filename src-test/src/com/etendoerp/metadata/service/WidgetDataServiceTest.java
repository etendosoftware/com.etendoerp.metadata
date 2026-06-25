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

import com.etendoerp.metadata.widgets.WidgetDataResolver;
import com.etendoerp.metadata.widgets.WidgetResolverRegistry;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WidgetDataServiceTest extends AbstractMockedContextTest {

    @SuppressWarnings("unchecked")
    private Query<Object[]> createStubQuery(Object result, boolean isList) {
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        if (isList) {
            when(q.list()).thenReturn(result == null ? Collections.emptyList() : (java.util.List<Object[]>) result);
        } else {
            when(q.uniqueResult()).thenReturn((Object[]) result);
        }
        return q;
    }

    @Test
    void processDelegatesToRegistryResolver() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/some-instance-id/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getParameterMap()).thenReturn(new HashMap<>());

        Object[] instanceRow = { "some-instance-id", "cls1", null };
        Object[] classRow    = { "cls1", "KPI", null, null, null };

        Query<Object[]> instanceQuery = createStubQuery(instanceRow, false);
        Query<Object[]> classQuery    = createStubQuery(classRow, false);
        Query<Object[]> paramsQuery   = createStubQuery(null, true);

        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Dashboard_Widget")), eq(Object[].class)))
                .thenReturn(instanceQuery);
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(classQuery);
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Param")), eq(Object[].class)))
                .thenReturn(paramsQuery);

        WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
        JSONObject mockData = new JSONObject().put("value", 95);
        when(mockResolver.getType()).thenReturn("KPI");
        when(mockResolver.isAvailable()).thenReturn(true);
        when(mockResolver.resolve(any())).thenReturn(mockData);

        runWithMockedContext(() -> {
            WidgetDataService svc = new WidgetDataService(request, response);
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            svc.setRegistry(registry);
            svc.process();

            String output = responseCapture.toString();
            assertTrue(output.contains("widgetInstanceId"));
            assertTrue(output.contains("95"));
        });
    }
}
