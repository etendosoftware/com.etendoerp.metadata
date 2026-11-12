/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may not use this file except in compliance with the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
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
 * Unit tests for the {@code deletableRecords} flag emitted by {@link TabBuilder}. The flag mirrors
 * {@code AD_Table.isDeletableRecords}, which Classic uses in {@code OBViewTab.getDeleteableTable} to disable
 * the Delete toolbar button for tables that do not allow deleting.
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderDeletableRecordsTest extends TabBuilderTestBase {

    private static final String DELETABLE_RECORDS_KEY = "deletableRecords";

    /**
     * Provides the {@code Table.isDeletableRecords()} values and the flag expected for each one.
     * A {@code null} value is treated as deletable.
     *
     * @return the (isDeletableRecords, expected flag) pairs
     */
    static Stream<Arguments> deletableCases() {
        return Stream.of(
                Arguments.of(Boolean.TRUE, true),
                Arguments.of(Boolean.FALSE, false),
                Arguments.of(null, true));
    }

    /**
     * Tests that {@code deletableRecords} is always emitted, and is {@code false} only when the tab's table
     * explicitly does not allow deleting.
     *
     * @param isDeletableRecords the value returned by {@code Table.isDeletableRecords()}
     * @param expected           the flag expected in the tab JSON
     * @throws Exception when mock setup fails
     */
    @ParameterizedTest
    @MethodSource("deletableCases")
    void toJSONEmitsDeletableRecordsFlag(Boolean isDeletableRecords, boolean expected) throws Exception {
        TestContext ctx = setupTestContext();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
        when(ctx.table.isDeletableRecords()).thenReturn(isDeletableRecords);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                assertTrue(result.has(DELETABLE_RECORDS_KEY), "deletableRecords should be present in JSON");
                assertEquals(expected, result.getBoolean(DELETABLE_RECORDS_KEY),
                        "deletableRecords should mirror Table.isDeletableRecords()");
            } catch (JSONException e) {
                fail("JSON exception: " + e.getMessage());
            }
        });
    }
}
