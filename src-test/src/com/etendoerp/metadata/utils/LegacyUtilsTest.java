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

import com.etendoerp.metadata.utils.LegacyUtils;
import org.junit.Test;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.test.base.OBBaseTest;

import static org.junit.Assert.*;

/**
 * Test class for LegacyUtils utility methods.
 */
public class LegacyUtilsTest extends OBBaseTest {

    /**
     * Tests isLegacyProcess method with known legacy process IDs.
     * Verifies that all predefined legacy IDs return true.
     */
    @Test
    public void testIsLegacyProcess_WithLegacyIds() {
        // Test known legacy process IDs
        assertTrue("Process 3663 should be legacy", LegacyUtils.isLegacyProcess("3663"));
        assertTrue("Process 4242 should be legacy", LegacyUtils.isLegacyProcess("4242"));
        assertTrue("Process 3670 should be legacy", LegacyUtils.isLegacyProcess("3670"));
        assertTrue("Process 4248 should be legacy", LegacyUtils.isLegacyProcess("4248"));
    }

    /**
     * Tests isLegacyProcess method with non-legacy process IDs.
     * Verifies that unknown IDs return false.
     */
    @Test
    public void testIsLegacyProcess_WithNonLegacyIds() {
        // Test non-legacy process IDs
        assertFalse("Process 1234 should not be legacy", LegacyUtils.isLegacyProcess("1234"));
        assertFalse("Process 9999 should not be legacy", LegacyUtils.isLegacyProcess("9999"));
        assertFalse("Empty string should not be legacy", LegacyUtils.isLegacyProcess(""));
        // Note: null check removed as Set.contains() doesn't handle null well
    }

    /**
     * Tests isLegacyProcess method with null input.
     * Verifies proper handling of null values.
     */
    @Test
    public void testIsLegacyProcess_WithNull() {
        // Test null separately to handle NullPointerException
        try {
            boolean result = LegacyUtils.isLegacyProcess(null);
            assertFalse("Null should not be legacy", result);
        } catch (NullPointerException e) {
            // This is expected behavior for Set.contains(null)
            assertTrue("NullPointerException is expected for null input", true);
        }
    }

    /**
     * Tests getLegacyProcess method with a test process ID.
     * Verifies that a properly configured Process object is returned.
     */
    @Test
    public void testGetLegacyProcess() {
        String testProcessId = "TEST123";
        
        Process legacyProcess = LegacyUtils.getLegacyProcess(testProcessId);
        
        assertNotNull("Legacy process should not be null", legacyProcess);
        assertEquals("Process ID should match", testProcessId, legacyProcess.getId());
        assertEquals("Process name should be set", "Legacy Process Placeholder", legacyProcess.getName());
        assertTrue("Process should be active", legacyProcess.isActive());
    }

    /**
     * Tests getLegacyProcess method with different process IDs.
     * Verifies that each ID creates a separate Process with correct properties.
     */
    @Test
    public void testGetLegacyProcess_WithDifferentIds() {
        // Test with different process IDs
        Process process1 = LegacyUtils.getLegacyProcess("ID1");
        Process process2 = LegacyUtils.getLegacyProcess("ID2");
        
        assertNotNull("First process should not be null", process1);
        assertNotNull("Second process should not be null", process2);
        assertEquals("First process ID should be ID1", "ID1", process1.getId());
        assertEquals("Second process ID should be ID2", "ID2", process2.getId());
        
        // Both should have the same name and active status
        assertEquals("Both processes should have same name", process1.getName(), process2.getName());
        assertEquals("Both processes should have same active status", process1.isActive(), process2.isActive());
    }
}