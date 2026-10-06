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
package com.etendoerp.metadata.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

/**
 * Unit tests for {@link LegacyProcessServlet#isDataGridCommand(String)}, which decides whether a
 * legacy response is served as DataGrid XML (e.g. the Audit Trail history and deleted-records
 * grids) instead of HTML.
 */
class LegacyProcessServletDataGridCommandTest {

    /**
     * Tests that the plain and suffixed DataGrid commands are recognized, while page and
     * process commands are not.
     *
     * @param command  the {@code Command} request parameter
     * @param expected whether it must be treated as a DataGrid command
     */
    @ParameterizedTest
    @CsvSource({
            "STRUCTURE, true",
            "DATA, true",
            "STRUCTURE_HISTORY, true",
            "DATA_HISTORY, true",
            "STRUCTURE_DELETED, true",
            "DATA_DELETED, true",
            "POPUP_HISTORY, false",
            "DEFAULT, false",
            "DATAX, false",
            "STRUCTURED, false",
            "'', false"
    })
    void isDataGridCommandRecognizesGridCommands(String command, boolean expected) {
        assertEquals(expected, LegacyProcessServlet.isDataGridCommand(command));
    }

    /**
     * Tests that a missing {@code Command} parameter is not a DataGrid command.
     *
     * @param command a {@code null} command
     */
    @ParameterizedTest
    @NullSource
    void isDataGridCommandRejectsMissingCommand(String command) {
        assertFalse(LegacyProcessServlet.isDataGridCommand(command));
    }
}
