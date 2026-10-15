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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code tableFullyAudited} flag emitted by {@link TabBuilder}. The flag
 * mirrors {@code AD_Table.isFullyAudited}, which Classic uses in
 * {@code OBViewTab.getIconButtons} to decide whether the Audit Trail toolbar button is offered.
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderAuditTrailTest extends TabBuilderTestBase {

    private static final String TABLE_FULLY_AUDITED_KEY = "tableFullyAudited";

    /**
     * Provides the {@code Table.isFullyAudited()} values and the flag expected for each one.
     * A {@code null} value is treated as not audited.
     *
     * @return the (isFullyAudited, expected flag) pairs
     */
    static Stream<Arguments> fullyAuditedCases() {
        return Stream.of(
                Arguments.of(Boolean.TRUE, true),
                Arguments.of(Boolean.FALSE, false),
                Arguments.of(null, false));
    }

    /**
     * Tests that {@code tableFullyAudited} is always emitted, and is {@code true} only when the
     * tab's table is marked as Fully Audited.
     *
     * @param isFullyAudited the value returned by {@code Table.isFullyAudited()}
     * @param expected       the flag expected in the tab JSON
     * @throws Exception when mock setup fails
     */
    @ParameterizedTest
    @MethodSource("fullyAuditedCases")
    void toJSONEmitsTableFullyAuditedFlag(Boolean isFullyAudited, boolean expected) throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        when(ctx.table.isFullyAudited()).thenReturn(isFullyAudited);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.has(TABLE_FULLY_AUDITED_KEY), "tableFullyAudited should be present in JSON");
                assertEquals(expected, result.getBoolean(TABLE_FULLY_AUDITED_KEY),
                        "tableFullyAudited should mirror Table.isFullyAudited()");
            } catch (JSONException e) {
                fail("JSON exception: " + e.getMessage());
            }
        });
    }
}
