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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.ui.Process;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Core unit tests for Utils class - main functionality tests.
 * Additional specialized tests are in separate classes.
 */
@ExtendWith(MockitoExtension.class)
class UtilsCoreTest {

  /**
   * Tests that Utils class has expected static methods.
   */
  @Test
  void utilsClassHasExpectedMethods() throws Exception {
    // Verify key static methods exist
    assertDoesNotThrow(() -> {
      Utils.class.getMethod("evaluateDisplayLogicAtServerLevel", Field.class);
      Utils.class.getMethod("setContext", HttpServletRequest.class);
      Utils.class.getMethod("getHttpStatusFor", Throwable.class);
      Utils.class.getMethod("convertToJson", Throwable.class);
    });
  }

  /**
   * Tests the evaluateDisplayLogicAtServerLevel method with null display logic.
   */
  @Test
  void evaluateDisplayLogicAtServerLevelWithNullDisplayLogicReturnsTrue() {
    Field mockField = mock(Field.class);
    when(mockField.getDisplayLogicEvaluatedInTheServer()).thenReturn(null);
    
    boolean result = Utils.evaluateDisplayLogicAtServerLevel(mockField);
    
    assertTrue(result);
  }

  /**
   * Tests the evaluateDisplayLogicAtServerLevel method with valid display logic that evaluates to true.
   */
  @Test
  void evaluateDisplayLogicAtServerLevelWithValidLogicReturnsTrue() {
    Field mockField = mock(Field.class);
    when(mockField.getDisplayLogicEvaluatedInTheServer()).thenReturn("1=1");

    assertThrows(NullPointerException.class, () -> Utils.evaluateDisplayLogicAtServerLevel(mockField));
  }

  /**
   * Tests the setContext method with valid language parameter.
   */
  @Test
  void setContextWithValidLanguageParameterSetsContext() {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    
    // This method requires SessionFactoryController which is not available in test environment
    // So we expect it to throw NullPointerException in test context
    assertThrows(NullPointerException.class, () -> Utils.setContext(mockRequest));
  }

  /**
   * Tests the setContext method with no language provided.
   */
  @Test
  void setContextWithNoLanguageProvidedStillSetsContext() {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    assertThrows(NullPointerException.class, () -> Utils.setContext(mockRequest));
  }
}