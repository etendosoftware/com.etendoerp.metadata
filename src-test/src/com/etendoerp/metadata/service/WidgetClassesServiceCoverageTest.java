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

    private static final String CLASS_ID_1 = "classId1";
    private static final String WIDGET_NAME = "My Widget";
    private static final String WIDGET_KEY = "my-widget";
    private static final String CLASSES_KEY = "classes";
    private static final String PARAMS_KEY = "params";
    private static final String LIST_VALUES_KEY = "listValues";

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

    private void runWithRegistryAndNullResolver(ThrowingRunnable action) throws Exception {
        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            when(registry.getResolver(anyString())).thenReturn(null);
            runWithMockedContext(action);
        }
    }

    private JSONObject processAndParse(List<Object[]> classRows, List<Object[]> paramRows) throws Exception {
        setupClassAndParamQueries(classRows, paramRows);
        WidgetClassesService svc = new WidgetClassesService(request, response);
        svc.process();
        return new JSONObject(responseCapture.toString());
    }

    @Test
    void processWithParamsIncludingListValues() throws Exception {
        Object[] classRow = {CLASS_ID_1, WIDGET_KEY, "KPI", WIDGET_NAME, "A test widget", 2, 1, 30};
        Object[] paramRow = {"theme", "Theme", "LIST", true, false, "dark", "dark:Dark,light:Light"};

        runWithRegistryAndNullResolver(() -> {
            JSONObject result = processAndParse(asRowList(classRow), asRowList(paramRow));
            JSONArray classes = result.getJSONArray(CLASSES_KEY);
            assertEquals(1, classes.length());

            JSONObject cls = classes.getJSONObject(0);
            JSONArray params = cls.getJSONArray(PARAMS_KEY);
            assertEquals(1, params.length());

            JSONObject param = params.getJSONObject(0);
            assertEquals("theme", param.getString("name"));
            assertTrue(param.getBoolean("required"));
            assertFalse(param.getBoolean("fixed"));
            assertEquals("dark", param.getString("defaultValue"));

            JSONArray listVals = param.getJSONArray(LIST_VALUES_KEY);
            assertEquals(2, listVals.length());
            assertEquals("dark", listVals.getJSONObject(0).getString("value"));
            assertEquals("Dark", listVals.getJSONObject(0).getString("label"));
            assertEquals("light", listVals.getJSONObject(1).getString("value"));
            assertEquals("Light", listVals.getJSONObject(1).getString("label"));
        });
    }

    @Test
    void processWithListValuesContainingInvalidEntry() throws Exception {
        Object[] classRow = {CLASS_ID_1, WIDGET_KEY, "KPI", WIDGET_NAME, "desc", 2, 1, 30};
        Object[] paramRow = {"mode", "Mode", "LIST", false, false, null, "a:Alpha,nocolon,b:Beta"};

        runWithRegistryAndNullResolver(() -> {
            JSONObject result = processAndParse(asRowList(classRow), asRowList(paramRow));
            JSONArray params = result.getJSONArray(CLASSES_KEY).getJSONObject(0).getJSONArray(PARAMS_KEY);
            JSONArray listVals = params.getJSONObject(0).getJSONArray(LIST_VALUES_KEY);
            assertEquals(2, listVals.length());
        });
    }

    @Test
    void processWithEmptyClassListReturnsEmptyArray() throws Exception {
        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);

            runWithMockedContext(() -> {
                JSONObject result = processAndParse(Collections.emptyList(), Collections.emptyList());
                assertEquals(0, result.getJSONArray(CLASSES_KEY).length());
            });
        }
    }

    @Test
    void processWithUnavailableResolverSetsAvailableFalse() throws Exception {
        Object[] classRow = {CLASS_ID_1, "copilot-widget", "COPILOT", "Copilot", "desc", 2, 1, 30};

        try (MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class)) {
            WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);

            WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
            when(mockResolver.isAvailable()).thenReturn(false);
            when(registry.getResolver("COPILOT")).thenReturn(mockResolver);

            runWithMockedContext(() -> {
                JSONObject result = processAndParse(asRowList(classRow), Collections.emptyList());
                JSONObject cls = result.getJSONArray(CLASSES_KEY).getJSONObject(0);
                assertFalse(cls.getBoolean("available"));
            });
        }
    }

    @Test
    void processWithMultipleParamsIncludingNullListValues() throws Exception {
        Object[] classRow = {CLASS_ID_1, WIDGET_KEY, "KPI", WIDGET_NAME, "desc", 2, 1, 30};
        Object[] param1 = {"url", "URL", "TEXT", true, true, "https://default.com", null};
        Object[] param2 = {"color", "Color", "LIST", false, false, "blue", "blue:Blue,red:Red"};

        runWithRegistryAndNullResolver(() -> {
            JSONObject result = processAndParse(asRowList(classRow), asRowList(param1, param2));
            JSONArray params = result.getJSONArray(CLASSES_KEY).getJSONObject(0).getJSONArray(PARAMS_KEY);
            assertEquals(2, params.length());

            JSONObject urlParam = params.getJSONObject(0);
            assertTrue(urlParam.getBoolean("required"));
            assertTrue(urlParam.getBoolean("fixed"));
            assertFalse(urlParam.has(LIST_VALUES_KEY));

            JSONObject colorParam = params.getJSONObject(1);
            assertTrue(colorParam.has(LIST_VALUES_KEY));
            assertEquals(2, colorParam.getJSONArray(LIST_VALUES_KEY).length());
        });
    }

    @Test
    void processWithMultipleClassRows() throws Exception {
        Object[] classRow1 = {"cls1", "widget-a", "KPI", "Widget A", "desc", 2, 1, 30};
        Object[] classRow2 = {"cls2", "widget-b", "HTML", "Widget B", "desc2", 4, 2, 60};

        runWithRegistryAndNullResolver(() -> {
            JSONObject result = processAndParse(asRowList(classRow1, classRow2), Collections.emptyList());
            JSONArray classes = result.getJSONArray(CLASSES_KEY);
            assertEquals(2, classes.length());
            assertEquals("KPI", classes.getJSONObject(0).getString("type"));
            assertEquals("HTML", classes.getJSONObject(1).getString("type"));
        });
    }
}
