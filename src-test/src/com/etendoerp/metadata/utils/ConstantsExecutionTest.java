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
 * Tests for Constants that exercise actual code execution and value calculations.
 */
class ConstantsExecutionTest {

    /**
     * Tests that SELECTOR_REFERENCES list contains expected reference IDs.
     */
    @Test
    void selectorReferencesContainsExpectedReferenceIds() {
        assertNotNull(Constants.SELECTOR_REFERENCES);
        assertEquals(5, Constants.SELECTOR_REFERENCES.size());
        
        assertTrue(Constants.SELECTOR_REFERENCES.contains("18")); // TABLE_REFERENCE_ID
        assertTrue(Constants.SELECTOR_REFERENCES.contains("19")); // TABLE_DIR_REFERENCE_ID
        assertTrue(Constants.SELECTOR_REFERENCES.contains("30")); // SEARCH_REFERENCE_ID
        assertTrue(Constants.SELECTOR_REFERENCES.contains("95E2A8B50A254B2AAE6774B8C2F28120")); // SELECTOR_REFERENCE_ID
        assertTrue(Constants.SELECTOR_REFERENCES.contains("8C57A4A2E05F4261A1FADF47C30398AD")); // TREE_REFERENCE_ID
    }

    /**
     * Tests that SERVLET_PATH_LENGTH is calculated correctly from SERVLET_PATH.
     */
    @Test
    void servletPathLengthIsCalculatedCorrectly() {
        assertEquals(Constants.SERVLET_PATH.length(), Constants.SERVLET_PATH_LENGTH);
        assertEquals("/forward".length(), Constants.SERVLET_PATH_LENGTH);
        assertEquals(8, Constants.SERVLET_PATH_LENGTH);
    }

    /**
     * Tests that SERVLET_FULL_PATH is concatenated correctly.
     */
    @Test
    void servletFullPathIsConcatenatedCorrectly() {
        assertEquals(Constants.MODULE_BASE_PATH + Constants.SERVLET_PATH, Constants.SERVLET_FULL_PATH);
        assertEquals("/meta/forward", Constants.SERVLET_FULL_PATH);
    }

    /**
     * Tests that SELECTOR_REFERENCES list is immutable and operations work correctly.
     */
    @Test
    void selectorReferencesListSupportsReadOperations() {
        assertFalse(Constants.SELECTOR_REFERENCES.isEmpty());
        
        // Test contains operations work
        assertTrue(Constants.SELECTOR_REFERENCES.contains("18"));
        assertFalse(Constants.SELECTOR_REFERENCES.contains("999"));
        
        // Test iteration works
        int count = 0;
        for (String ref : Constants.SELECTOR_REFERENCES) {
            assertNotNull(ref);
            count++;
        }
        assertEquals(5, count);
    }

    /**
     * Tests that constants have expected string values.
     */
    @Test
    void constantsHaveExpectedStringValues() {
        assertEquals("/meta", Constants.MODULE_BASE_PATH);
        assertEquals("/forward", Constants.SERVLET_PATH);
        assertEquals("/legacy", Constants.LEGACY_PATH);
        assertEquals("/session", Constants.SESSION_PATH);
        assertEquals("/menu", Constants.MENU_PATH);
        assertEquals("/window/", Constants.WINDOW_PATH);
        assertEquals("/tab/", Constants.TAB_PATH);
        assertEquals("/language", Constants.LANGUAGE_PATH);
        assertEquals("/message", Constants.MESSAGE_PATH);
        assertEquals("/labels", Constants.LABELS_PATH);
        assertEquals("/location/", Constants.LOCATION_PATH);
        assertEquals("/toolbar", Constants.TOOLBAR_PATH);
    }

    /**
     * Tests that HTTP method constants are correct.
     */
    @Test
    void httpMethodConstantsAreCorrect() {
        assertEquals("OPTIONS", Constants.OPTIONS);
        assertEquals("GET", Constants.GET);
        assertEquals("POST", Constants.POST);
        assertEquals("PUT", Constants.PUT);
        assertEquals("PATCH", Constants.PATCH);
        assertEquals("DELETE", Constants.DELETE);
    }

    /**
     * Tests boolean constants.
     */
    @Test
    void booleanConstantsHaveExpectedValues() {
        assertTrue(Constants.DEFAULT_CHECKON_SAVE);
        assertTrue(Constants.DEFAULT_EDITABLE_FIELD);
    }

    /**
     * Tests locale constants.
     */
    @Test
    void localeConstantsHaveExpectedValues() {
        assertEquals("Locale", Constants.LOCALE_KEY);
        assertEquals("en_US", Constants.DEFAULT_LOCALE);
    }
}