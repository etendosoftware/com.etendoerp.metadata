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

package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the auxiliary helpers in {@link LegacyUtils}. Legacy-process detection
 * itself is covered by
 * {@code com.etendoerp.metadata.builders.LegacyProcessResolverTest}.
 */
class LegacyUtilsExecutionTest {

    @Test
    void isLegacyPathRecognisesUsedByLink() {
        assertTrue(LegacyUtils.isLegacyPath(LegacyPaths.USED_BY_LINK));
    }

    @Test
    void isLegacyPathRejectsUnknownPaths() {
        assertFalse(LegacyUtils.isLegacyPath("/some/other/path.html"));
        assertFalse(LegacyUtils.isLegacyPath(""));
    }

    @Test
    void isMutableSessionAttributeRecognisesKnownEntries() {
        assertTrue(LegacyUtils.isMutableSessionAttribute("143|C_ORDER_ID"));
        assertTrue(LegacyUtils.isMutableSessionAttribute("CREATEFROM|TABID"));
    }

    @Test
    void isMutableSessionAttributeRejectsUnknownEntries() {
        assertFalse(LegacyUtils.isMutableSessionAttribute("999|C_ORDER_ID"));
        assertFalse(LegacyUtils.isMutableSessionAttribute(""));
    }
}
