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
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import com.etendoerp.metadata.widgets.WidgetResolverRegistry;
import com.etendoerp.metadata.widgets.WidgetResolverRegistryHolder;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WidgetClassesServiceCoverageTest extends AbstractMockedContextTest {

    @SuppressWarnings("unchecked")
    private void setupClassAndParamQueries(List<Object[]> classRows, List<Object[]> paramRows) {
        Query<Object[]> classQuery = mock(Query.class);
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(classQuery);
        when(classQuery.list()).thenReturn(classRows);

        Query<Object[]> paramQuery = mock(Query.class);
        when(session.createQuery(argThat(s -> s != null && s.contains("etmeta_Widget_Param")), eq(Object[].class)))
                .thenReturn(paramQuery);
        when(paramQuery.setParameter(anyString(), any())).thenReturn(paramQuery);
        when(paramQuery.list()).thenReturn(paramRows);
    }

    private static List<Object[]> asRowList(Object[]... rows) {
        List<Object[]> list = new java.util.ArrayList<>();
        Collections.addAll(list, rows);
        return list;
    }

    @Test
    void processWithParamsIncludingListValues() throws Exception {
        Object[] classRow = {"classId1", "my-widget", "KPI", "My Widget", "A test widget", 2, 1, 30};
        Object[] paramRow = {"theme", "Theme", "LIST", true, false, "dark", "dark:Dark,light:Light"};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            when(registry.getResolver(anyString())).thenReturn(null);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(asRowList(classRow), asRowList(paramRow));

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                JSONArray classes = result.getJSONArray("classes");
                assertEquals(1, classes.length());

                JSONObject cls = classes.getJSONObject(0);
                JSONArray params = cls.getJSONArray("params");
                assertEquals(1, params.length());

                JSONObject param = params.getJSONObject(0);
                assertEquals("theme", param.getString("name"));
                assertTrue(param.getBoolean("required"));
                assertFalse(param.getBoolean("fixed"));
                assertEquals("dark", param.getString("defaultValue"));

                JSONArray listVals = param.getJSONArray("listValues");
                assertEquals(2, listVals.length());
                assertEquals("dark", listVals.getJSONObject(0).getString("value"));
                assertEquals("Dark", listVals.getJSONObject(0).getString("label"));
                assertEquals("light", listVals.getJSONObject(1).getString("value"));
                assertEquals("Light", listVals.getJSONObject(1).getString("label"));
            });
        }
    }

    @Test
    void processWithListValuesContainingInvalidEntry() throws Exception {
        Object[] classRow = {"classId1", "my-widget", "KPI", "My Widget", "desc", 2, 1, 30};
        // "nocolon" has no colon separator, should be skipped
        Object[] paramRow = {"mode", "Mode", "LIST", false, false, null, "a:Alpha,nocolon,b:Beta"};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            when(registry.getResolver(anyString())).thenReturn(null);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(asRowList(classRow), asRowList(paramRow));

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                JSONArray params = result.getJSONArray("classes").getJSONObject(0).getJSONArray("params");
                // "nocolon" is split with limit 2, so it produces ["nocolon"], length 1 → skipped
                // Actually split(":", 2) on "nocolon" gives ["nocolon"], length 1 → skipped
                JSONArray listVals = params.getJSONObject(0).getJSONArray("listValues");
                assertEquals(2, listVals.length());
            });
        }
    }

    @Test
    void processWithEmptyClassListReturnsEmptyArray() throws Exception {
        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(Collections.emptyList(), Collections.emptyList());

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                assertEquals(0, result.getJSONArray("classes").length());
            });
        }
    }

    @Test
    void processWithUnavailableResolverSetsAvailableFalse() throws Exception {
        Object[] classRow = {"classId1", "copilot-widget", "COPILOT", "Copilot", "desc", 2, 1, 30};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);

            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.isAvailable()).thenReturn(false);
            when(registry.getResolver("COPILOT")).thenReturn(mockResolver);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(asRowList(classRow), Collections.emptyList());

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                JSONObject cls = result.getJSONArray("classes").getJSONObject(0);
                assertFalse(cls.getBoolean("available"));
            });
        }
    }

    @Test
    void processWithMultipleParamsIncludingNullListValues() throws Exception {
        Object[] classRow = {"classId1", "my-widget", "KPI", "My Widget", "desc", 2, 1, 30};
        Object[] param1 = {"url", "URL", "TEXT", true, true, "https://default.com", null};
        Object[] param2 = {"color", "Color", "LIST", false, false, "blue", "blue:Blue,red:Red"};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            when(registry.getResolver(anyString())).thenReturn(null);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(asRowList(classRow), asRowList(param1, param2));

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                JSONArray params = result.getJSONArray("classes").getJSONObject(0).getJSONArray("params");
                assertEquals(2, params.length());

                JSONObject urlParam = params.getJSONObject(0);
                assertTrue(urlParam.getBoolean("required"));
                assertTrue(urlParam.getBoolean("fixed"));
                assertFalse(urlParam.has("listValues"));

                JSONObject colorParam = params.getJSONObject(1);
                assertTrue(colorParam.has("listValues"));
                assertEquals(2, colorParam.getJSONArray("listValues").length());
            });
        }
    }

    @Test
    void processWithMultipleClassRows() throws Exception {
        Object[] classRow1 = {"cls1", "widget-a", "KPI", "Widget A", "desc", 2, 1, 30};
        Object[] classRow2 = {"cls2", "widget-b", "HTML", "Widget B", "desc2", 4, 2, 60};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            when(registry.getResolver(anyString())).thenReturn(null);

            runWithMockedContext(() -> {
                setupClassAndParamQueries(asRowList(classRow1, classRow2), Collections.emptyList());

                WidgetClassesService svc = new WidgetClassesService(request, response);
                svc.process();

                JSONObject result = new JSONObject(responseCapture.toString());
                JSONArray classes = result.getJSONArray("classes");
                assertEquals(2, classes.length());
                assertEquals("KPI", classes.getJSONObject(0).getString("type"));
                assertEquals("HTML", classes.getJSONObject(1).getString("type"));
            });
        }
    }
}
