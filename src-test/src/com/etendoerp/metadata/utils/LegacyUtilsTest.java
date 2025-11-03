package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.ui.Process;

/**
 * Unit tests for {@link LegacyUtils}.
 * Tests all public utility methods of the LegacyUtils class.
 */
@ExtendWith(MockitoExtension.class)
class LegacyUtilsTest {

  /**
   * Tests the isLegacyProcess method for a process ID that exists in the legacy list.
   */
  @Test
  void isLegacyProcessWithExistingIdReturnsTrue() {
    assertTrue(LegacyUtils.isLegacyProcess("3663"));
    assertTrue(LegacyUtils.isLegacyProcess("4242"));
    assertTrue(LegacyUtils.isLegacyProcess("3670"));
    assertTrue(LegacyUtils.isLegacyProcess("4248"));
  }

  /**
   * Tests the isLegacyProcess method for a process ID that does not exist in the legacy list.
   */
  @Test
  void isLegacyProcessWithNonExistingIdReturnsFalse() {
    assertFalse(LegacyUtils.isLegacyProcess("1234"));
    assertFalse(LegacyUtils.isLegacyProcess("9999"));
    assertFalse(LegacyUtils.isLegacyProcess(""));
  }

  @Test
  void isLegacyProcessWithNullReturnsFalseOrNoException() {
    assertDoesNotThrow(() -> {
      boolean result = LegacyUtils.isLegacyProcess(null);
      assertFalse(result, "Null should not be legacy");
    });
  }

  /**
   * Tests the getLegacyProcess method to ensure it creates and populates a Process instance correctly.
   */
  @Test
  void getLegacyProcessCreatesStubbedProcess() {
    String processId = "3663";
    Process mockProcess = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class)).thenReturn(mockProcess);

      Process result = LegacyUtils.getLegacyProcess(processId);

      assertNotNull(result);
      verify(mockProcess).setId(processId);
      verify(mockProcess).setName("Legacy Process Placeholder");
      verify(mockProcess).setActive(true);
    }
  }

  @Test
  void getLegacyProcessReturnsConfiguredProcess() {
    String testProcessId = "TEST123";
    Process mockProcess = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class)).thenReturn(mockProcess);

      Process result = LegacyUtils.getLegacyProcess(testProcessId);

      assertNotNull(result, "Legacy process should not be null");
      verify(mockProcess).setId(testProcessId);
      verify(mockProcess).setName("Legacy Process Placeholder");
      verify(mockProcess).setActive(true);
    }
  }

  @Test
  void getLegacyProcessWithDifferentIdsCreatesDifferentInstances() {
    Process mockProcess1 = mock(Process.class);
    Process mockProcess2 = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class))
              .thenReturn(mockProcess1)
              .thenReturn(mockProcess2);

      Process result1 = LegacyUtils.getLegacyProcess("ID1");
      Process result2 = LegacyUtils.getLegacyProcess("ID2");

      assertNotNull(result1);
      assertNotNull(result2);

      verify(mockProcess1).setId("ID1");
      verify(mockProcess2).setId("ID2");
      verify(mockProcess1).setName("Legacy Process Placeholder");
      verify(mockProcess2).setName("Legacy Process Placeholder");
    }
  }

  /**
   * Tests the isLegacyPath method for a path that exists in the legacy paths set.
   */
  @Test
  void isLegacyPathWithExistingPathReturnsTrue() {
    assertTrue(LegacyUtils.isLegacyPath(LegacyPaths.USED_BY_LINK));
  }

  /**
   * Tests the isLegacyPath method for a path that does not exist in the legacy paths set.
   */
  @Test
  void isLegacyPathWithNonExistingPathReturnsFalse() {
    assertFalse(LegacyUtils.isLegacyPath("/not/legacy/path.html"));
  }

  /**
   * Tests the isMutableSessionAttribute method for an attribute that exists in the mutable session attributes set.
   */
  @Test
  void isMutableSessionAttributeWithExistingAttributeReturnsTrue() {
    assertTrue(LegacyUtils.isMutableSessionAttribute("143|C_ORDER_ID"));
  }

  /**
   * Tests the isMutableSessionAttribute method for an attribute that does not exist in the mutable session attributes set.
   */
  @Test
  void isMutableSessionAttributeWithNonExistingAttributeReturnsFalse() {
    assertFalse(LegacyUtils.isMutableSessionAttribute("143|C_INVOICE_ID"));
  }

  /**
   * Tests that the legacy process IDs, paths, and attributes are not empty or null.
   */
  @Test
  void staticSetsAreInitializedCorrectly() throws Exception {
    // Using reflection to verify that sets are initialized
    var processIdsField = LegacyUtils.class.getDeclaredField("LEGACY_PROCESS_IDS");
    var pathsField = LegacyUtils.class.getDeclaredField("LEGACY_PATHS");
    var attributesField = LegacyUtils.class.getDeclaredField("MUTABLE_SESSION_ATTRIBUTES");

    processIdsField.setAccessible(true);
    pathsField.setAccessible(true);
    attributesField.setAccessible(true);

    Set<?> processIds = (Set<?>) processIdsField.get(null);
    Set<?> paths = (Set<?>) pathsField.get(null);
    Set<?> attributes = (Set<?>) attributesField.get(null);

    assertNotNull(processIds);
    assertNotNull(paths);
    assertNotNull(attributes);

    assertFalse(processIds.isEmpty());
    assertFalse(paths.isEmpty());
    assertFalse(attributes.isEmpty());
  }
}
