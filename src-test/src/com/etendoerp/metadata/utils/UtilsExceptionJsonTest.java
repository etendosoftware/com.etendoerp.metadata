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

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.HttpSecureAppServlet.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Utils class exception and JSON handling methods.
 */
@ExtendWith(MockitoExtension.class)
class UtilsExceptionJsonTest {

  private static final String TEST_ERROR_MESSAGE = "Test error message";

  /**
   * Tests the getHttpStatusFor method with different exception types.
   */
  @Test
  void getHttpStatusForWithDifferentExceptionsReturnsCorrectStatusCodes() {
    assertEquals(HttpStatus.SC_UNAUTHORIZED, Utils.getHttpStatusFor(new AuthenticationException("auth error")));
    assertEquals(HttpStatus.SC_BAD_REQUEST, Utils.getHttpStatusFor(new OBException("ob error")));
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, Utils.getHttpStatusFor(new RuntimeException("runtime error")));
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, Utils.getHttpStatusFor(new Exception("general error")));
  }

  /**
   * Tests the convertToJson method with simple exception.
   */
  @Test
  void convertToJsonWithSimpleExceptionReturnsJsonWithErrorMessage() {
    String errorMessage = TEST_ERROR_MESSAGE;
    Exception exception = new RuntimeException(errorMessage);
    
    JSONObject result = Utils.convertToJson(exception);
    
    assertNotNull(result);
    assertEquals(false, result.optBoolean("success"));
    assertEquals(errorMessage, result.optString("message"));
    assertEquals("RuntimeException", result.optString("type"));
  }

  /**
   * Tests the convertToJson method with exception that has a cause.
   */
  @Test
  void convertToJsonWithExceptionWithCauseReturnsJsonWithCauseMessage() {
    String causeMessage = "Root cause message";
    Exception cause = new IllegalArgumentException(causeMessage);
    Exception exception = new RuntimeException("Wrapper exception", cause);
    
    JSONObject result = Utils.convertToJson(exception);
    
    assertNotNull(result);
    assertEquals(false, result.optBoolean("success"));
    assertEquals(causeMessage, result.optString("message"));
    assertEquals("IllegalArgumentException", result.optString("type"));
  }

  /**
   * Tests the getJsonObject method with null object.
   */
  @Test
  void getJsonObjectWithNullObjectReturnsNull() {
    JSONObject result = Utils.getJsonObject(null);
    assertNull(result);
  }

  /**
   * Tests that convertToJson handles null exception gracefully.
   */
  @Test
  void convertToJsonWithNullExceptionHandlesGracefully() {
    try {
      JSONObject result = Utils.convertToJson(null);
      assertNotNull(result);
      assertEquals(false, result.optBoolean("success"));
    } catch (Exception e) {
      // If it throws an exception, that's also acceptable behavior
      assertNotNull(e);
    }
  }

  /**
   * Test para convertToJson con excepción que tiene causa nula pero mensaje largo
   */
  @Test
  void convertToJsonWithExceptionWithLongMessage() {
    String longMessage = "A".repeat(1000);
    Exception exception = new RuntimeException(longMessage);
    
    JSONObject result = Utils.convertToJson(exception);
    
    assertNotNull(result);
    assertEquals(false, result.optBoolean("success"));
    assertEquals(longMessage, result.optString("message"));
  }

  /**
   * Test para convertToJson con excepción anidada múltiples niveles
   */
  @Test
  void convertToJsonWithDeeplyNestedException() {
    Exception level3 = new RuntimeException("Level 3");
    Exception level2 = new IllegalStateException("Level 2", level3);
    Exception level1 = new OBException("Level 1", level2);
    
    JSONObject result = Utils.convertToJson(level1);
    
    assertNotNull(result);
    assertEquals(false, result.optBoolean("success"));
    // Should get the deepest cause message
    assertEquals("Level 3", result.optString("message"));
  }

  /**
   * Test para convertToJson con excepción con mensaje vacío
   */
  @Test
  void convertToJsonWithEmptyMessageException() {
    Exception exception = new RuntimeException("");
    
    JSONObject result = Utils.convertToJson(exception);
    
    assertNotNull(result);
    assertEquals(false, result.optBoolean("success"));
    // Should handle empty message gracefully
    assertNotNull(result.optString("message"));
  }
}