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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Edge case tests for {@link LegacyUtils} covering behaviors not addressed in
 * {@link LegacyUtilsTest} and {@link LegacyUtilsExecutionTest}.
 * <p>
 * Specifically covers:
 * <ul>
 *   <li>The GUID-based legacy process ID.</li>
 *   <li>Null-input behavior for {@code isLegacyPath} and {@code isMutableSessionAttribute},
 *       which use {@code Set.of()} internally and therefore throw {@link NullPointerException}
 *       on {@code contains(null)}.</li>
 *   <li>Non-legacy paths that are defined in {@link LegacyPaths} but not in the set.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LegacyUtilsEdgeCasesTest {

  private static final String GUID_LEGACY_PROCESS_ID = "7C541AC0C767FDD7E040007F01016B4D";

  /**
   * Verifies that the GUID-based legacy process ID is recognised as a legacy process.
   * This ID is part of {@code LEGACY_PROCESS_IDS} but is not covered by the numeric-only
   * tests in the existing test classes.
   */
  @Test
  void isLegacyProcessWithGuidIdReturnsTrue() {
    assertTrue(LegacyUtils.isLegacyProcess(GUID_LEGACY_PROCESS_ID));
  }

  /**
   * Documents that {@code isLegacyPath(null)} throws {@link NullPointerException} because
   * the backing {@code Set.of()} rejects {@code null} lookups.
   * Callers must guard against null paths before invoking this method.
   */
  @Test
  void isLegacyPathWithNullThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> LegacyUtils.isLegacyPath(null));
  }

  /**
   * Documents that {@code isMutableSessionAttribute(null)} throws {@link NullPointerException}
   * because the backing {@code Set.of()} rejects {@code null} lookups.
   */
  @Test
  void isMutableSessionAttributeWithNullThrowsNullPointerException() {
    assertThrows(NullPointerException.class, () -> LegacyUtils.isMutableSessionAttribute(null));
  }

  /**
   * Verifies that an empty string is not a legacy path.
   */
  @Test
  void isLegacyPathWithEmptyStringReturnsFalse() {
    assertFalse(LegacyUtils.isLegacyPath(""));
  }

  /**
   * Verifies that an empty string is not a mutable session attribute.
   */
  @Test
  void isMutableSessionAttributeWithEmptyStringReturnsFalse() {
    assertFalse(LegacyUtils.isMutableSessionAttribute(""));
  }

  /**
   * Verifies that paths defined in {@link LegacyPaths} but NOT included in
   * {@code LEGACY_PATHS} set return false.
   */
  @Test
  void isLegacyPathWithUnregisteredLegacyConstantsReturnsFalse() {
    assertFalse(LegacyUtils.isLegacyPath(LegacyPaths.ABOUT_MODAL));
    assertFalse(LegacyUtils.isLegacyPath(LegacyPaths.MANUAL_PROCESS));
    assertFalse(LegacyUtils.isLegacyPath("/unknown/path.html"));
  }

  /**
   * Verifies that attribute values similar to the registered mutable attribute return false
   * (case sensitivity and partial matches).
   */
  @Test
  void isMutableSessionAttributeWithSimilarAttributesReturnsFalse() {
    assertFalse(LegacyUtils.isMutableSessionAttribute("143|C_INVOICE_ID"));
    assertFalse(LegacyUtils.isMutableSessionAttribute("143|C_ORDER"));
    assertFalse(LegacyUtils.isMutableSessionAttribute("144|C_ORDER_ID"));
    assertFalse(LegacyUtils.isMutableSessionAttribute("143|c_order_id")); // wrong case
  }

  /**
   * Verifies that all five known legacy process IDs are recognised, including the GUID.
   */
  @Test
  void isLegacyProcessWithAllKnownLegacyIdsReturnTrue() {
    assertTrue(LegacyUtils.isLegacyProcess("3663"));
    assertTrue(LegacyUtils.isLegacyProcess("4242"));
    assertTrue(LegacyUtils.isLegacyProcess("3670"));
    assertTrue(LegacyUtils.isLegacyProcess("4248"));
    assertTrue(LegacyUtils.isLegacyProcess(GUID_LEGACY_PROCESS_ID));
  }
}
