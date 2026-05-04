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

package com.etendoerp.metadata.widgets;

import org.junit.jupiter.api.Test;
import org.openbravo.dal.core.OBContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class WidgetDataContextTest {

    private static final String INSTANCE_ID = "inst-001";
    private static final String BEARER = "Bearer tok123";
    private static final String COLOR = "color";

    private WidgetDataContext buildContext() {
        Map<String, Object> instanceData = new HashMap<>();
        instanceData.put("name", "My Widget");

        Map<String, Object> classData = new HashMap<>();
        classData.put("type", "KPI");
        classData.put("4", "<p>desc</p>");

        Map<String, Object> params = new HashMap<>();
        params.put(COLOR, "blue");
        params.put("count", 42);

        OBContext ctx = mock(OBContext.class);
        return new WidgetDataContext(INSTANCE_ID, instanceData, classData, params, ctx, BEARER);
    }

    @Test
    void gettersReturnConstructorValues() {
        WidgetDataContext ctx = buildContext();
        assertEquals(INSTANCE_ID, ctx.getInstanceId());
        assertEquals(BEARER, ctx.getBearerToken());
        assertNotNull(ctx.getObContext());
        assertEquals("My Widget", ctx.getInstanceData().get("name"));
        assertEquals("KPI", ctx.getClassData().get("type"));
        assertEquals("blue", ctx.getParams().get(COLOR));
    }

    @Test
    void mapsAreDefensiveCopies() {
        Map<String, Object> original = new HashMap<>();
        original.put("k", "v");
        WidgetDataContext ctx = new WidgetDataContext("id", original, original, original,
                mock(OBContext.class), null);
        assertNotSame(original, ctx.getInstanceData());
        assertNotSame(original, ctx.getClassData());
        assertNotSame(original, ctx.getParams());
    }

    @Test
    void classStringReturnsStringValue() {
        WidgetDataContext ctx = buildContext();
        assertEquals("KPI", ctx.classString("type"));
        assertEquals("<p>desc</p>", ctx.classString("4"));
    }

    @Test
    void classStringReturnsNullForMissingKey() {
        WidgetDataContext ctx = buildContext();
        assertNull(ctx.classString("nonexistent"));
    }

    @Test
    void classStringConvertsNonStringToString() {
        Map<String, Object> classData = new HashMap<>();
        classData.put("num", 123);
        WidgetDataContext ctx = new WidgetDataContext("id", new HashMap<>(), classData,
                new HashMap<>(), mock(OBContext.class), null);
        assertEquals("123", ctx.classString("num"));
    }

    @Test
    void paramReturnsStringValue() {
        WidgetDataContext ctx = buildContext();
        assertEquals("blue", ctx.param(COLOR));
    }

    @Test
    void paramReturnsNullForMissingKey() {
        WidgetDataContext ctx = buildContext();
        assertNull(ctx.param("nonexistent"));
    }

    @Test
    void paramConvertsNonStringToString() {
        WidgetDataContext ctx = buildContext();
        assertEquals("42", ctx.param("count"));
    }

    @Test
    void nullBearerTokenReturnsNull() {
        WidgetDataContext ctx = new WidgetDataContext("id", new HashMap<>(), new HashMap<>(),
                new HashMap<>(), mock(OBContext.class), null);
        assertNull(ctx.getBearerToken());
    }
}
