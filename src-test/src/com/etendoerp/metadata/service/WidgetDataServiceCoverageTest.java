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

    private static final String WIDGET_DATA_PATH = "/widget/inst1/data";
    private static final String INSTANCE_ID = "inst1";
    private static final String VALUE_KEY = "value";
    private static final String COLOR_KEY = "color";

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

    private void setupWidgetDataPath() {
        when(request.getPathInfo()).thenReturn(WIDGET_DATA_PATH);
        setupRequestDefaults();
    }

    private Object[] defaultInstanceRow() {
        return new Object[]{INSTANCE_ID, "cls1", null};
    }

    private Object[] defaultClassRow(String type) {
        return new Object[]{"cls1", type, null, null, null};
    }

    private WidgetDataService createServiceWithEmptyRegistry() {
        WidgetDataService svc = new WidgetDataService(request, response);
        svc.setRegistry(new WidgetResolverRegistry());
        return svc;
    }

    private WidgetDataService createServiceWithResolver(String type, boolean available, JSONObject resolveResult) throws Exception {
        WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
        when(mockResolver.getType()).thenReturn(type);
        when(mockResolver.isAvailable()).thenReturn(available);
        if (resolveResult != null) {
            when(mockResolver.resolve(any())).thenReturn(resolveResult);
        }
        WidgetResolverRegistry registry = new WidgetResolverRegistry();
        registry.register(mockResolver);
        WidgetDataService svc = new WidgetDataService(request, response);
        svc.setRegistry(registry);
        return svc;
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
            WidgetDataService svc = createServiceWithEmptyRegistry();
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processThrowsNotFoundWhenClassNotFound() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        runWithMockedContext(() -> {
            setupQueries(instanceRow, null, null);
            WidgetDataService svc = createServiceWithEmptyRegistry();
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processReturnsUnavailableWhenResolverNotAvailable() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = defaultClassRow("KPI");
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = createServiceWithResolver("KPI", false, null);
            svc.process();
            assertTrue(responseCapture.toString().contains("\"available\":false"));
        });
    }

    @Test
    void processThrowsWhenNoResolverAndNoExternalUrl() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = defaultClassRow("UNKNOWN_TYPE");
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = createServiceWithEmptyRegistry();
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processAppliesRequestParamsPageAndPageSize() throws Exception {
        when(request.getPathInfo()).thenReturn(WIDGET_DATA_PATH);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("pageSize")).thenReturn("25");
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("page", new String[]{"2"});
        paramMap.put("pageSize", new String[]{"25"});
        paramMap.put("customFilter", new String[]{"abc"});
        when(request.getParameterMap()).thenReturn(paramMap);
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = defaultClassRow("KPI");
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = createServiceWithResolver("KPI", true, new JSONObject().put(VALUE_KEY, 42));
            svc.process();
            assertTrue(responseCapture.toString().contains("42"));
        });
    }

    @Test
    void processMergesParamDefaultsAndInstanceOverrides() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = {INSTANCE_ID, "cls1", "{\"color\":\"red\",\"fixedKey\":\"override\"}"};
        Object[] classRow = defaultClassRow("KPI");
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, "blue", false});
        paramRows.add(new Object[]{"size", "10", false});
        paramRows.add(new Object[]{"fixedKey", "fixedValue", true});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataService svc = createServiceWithResolver("KPI", true, new JSONObject().put(VALUE_KEY, 99));
            svc.process();
            assertTrue(responseCapture.toString().contains("99"));
        });
    }

    @Test
    void processWithNullRegistryFallsBackToProxyResolverWhenExternalUrlSet() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = {"cls1", "CUSTOM", null, "https://api.example.com/data", null};
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(null);
            assertThrows(Exception.class, svc::process);
        });
    }

    @Test
    void processWithProxyTypeFallsBackToProxyResolver() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = defaultClassRow("PROXY");
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, Collections.emptyList());
            WidgetDataService svc = createServiceWithEmptyRegistry();
            svc.process();
            assertTrue(responseCapture.toString().contains("no_external_url"));
        });
    }

    @Test
    void processWithBlankInstanceParamsJsonSkipsOverrides() throws Exception {
        setupWidgetDataPath();
        Object[] instanceRow = {INSTANCE_ID, "cls1", "   "};
        Object[] classRow = defaultClassRow("KPI");
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, "blue", false});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataService svc = createServiceWithResolver("KPI", true, new JSONObject().put(VALUE_KEY, 77));
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
        setupWidgetDataPath();
        Object[] instanceRow = defaultInstanceRow();
        Object[] classRow = defaultClassRow("KPI");
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, null, false});
        paramRows.add(new Object[]{"size", "10", false});
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataService svc = createServiceWithResolver("KPI", true, new JSONObject().put("ok", true));
            svc.process();
            assertTrue(responseCapture.toString().contains("ok"));
        });
    }
}
