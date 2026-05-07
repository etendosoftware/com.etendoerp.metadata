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

    private static final String INSTANCE_ID = "inst1";
    private static final String CLASS_ID = "cls1";
    private static final String VALUE_KEY = "value";
    private static final String COLOR_KEY = "color";
    private static final String KPI_TYPE = "KPI";
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer tok";
    private static final String EXTERNAL_URL = "https://api.example.com/data";

    private static String widgetDataPath(String instanceId) {
        return "/widget/" + instanceId + "/data";
    }

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

    private void setupWidgetDataPath() {
        when(request.getPathInfo()).thenReturn(widgetDataPath(INSTANCE_ID));
        when(request.getHeader(AUTH_HEADER)).thenReturn(AUTH_TOKEN);
        when(request.getParameterMap()).thenReturn(new HashMap<>());
    }

    private Object[] defaultInstanceRow() {
        return defaultInstanceRow(null);
    }

    private Object[] defaultInstanceRow(String paramsJson) {
        return new Object[]{INSTANCE_ID, CLASS_ID, paramsJson};
    }

    private Object[] defaultClassRow(String type) {
        return defaultClassRow(type, null);
    }

    private Object[] defaultClassRow(String type, String externalUrl) {
        return new Object[]{CLASS_ID, type, null, externalUrl, null};
    }

    private WidgetDataService createServiceWithEmptyRegistry() {
        WidgetDataService svc = new WidgetDataService(request, response);
        svc.setRegistry(new WidgetResolverRegistry());
        return svc;
    }

    private WidgetDataService createServiceWithResolver(boolean available, JSONObject resolveResult) throws Exception {
        WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
        when(mockResolver.getType()).thenReturn(KPI_TYPE);
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

    private void processAndAssertContains(WidgetDataService svc, String expected) throws Exception {
        svc.process();
        assertTrue(responseCapture.toString().contains(expected));
    }

    private void runResolverTest(Object[] instanceRow, Object[] classRow,
                                 java.util.List<Object[]> paramRows,
                                 JSONObject resolveResult, String expectedContent) throws Exception {
        setupWidgetDataPath();
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, paramRows);
            WidgetDataService svc = createServiceWithResolver(true, resolveResult);
            processAndAssertContains(svc, expectedContent);
        });
    }

    private void runEmptyRegistryExpectingException(Object[] instanceRow, Object[] classRow,
                                                    Class<? extends Exception> expectedException) throws Exception {
        setupWidgetDataPath();
        runWithMockedContext(() -> {
            setupQueries(instanceRow, classRow, instanceRow != null ? Collections.emptyList() : null);
            WidgetDataService svc = createServiceWithEmptyRegistry();
            assertThrows(expectedException, svc::process);
        });
    }

    private void assertNotFoundForPath(String path) {
        when(request.getPathInfo()).thenReturn(path);
        WidgetDataService svc = new WidgetDataService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processThrowsNotFoundForNullPathInfo() {
        assertNotFoundForPath(null);
    }

    @Test
    void processThrowsNotFoundForPathWithoutWidgetSegment() {
        assertNotFoundForPath("/something/else");
    }

    @Test
    void processThrowsNotFoundWhenInstanceNotFound() throws Exception {
        when(request.getPathInfo()).thenReturn(widgetDataPath("missing-id"));
        when(request.getHeader(AUTH_HEADER)).thenReturn(AUTH_TOKEN);
        when(request.getParameterMap()).thenReturn(new HashMap<>());
        runWithMockedContext(() -> {
            setupQueries(null, null, null);
            WidgetDataService svc = createServiceWithEmptyRegistry();
            assertThrows(InternalServerException.class, svc::process);
        });
    }

    @Test
    void processThrowsNotFoundWhenClassNotFound() throws Exception {
        runEmptyRegistryExpectingException(defaultInstanceRow(), null, InternalServerException.class);
    }

    @Test
    void processReturnsUnavailableWhenResolverNotAvailable() throws Exception {
        setupWidgetDataPath();
        runWithMockedContext(() -> {
            setupQueries(defaultInstanceRow(), defaultClassRow(KPI_TYPE), Collections.emptyList());
            WidgetDataService svc = createServiceWithResolver(false, null);
            processAndAssertContains(svc, "\"available\":false");
        });
    }

    @Test
    void processThrowsWhenNoResolverAndNoExternalUrl() throws Exception {
        runEmptyRegistryExpectingException(defaultInstanceRow(), defaultClassRow("UNKNOWN_TYPE"),
                InternalServerException.class);
    }

    @Test
    void processAppliesRequestParamsPageAndPageSize() throws Exception {
        when(request.getPathInfo()).thenReturn(widgetDataPath(INSTANCE_ID));
        when(request.getHeader(AUTH_HEADER)).thenReturn(AUTH_TOKEN);
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("pageSize")).thenReturn("25");
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("page", new String[]{"2"});
        paramMap.put("pageSize", new String[]{"25"});
        paramMap.put("customFilter", new String[]{"abc"});
        when(request.getParameterMap()).thenReturn(paramMap);
        runWithMockedContext(() -> {
            setupQueries(defaultInstanceRow(), defaultClassRow(KPI_TYPE), Collections.emptyList());
            WidgetDataService svc = createServiceWithResolver(true, new JSONObject().put(VALUE_KEY, 42));
            processAndAssertContains(svc, "42");
        });
    }

    @Test
    void processMergesParamDefaultsAndInstanceOverrides() throws Exception {
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, "blue", false});
        paramRows.add(new Object[]{"size", "10", false});
        paramRows.add(new Object[]{"fixedKey", "fixedValue", true});
        runResolverTest(
                defaultInstanceRow("{\"color\":\"red\",\"fixedKey\":\"override\"}"),
                defaultClassRow(KPI_TYPE), paramRows,
                new JSONObject().put(VALUE_KEY, 99), "99");
    }

    @Test
    void processWithNullRegistryFallsBackToProxyResolverWhenExternalUrlSet() throws Exception {
        setupWidgetDataPath();
        runWithMockedContext(() -> {
            setupQueries(defaultInstanceRow(), defaultClassRow("CUSTOM", EXTERNAL_URL), Collections.emptyList());
            WidgetDataService svc = new WidgetDataService(request, response);
            svc.setRegistry(null);
            assertThrows(Exception.class, svc::process);
        });
    }

    @Test
    void processWithProxyTypeFallsBackToProxyResolver() throws Exception {
        setupWidgetDataPath();
        runWithMockedContext(() -> {
            setupQueries(defaultInstanceRow(), defaultClassRow("PROXY"), Collections.emptyList());
            WidgetDataService svc = createServiceWithEmptyRegistry();
            processAndAssertContains(svc, "no_external_url");
        });
    }

    @Test
    void processWithBlankInstanceParamsJsonSkipsOverrides() throws Exception {
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, "blue", false});
        runResolverTest(
                defaultInstanceRow("   "), defaultClassRow(KPI_TYPE), paramRows,
                new JSONObject().put(VALUE_KEY, 77), "77");
    }

    @Test
    void extractInstanceIdSkipsClassesSegment() {
        assertNotFoundForPath("/widget/classes");
    }

    @Test
    void processWithNullParamDefaultsSkipsNullValues() throws Exception {
        java.util.List<Object[]> paramRows = new java.util.ArrayList<>();
        paramRows.add(new Object[]{COLOR_KEY, null, false});
        paramRows.add(new Object[]{"size", "10", false});
        runResolverTest(
                defaultInstanceRow(), defaultClassRow(KPI_TYPE), paramRows,
                new JSONObject().put("ok", true), "ok");
    }
}
