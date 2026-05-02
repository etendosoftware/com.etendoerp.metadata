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

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetDataResponseTest {

    private static final String INSTANCE_ID = "inst-001";
    private static final String TYPE = "KPI";

    @Test
    void buildWithTotalRowsReturnsCompleteEnvelope() throws Exception {
        JSONObject data = new JSONObject().put("value", 95);
        JSONObject result = WidgetDataResponse.build(INSTANCE_ID, TYPE, data, 10);

        assertEquals(INSTANCE_ID, result.getString("widgetInstanceId"));
        assertEquals(TYPE, result.getString("type"));
        assertEquals(95, result.getJSONObject("data").getInt("value"));

        JSONObject meta = result.getJSONObject("meta");
        assertEquals(10, meta.getInt("totalRows"));
        assertFalse(meta.getBoolean("hasMore"));
        assertTrue(meta.has("lastUpdate"));
    }

    @Test
    void buildWithoutTotalRowsSetsNullTotalRows() throws Exception {
        JSONObject data = new JSONObject().put("items", "list");
        JSONObject result = WidgetDataResponse.build(INSTANCE_ID, TYPE, data, null);

        JSONObject meta = result.getJSONObject("meta");
        assertTrue(meta.isNull("totalRows"));
    }

    @Test
    void buildTwoArgOverloadSetsNullTotalRows() throws Exception {
        JSONObject data = new JSONObject();
        JSONObject result = WidgetDataResponse.build(INSTANCE_ID, TYPE, data);

        JSONObject meta = result.getJSONObject("meta");
        assertTrue(meta.isNull("totalRows"));
        assertFalse(meta.getBoolean("hasMore"));
    }

    @Test
    void unavailableReturnsCorrectStructure() throws Exception {
        JSONObject result = WidgetDataResponse.unavailable(INSTANCE_ID, TYPE);

        assertEquals(INSTANCE_ID, result.getString("widgetInstanceId"));
        assertEquals(TYPE, result.getString("type"));
        assertFalse(result.getBoolean("available"));
        assertTrue(result.isNull("data"));
        assertTrue(result.isNull("meta"));
    }

    @Test
    void buildContainsLastUpdateTimestamp() throws Exception {
        JSONObject data = new JSONObject();
        JSONObject result = WidgetDataResponse.build(INSTANCE_ID, TYPE, data, 5);

        String lastUpdate = result.getJSONObject("meta").getString("lastUpdate");
        assertTrue(lastUpdate.contains("T"), "lastUpdate should be an ISO instant");
    }
}
