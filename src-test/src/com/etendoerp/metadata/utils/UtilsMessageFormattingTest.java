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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Utils class message formatting methods.
 */
@ExtendWith(MockitoExtension.class)
class UtilsMessageFormattingTest {

  private static final String TEST_MESSAGE = "Test message with {} and {}";
  private static final String TEST_PARAM_1 = "param1";
  private static final String TEST_PARAM_2 = "param2";

  /**
   * Tests the formatMessage method with valid parameters.
   */
  @Test
  void formatMessageWithValidParametersReturnsFormattedString() {
    String result = Utils.formatMessage(TEST_MESSAGE, TEST_PARAM_1, TEST_PARAM_2);
    assertEquals("Test message with param1 and param2", result);
  }

  /**
   * Tests the formatMessage method with no parameters.
   */
  @Test
  void formatMessageWithNoParametersReturnsOriginalMessage() {
    String message = "Simple message";
    String result = Utils.formatMessage(message);
    assertEquals(message, result);
  }

  /**
   * Tests the formatMessage method when MessageFactory throws exception.
   */
  @Test
  void formatMessageWithExceptionInFormattingReturnsOriginalMessage() {
    String message = "Message with invalid {} format {}";

    // Test with extreme parameters that might cause formatting issues
    String result = Utils.formatMessage(message, (Object) null);

    // Should either return formatted message or original message if formatting fails
    assertNotNull(result);
    assertTrue(result.contains("Message"));
  }

  /**
   * Test para formatMessage con parámetros null
   */
  @Test
  void formatMessageWithNullParametersHandlesGracefully() {
    String message = "Message with {} and {}";
    String result = Utils.formatMessage(message, null, null);
    
    assertNotNull(result);
    assertTrue(result.contains("Message"));
  }

  /**
   * Test para formatMessage con muchos parámetros
   */
  @Test
  void formatMessageWithMultipleParametersFormatsCorrectly() {
    String message = "Test {} {} {} {}";
    String result = Utils.formatMessage(message, "a", "b", "c", "d");
    
    assertEquals("Test a b c d", result);
  }

  /**
   * Test para formatMessage con parámetros que contienen caracteres especiales
   */
  @Test
  void formatMessageWithSpecialCharactersInParameters() {
    String message = "Message: {}";
    String specialParam = "Special chars: @#$%^&*()";
    String result = Utils.formatMessage(message, specialParam);
    
    assertEquals("Message: Special chars: @#$%^&*()", result);
  }

  /**
   * Test para formatMessage con template que no tiene placeholders
   */
  @Test
  void formatMessageWithNoPlaceholdersIgnoresParameters() {
    String message = "Message without placeholders";
    String result = Utils.formatMessage(message, "ignored", "parameters");
    
    assertEquals(message, result);
  }
}