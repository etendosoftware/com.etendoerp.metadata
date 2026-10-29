/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;

/**
 * Unit tests for {@link LegacyProcessResolver#withManualProcessId(Field, Map)}: Manual processes
 * receive the {@code inpProcessId} Classic submits when it opens their popup (ETP-6022).
 */
class LegacyProcessResolverManualProcessIdTest {

    private static final String PROCESS_ID = "468156B616E04DB39CBAD3592316FD50";
    private static final String INP_PROCESSED = "inpprocessed";
    private static final String PROCESSED_PLACEHOLDER = "$record.processed!yn";
    private static final String UIPATTERN_MANUAL = "M";
    private static final String UIPATTERN_STANDARD = "S";

    /**
     * Builds a field whose button column runs a process with the given UI pattern.
     *
     * @param uiPattern the {@code AD_Process.UIPattern} of the button process
     * @return the field mock
     */
    private static Field fieldWithProcess(String uiPattern) {
        Process process = mock(Process.class);
        when(process.getUIPattern()).thenReturn(uiPattern);
        when(process.getId()).thenReturn(PROCESS_ID);
        Column column = mock(Column.class);
        when(column.getProcess()).thenReturn(process);
        Field field = mock(Field.class);
        when(field.getColumn()).thenReturn(column);
        return field;
    }

    /**
     * Manual processes get the {@code inpProcessId} and keep the column-based parameters.
     */
    @Test
    void addsProcessIdForManualProcesses() {
        Map<String, String> result = LegacyProcessResolver.withManualProcessId(fieldWithProcess(UIPATTERN_MANUAL),
                Map.of(INP_PROCESSED, PROCESSED_PLACEHOLDER));

        assertEquals(PROCESS_ID, result.get(LegacyProcessResolver.INP_PROCESS_ID));
        assertEquals(PROCESSED_PLACEHOLDER, result.get(INP_PROCESSED));
    }

    /**
     * Manual processes still get the {@code inpProcessId} when no column parameter was resolved
     * (the column-based map is immutable and empty).
     */
    @Test
    void addsProcessIdToEmptyParameters() {
        Map<String, String> result = LegacyProcessResolver.withManualProcessId(fieldWithProcess(UIPATTERN_MANUAL),
                Collections.emptyMap());

        assertEquals(Map.of(LegacyProcessResolver.INP_PROCESS_ID, PROCESS_ID), result);
    }

    /**
     * Non-manual buttons keep their parameters untouched.
     */
    @Test
    void keepsParametersForStandardProcesses() {
        Map<String, String> params = Map.of(INP_PROCESSED, PROCESSED_PLACEHOLDER);

        Map<String, String> result = LegacyProcessResolver.withManualProcessId(fieldWithProcess(UIPATTERN_STANDARD),
                params);

        assertFalse(result.containsKey(LegacyProcessResolver.INP_PROCESS_ID));
        assertEquals(params, result);
    }
}
