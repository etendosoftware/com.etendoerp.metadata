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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Utils class request/response handling methods.
 */
@ExtendWith(MockitoExtension.class)
class UtilsRequestResponseTest {

  /**
   * @throws Exception if request processing fails
   */
  @Test
  void getRequestDataWithInvalidJsonReturnsEmptyJson() throws Exception {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    BufferedReader mockReader = mock(BufferedReader.class);
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    when(mockReader.readLine()).thenReturn("invalid json {");
    when(mockReader.readLine()).thenReturn(null);
    
    String result = Utils.getRequestData(mockRequest);
    
    assertEquals("{}", result);
  }

  /**
   * @throws Exception if request processing fails
   */
  @Test
  void getRequestDataWithIOExceptionReturnsEmptyJson() throws Exception {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    
    when(mockRequest.getReader()).thenThrow(new IOException("Test IO Exception"));
    
    String result = Utils.getRequestData(mockRequest);
    
    assertEquals("{}", result);
  }

  /**
   * @throws Exception if request processing fails
   */
  @Test
  void readRequestBodyWithValidRequestReturnsBodyContent() throws Exception {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    String testContent = "Test request body content";
    BufferedReader mockReader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(testContent.getBytes())));
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    
    String result = Utils.readRequestBody(mockRequest);
    
    assertEquals(testContent, result);
  }

  /**
   * @throws Exception if request processing fails
   */
  @Test
  void readRequestBodyWithEmptyRequestReturnsEmptyString() throws IOException {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    BufferedReader mockReader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream("".getBytes())));
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    
    String result = Utils.readRequestBody(mockRequest);
    
    assertEquals("", result);
  }

  /**
   * @throws Exception if request processing fails
   */
  @Test
  void readRequestBodyWithMultilineContentReturnsFullContent() throws Exception {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    String testContent = "Line 1\nLine 2\nLine 3";
    BufferedReader mockReader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(testContent.getBytes())));
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    
    String result = Utils.readRequestBody(mockRequest);
    
    assertEquals(testContent, result);
  }

  /**
   * @throws Exception if response writing fails
   */
  @Test
  void writeJsonErrorResponseWithValidParametersWritesErrorResponse() throws Exception {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    
    when(mockResponse.getWriter()).thenReturn(printWriter);
    
    Utils.writeJsonErrorResponse(mockResponse, 400, "Test error message");
    
    verify(mockResponse).setStatus(400);
    verify(mockResponse).setContentType("application/json");
    verify(mockResponse).setCharacterEncoding("UTF-8");
    
    String output = stringWriter.toString();
    assertTrue(output.contains("Test error message"));
    assertTrue(output.contains("\"success\":false"));
  }

  /**
   * @throws Exception if response writing fails
   */
  @Test
  void writeJsonErrorResponseWithJsonExceptionWritesFallbackResponse() throws Exception {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    PrintWriter mockWriter = mock(PrintWriter.class);
    
    when(mockResponse.getWriter()).thenReturn(mockWriter);
    
    // Test with null message to potentially cause JSON issues
    Utils.writeJsonErrorResponse(mockResponse, 500, null);
    
    verify(mockResponse).setStatus(500);
    verify(mockResponse).setContentType("application/json");
    verify(mockResponse).setCharacterEncoding("UTF-8");
    verify(mockWriter).write(anyString());
  }

  /**
   * @throws IOException if request reading fails
   */
  @Test
  void readRequestBodyWhenIOExceptionOccursRethrowsException() throws IOException {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    
    when(mockRequest.getReader()).thenThrow(new IOException("Test IO Exception"));
    
    assertThrows(IOException.class, () -> Utils.readRequestBody(mockRequest));
  }

  /**
   * Test para readRequestBody con request válido
   */
  @Test
  void readRequestBodyWithValidRequestReturnsBody() throws IOException {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    String testBody = "Test body content";
    BufferedReader mockReader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(testBody.getBytes())));
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    
    String result = Utils.readRequestBody(mockRequest);
    assertEquals(testBody, result);
  }

  /**
   * Test para readRequestBody cuando IOException ocurre
   */
  @Test
  void readRequestBodyWithIOExceptionThrowsException() throws IOException {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getReader()).thenThrow(new IOException("IO Error"));
    
    assertThrows(IOException.class, () -> Utils.readRequestBody(mockRequest));
  }

  /**
   * Test para validación de encoding en diferentes charsets
   */
  @Test
  void readRequestBodyWithDifferentEncodingsHandlesCorrectly() throws IOException {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    String testContent = "Contenido con acentos: áéíóú";
    BufferedReader mockReader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(testContent.getBytes("UTF-8")), "UTF-8"));
    
    when(mockRequest.getReader()).thenReturn(mockReader);
    
    String result = Utils.readRequestBody(mockRequest);
    assertEquals(testContent, result);
  }
}