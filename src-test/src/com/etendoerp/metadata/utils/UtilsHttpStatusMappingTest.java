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
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBSecurityException;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Tests for {@link Utils#getHttpStatusFor(Throwable)} covering all exception types registered
 * in the exception-to-HTTP-status map, and for {@link Utils#writeJsonResponse} which is not
 * covered by {@link UtilsRequestResponseTest}.
 * <p>
 * The existing {@link UtilsExceptionJsonTest} tests AuthenticationException and generic
 * exceptions. This class fills the gap for the custom metadata exceptions and OBSecurityException.
 */
@ExtendWith(MockitoExtension.class)
class UtilsHttpStatusMappingTest {

  private static final String UTF_8 = "UTF-8";

  // ─── getHttpStatusFor ────────────────────────────────────────────────────────

  /**
   * {@link UnauthorizedException} must map to HTTP 401 Unauthorized.
   */
  @Test
  void getHttpStatusForUnauthorizedExceptionReturns401() {
    assertEquals(HttpStatus.SC_UNAUTHORIZED, Utils.getHttpStatusFor(new UnauthorizedException("test")));
  }

  /**
   * {@link UnauthorizedException} with default constructor must also map to HTTP 401.
   */
  @Test
  void getHttpStatusForUnauthorizedExceptionDefaultConstructorReturns401() {
    assertEquals(HttpStatus.SC_UNAUTHORIZED, Utils.getHttpStatusFor(new UnauthorizedException()));
  }

  /**
   * {@link OBSecurityException} must map to HTTP 401 Unauthorized.
   */
  @Test
  void getHttpStatusForOBSecurityExceptionReturns401() {
    assertEquals(HttpStatus.SC_UNAUTHORIZED, Utils.getHttpStatusFor(new OBSecurityException("security error")));
  }

  /**
   * {@link MethodNotAllowedException} must map to HTTP 405 Method Not Allowed.
   */
  @Test
  void getHttpStatusForMethodNotAllowedExceptionReturns405() {
    assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED,
        Utils.getHttpStatusFor(new MethodNotAllowedException("not allowed")));
  }

  /**
   * {@link MethodNotAllowedException} with default constructor must also map to HTTP 405.
   */
  @Test
  void getHttpStatusForMethodNotAllowedExceptionDefaultConstructorReturns405() {
    assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, Utils.getHttpStatusFor(new MethodNotAllowedException()));
  }

  /**
   * {@link UnprocessableContentException} must map to HTTP 422 Unprocessable Entity.
   */
  @Test
  void getHttpStatusForUnprocessableContentExceptionReturns422() {
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY,
        Utils.getHttpStatusFor(new UnprocessableContentException("bad content")));
  }

  /**
   * {@link UnprocessableContentException} with default constructor must also map to HTTP 422.
   */
  @Test
  void getHttpStatusForUnprocessableContentExceptionDefaultConstructorReturns422() {
    assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY,
        Utils.getHttpStatusFor(new UnprocessableContentException()));
  }

  /**
   * {@link NotFoundException} must map to HTTP 404 Not Found.
   */
  @Test
  void getHttpStatusForNotFoundExceptionReturns404() {
    assertEquals(HttpStatus.SC_NOT_FOUND, Utils.getHttpStatusFor(new NotFoundException("not found")));
  }

  /**
   * {@link NotFoundException} with default constructor must also map to HTTP 404.
   */
  @Test
  void getHttpStatusForNotFoundExceptionDefaultConstructorReturns404() {
    assertEquals(HttpStatus.SC_NOT_FOUND, Utils.getHttpStatusFor(new NotFoundException()));
  }

  /**
   * An unmapped exception type must default to HTTP 500 Internal Server Error.
   */
  @Test
  void getHttpStatusForUnmappedExceptionReturns500() {
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR,
        Utils.getHttpStatusFor(new IllegalStateException("state error")));
  }

  // ─── writeJsonResponse ───────────────────────────────────────────────────────

  /**
   * {@link Utils#writeJsonResponse} must set the status code, content type, charset, and
   * write the provided JSON body to the response writer.
   */
  @Test
  void writeJsonResponseSetsStatusContentTypeCharsetAndBody() throws IOException {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(printWriter);

    String jsonContent = "{\"key\":\"value\"}";
    Utils.writeJsonResponse(mockResponse, 200, jsonContent);

    verify(mockResponse).setStatus(200);
    verify(mockResponse).setContentType("application/json");
    verify(mockResponse).setCharacterEncoding(UTF_8);
    assertEquals(jsonContent, stringWriter.toString());
  }

  /**
   * {@link Utils#writeJsonResponse} with a 404 status code must set that status on the response.
   */
  @Test
  void writeJsonResponseWith404StatusSetsCorrectStatus() throws IOException {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    PrintWriter mockWriter = mock(PrintWriter.class);
    when(mockResponse.getWriter()).thenReturn(mockWriter);

    Utils.writeJsonResponse(mockResponse, 404, "{\"error\":\"not found\"}");

    verify(mockResponse).setStatus(404);
    verify(mockResponse).setContentType("application/json");
    verify(mockResponse).setCharacterEncoding(UTF_8);
  }

  /**
   * {@link Utils#writeJsonResponse} with a 500 status code must propagate the status correctly.
   */
  @Test
  void writeJsonResponseWith500StatusSetsCorrectStatus() throws IOException {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    PrintWriter mockWriter = mock(PrintWriter.class);
    when(mockResponse.getWriter()).thenReturn(mockWriter);

    Utils.writeJsonResponse(mockResponse, 500, "{\"error\":\"internal error\"}");

    verify(mockResponse).setStatus(500);
    verify(mockResponse).setContentType("application/json");
    verify(mockResponse).setCharacterEncoding(UTF_8);
  }

  /**
   * {@link Utils#writeJsonResponse} with an empty body must write an empty string.
   */
  @Test
  void writeJsonResponseWithEmptyBodyWritesEmptyString() throws IOException {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(mockResponse.getWriter()).thenReturn(printWriter);

    Utils.writeJsonResponse(mockResponse, 200, "");

    assertEquals("", stringWriter.toString());
  }

  /**
   * The writer's {@code flush} method must be called after writing so that the response
   * is committed to the client.
   */
  @Test
  void writeJsonResponseFlushesWriter() throws IOException {
    HttpServletResponse mockResponse = mock(HttpServletResponse.class);
    PrintWriter mockWriter = mock(PrintWriter.class);
    when(mockResponse.getWriter()).thenReturn(mockWriter);

    Utils.writeJsonResponse(mockResponse, 200, "{\"ok\":true}");

    verify(mockWriter).flush();
  }
}
