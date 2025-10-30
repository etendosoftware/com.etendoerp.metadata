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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for LegacyUtils that execute real code logic without complex framework dependencies.
 */
class LegacyUtilsExecutionTest {

    /**
     * Tests isLegacyProcess method with known legacy process IDs.
     */
    @Test
    void isLegacyProcessWithKnownLegacyIdsReturnsTrue() {
        assertTrue(LegacyUtils.isLegacyProcess("3663"));
        assertTrue(LegacyUtils.isLegacyProcess("4242"));
        assertTrue(LegacyUtils.isLegacyProcess("3670"));
        assertTrue(LegacyUtils.isLegacyProcess("4248"));
    }

    /**
     * Tests isLegacyProcess method with non-legacy process IDs.
     */
    @Test
    void isLegacyProcessWithNonLegacyIdsReturnsFalse() {
        assertFalse(LegacyUtils.isLegacyProcess("1234"));
        assertFalse(LegacyUtils.isLegacyProcess("9999"));
        assertFalse(LegacyUtils.isLegacyProcess("0"));
        assertFalse(LegacyUtils.isLegacyProcess("5555"));
    }

    /**
     * Tests isLegacyProcess method with null input throws NPE.
     */
    @Test
    void isLegacyProcessWithNullInputThrowsNPE() {
        assertThrows(NullPointerException.class, () -> {
            LegacyUtils.isLegacyProcess(null);
        });
    }

    /**
     * Tests isLegacyProcess method with empty string input.
     */
    @Test
    void isLegacyProcessWithEmptyStringReturnsFalse() {
        assertFalse(LegacyUtils.isLegacyProcess(""));
    }

    /**
     * Tests isLegacyProcess method with whitespace input.
     */
    @Test
    void isLegacyProcessWithWhitespaceReturnsFalse() {
        assertFalse(LegacyUtils.isLegacyProcess(" "));
        assertFalse(LegacyUtils.isLegacyProcess("\t"));
        assertFalse(LegacyUtils.isLegacyProcess("\n"));
    }

    /**
     * Tests isLegacyProcess method case sensitivity.
     */
    @Test
    void isLegacyProcessIsCaseSensitive() {
        assertTrue(LegacyUtils.isLegacyProcess("3663"));
        assertFalse(LegacyUtils.isLegacyProcess("3663 "));
        assertFalse(LegacyUtils.isLegacyProcess(" 3663"));
    }

    /**
     * Tests isLegacyProcess method with numeric strings that are close but not exact matches.
     */
    @Test
    void isLegacyProcessWithSimilarButNotExactIdsReturnsFalse() {
        assertFalse(LegacyUtils.isLegacyProcess("3662")); // One less than 3663
        assertFalse(LegacyUtils.isLegacyProcess("3664")); // One more than 3663
        assertFalse(LegacyUtils.isLegacyProcess("4241")); // One less than 4242
        assertFalse(LegacyUtils.isLegacyProcess("4243")); // One more than 4242
    }

    /**
     * Tests isLegacyProcess method with very long strings.
     */
    @Test
    void isLegacyProcessWithLongStringsReturnsFalse() {
        assertFalse(LegacyUtils.isLegacyProcess("366333333333333333333"));
        assertFalse(LegacyUtils.isLegacyProcess("4242424242424242424242"));
    }
}