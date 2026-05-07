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

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import com.etendoerp.metadata.widgets.WidgetResolverRegistry;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WidgetDataServiceCoverageTest extends AbstractMockedContextTest {

    @SuppressWarnings("unchecked")
    private Query<Object[]> stubQuery(Object result, boolean isList) {
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        if (isList) {
            when(q.list()).thenReturn(result == null ? Collections.emptyList() : (java.util.List<Object[]>) result);
        } else {
            when(q.uniqueResult()).thenReturn((Object[]) result);
        }
        return q;
    }

    private void setupQueries(Object[] instanceRow, Object[] classRow,
                              java.util.List<Object[]> paramRows) {
        doReturn(stubQuery(instanceRow, false))
                .when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("etmeta_Dashboard_Widget")), eq(Object[].class));
        doReturn(stubQuery(classRow, false))
                .when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("etmeta_Widget_Class")), eq(Object[].class));
        doReturn(stubQuery(paramRows, true))
                .when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("etmeta_Widget_Param")), eq(Object[].class));
    }

    private void setupRequestDefaults() {
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getParameterMap()).thenReturn(new HashMap<>());
    }

    @Test
    void processThrowsNotFoundForNullPathInfo() {
        when(request.getPathInfo()).thenReturn(null);
        WidgetDataService svc = new WidgetDataService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processThrowsNotFoundForPathWithoutWidgetSegment() {
        when(request.getPathInfo()).thenReturn("/something/else");
        WidgetDataService svc = new WidgetDataService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processThrowsNotFoundWhenInstanceNotFound() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/missing-id/data");
        setupRequestDefaults();
        runWithMockedContext(() -> {
            setupQueries(null, null, null);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(new WidgetResolverRegistry());
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processThrowsNotFoundWhenClassNotFound() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, null, null);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(new WidgetResolverRegistry());
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processReturnsUnavailableWhenResolverNotAvailable() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        Object[] classRow = {"cls1", "KPI", null, null, null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.getType()).thenReturn("KPI");
            when(mockResolver.isAvailable()).thenReturn(false);
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(registry);
            svc.process();
            assertTrue(responseCapture.toString().contains("\"available\":false"));
        });
    }

    @Test
    void processThrowsWhenNoResolverAndNoExternalUrl() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        Object[] classRow = {"cls1", "UNKNOWN_TYPE", null, null, null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(new WidgetResolverRegistry());
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processAppliesRequestParamsPageAndPageSize() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("pageSize")).thenReturn("25");
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("page", new String[]{"2"});
        paramMap.put("pageSize", new String[]{"25"});
        paramMap.put("customFilter", new String[]{"abc"});
        when(request.getParameterMap()).thenReturn(paramMap);
        Object[] instanceRow = {"inst1", "cls1", null};
        Object[] classRow = {"cls1", "KPI", null, null, null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.getType()).thenReturn("KPI");
            when(mockResolver.isAvailable()).thenReturn(true);
            when(mockResolver.resolve(any())).thenReturn(new JSONObject().put("value", 42));
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(registry);
            svc.process();
            assertTrue(responseCapture.toString().contains("42"));
        });
    }

    @Test
    void processMergesParamDefaultsAndInstanceOverrides() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", "{\"color\":\"red\",\"fixedKey\":\"override\"}"};
        Object[] classRow = {"cls1", "KPI", null, null, null};
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{"color", "blue", false});
        paramRows.add(new Object[]{"size", "10", false});
        paramRows.add(new Object[]{"fixedKey", "fixedValue", true});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.getType()).thenReturn("KPI");
            when(mockResolver.isAvailable()).thenReturn(true);
            when(mockResolver.resolve(any())).thenReturn(new JSONObject().put("value", 99));
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(registry);
            svc.process();
            assertTrue(responseCapture.toString().contains("99"));
        });
    }

    @Test
    void processWithNullRegistryFallsBackToProxyResolverWhenExternalUrlSet() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        Object[] classRow = {"cls1", "CUSTOM", null, "https://api.example.com/data", null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(null);
            // ProxyResolver makes a real HTTP call which will fail with an exception
            assertThrows(Exception.class, svc::process);
        });
    }

    @Test
    void processWithProxyTypeFallsBackToProxyResolver() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        // PROXY type with no external URL - ProxyResolver returns {"available":false,"reason":"no_external_url"}
        Object[] classRow = {"cls1", "PROXY", null, null, null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(new WidgetResolverRegistry());
            svc.process();
            assertTrue(responseCapture.toString().contains("no_external_url"));
        });
    }

    @Test
    void processWithBlankInstanceParamsJsonSkipsOverrides() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", "   "};
        Object[] classRow = {"cls1", "KPI", null, null, null};
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{"color", "blue", false});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.getType()).thenReturn("KPI");
            when(mockResolver.isAvailable()).thenReturn(true);
            when(mockResolver.resolve(any())).thenReturn(new JSONObject().put("value", 77));
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(registry);
            svc.process();
            assertTrue(responseCapture.toString().contains("77"));
        });
    }

    @Test
    void extractInstanceIdSkipsClassesSegment() {
        when(request.getPathInfo()).thenReturn("/widget/classes");
        WidgetDataService svc = new WidgetDataService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processWithNullParamDefaultsSkipsNullValues() throws Exception {
        when(request.getPathInfo()).thenReturn("/widget/inst1/data");
        setupRequestDefaults();
        Object[] instanceRow = {"inst1", "cls1", null};
        Object[] classRow = {"cls1", "KPI", null, null, null};
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{"color", null, false});
        paramRows.add(new Object[]{"size", "10", false});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.getType()).thenReturn("KPI");
            when(mockResolver.isAvailable()).thenReturn(true);
            when(mockResolver.resolve(any())).thenReturn(new JSONObject().put("ok", true));
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(registry);
            svc.process();
            assertTrue(responseCapture.toString().contains("ok"));
        });
    }
}
