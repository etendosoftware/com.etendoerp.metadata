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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link MetadataFilter}. */
@SuppressWarnings("java:S1448")
public class MetadataFilterTest extends WeldBaseTest {

  private MetadataFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;

  private static final String FORWARD_PATH = "forwardPath";
  private static final String ACCEPT_HEADER = "Accept";
  private static final String APPLICATION_JSON = "application/json";
  private static final String TEXT_HTML = "text/html";
  private static final String TEXT_HTML_CHARSET_UTF8 = "text/html; charset=UTF-8";
  private static final String TEST_PATH = "/test";
  private static final String TEST_HTML_PATH = "/test.html";
  private static final String META_API_TEST_URI = "/etendo/meta/api/test";
  private static final String META_API_PREFIX = "/etendo/meta/api/";
  private static final String GET_ROOT_CAUSE = "getRootCause";
  private static final String ESCAPE = "escape";
  private static final String DERIVE_LEGACY_CLASS = "deriveLegacyClass";
  private static final String CID_123 = "cid-123";
  private static final String BUILD_HTML_ERROR_DETAILED = "buildHtmlErrorDetailed";
  private static final String FORWARD_SALES_ORDER_HEADER_HTML = "/etendo/meta/forward/SalesOrder/Header.html";
  private static final String HANDLE_EXCEPTION = "handleException";
  private static final String DETERMINE_WANTS_HTML = "determineWantsHtml";
  private static final String ISC_DATA_FORMAT = ISC_DATA_FORMAT;
  private static final String ETENDO_META_ERROR = ETENDO_META_ERROR;
  private static final String BUILD_HTML_ERROR = BUILD_HTML_ERROR;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);

    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter(FORWARD_PATH)).thenReturn(null);
    filter.init(config);
  }

  private Method getPrivateMethod(String name,
      Class<?>... paramTypes) throws ReflectiveOperationException {
    Method method = MetadataFilter.class.getDeclaredMethod(name, paramTypes);
    method.setAccessible(true);
    return method;
  }

  private String invokeBuildHtmlErrorDetailed(String cid, int status, String httpMethod,
      String uri, String exClass, String message) throws ReflectiveOperationException {
    Method method = getPrivateMethod(BUILD_HTML_ERROR_DETAILED,
        String.class, int.class, String.class, String.class,
        String.class, String.class);
    return (String) method.invoke(filter, cid, status, httpMethod, uri, exClass, message);
  }

  private void invokeHandleException(ServletRequest req, ServletResponse res,
      Throwable ex) throws ReflectiveOperationException {
    Method method = getPrivateMethod(HANDLE_EXCEPTION,
        ServletRequest.class, ServletResponse.class, Throwable.class);
    method.invoke(filter, req, res, ex);
  }

  private void setupJsonRequest(String uri, String httpMethod) {
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getMethod()).thenReturn(httpMethod);
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(APPLICATION_JSON);
  }

  private void setupJsonResponse() throws Exception {
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
  }

  private StringWriter setupHtmlResponse() throws Exception {
    StringWriter stringWriter = new StringWriter();
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    return stringWriter;
  }

  private void setupHtmlRequest(String path) {
    when(request.getPathInfo()).thenReturn(path);
    when(request.getRequestURI()).thenReturn("/etendo/meta" + path);
    when(request.getMethod()).thenReturn("GET");
    when(request.getSession()).thenReturn(mock(javax.servlet.http.HttpSession.class));
    when(request.getSession(true)).thenReturn(mock(javax.servlet.http.HttpSession.class));
  }

  private FilterChain createExceptionChain(String message) {
    return (ServletRequest r, ServletResponse s) -> {
      throw new IllegalStateException(message);
    };
  }

  @Test
  public void testInitWithDefaultForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter(FORWARD_PATH)).thenReturn(null);

    filter.init(config);

    verify(config).getInitParameter(FORWARD_PATH);
  }

  @Test
  public void testInitWithCustomForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter(FORWARD_PATH)).thenReturn("/custom-forward/");

    filter.init(config);

    verify(config).getInitParameter(FORWARD_PATH);
  }

  @Test
  public void testInitWithEmptyForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter(FORWARD_PATH)).thenReturn("   ");

    filter.init(config);

    verify(config).getInitParameter(FORWARD_PATH);
  }

  @Test(expected = ServletException.class)
  public void testNonHttpRequestThrowsException() throws Exception {
    ServletRequest req = mock(ServletRequest.class);
    ServletResponse res = mock(ServletResponse.class);

    filter.doFilter(req, res, chain);
  }

  @Test
  public void testDoFilterWithNullPathInfo() throws Exception {
    when(request.getPathInfo()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  public void testHtmlRequestHandledByLegacyServlet() throws Exception {
    setupHtmlRequest("/SalesOrder/Header.html");

    try {
      filter.doFilter(request, response, chain);
    } catch (Exception e) {
      // expected
    }

    verify(chain, never()).doFilter(request, response);
  }

  @Test
  public void returnsJsonWhenIscDataFormatJsonOnException() throws Exception {
    setupJsonRequest("/etendo/meta/window/foo", "GET");
    when(request.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
    setupJsonResponse();

    FilterChain exceptionChain = createExceptionChain("boom");

    filter.doFilter(request, response, exceptionChain);

    verify(response).setContentType(contains(APPLICATION_JSON));
    verify(response).setStatus(anyInt());
  }

  @Test
  public void testHtmlErrorResponseOnException() throws Exception {
    StringWriter stringWriter = setupHtmlResponse();
    when(request.getRequestURI()).thenReturn("/etendo/meta/SalesOrder/Header.html");
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

    FilterChain exceptionChain = createExceptionChain("Test exception");

    filter.doFilter(request, response, exceptionChain);

    verify(response).setContentType(TEXT_HTML_CHARSET_UTF8);
    verify(response).setStatus(anyInt());
    assertTrue(stringWriter.toString().contains(ETENDO_META_ERROR));
  }

  @Test
  public void testCommittedResponseNotModifiedOnException() throws Exception {
    setupJsonRequest(META_API_TEST_URI, "GET");
    when(response.isCommitted()).thenReturn(true);

    FilterChain exceptionChain = createExceptionChain("boom");

    filter.doFilter(request, response, exceptionChain);

    verify(response, never()).setStatus(anyInt());
    verify(response, never()).setContentType(anyString());
  }

  @Test
  public void testJsonResponseWhenAcceptNotHtml() throws Exception {
    setupJsonRequest(META_API_PREFIX + "endpoint", "POST");
    setupJsonResponse();

    FilterChain exceptionChain = createExceptionChain("Test error");

    filter.doFilter(request, response, exceptionChain);

    verify(response).setContentType(contains(APPLICATION_JSON));
  }

  @Test
  public void testDestroy() {
    filter.destroy();
  }

  @Test
  public void testHtmlExtensionCaseInsensitive() throws Exception {
    setupHtmlRequest("/SalesOrder/Header.HTML");

    try {
      filter.doFilter(request, response, chain);
    } catch (Exception e) {
      // expected
    }

    verify(chain, never()).doFilter(request, response);
  }

  @Test
  public void testErrorResponseWithQueryString() throws Exception {
    setupJsonRequest(META_API_TEST_URI, "GET");
    when(request.getQueryString()).thenReturn("param1=value1&param2=value2");
    setupJsonResponse();

    FilterChain exceptionChain = createExceptionChain("Error with query");

    filter.doFilter(request, response, exceptionChain);

    verify(response).setStatus(anyInt());
  }

  @Test
  public void testHtmlErrorEscapesSpecialCharacters() throws Exception {
    StringWriter stringWriter = setupHtmlResponse();
    when(request.getRequestURI()).thenReturn("/etendo/meta/test.html");
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

    FilterChain exceptionChain = createExceptionChain("<script>alert('xss')</script>");

    filter.doFilter(request, response, exceptionChain);

    String output = stringWriter.toString();
    assertFalse(output.contains("<script>"));
    assertTrue(output.contains("&lt;script&gt;"));
  }

  @Test
  public void testDetermineWantsHtml() throws Exception {
    Method method = MetadataFilter.class.getDeclaredMethod(DETERMINE_WANTS_HTML, String.class,
        String.class, HttpServletRequest.class);
    method.setAccessible(true);

    assertTrue((Boolean) method.invoke(filter, TEST_HTML_PATH, null, null));

    assertFalse((Boolean) method.invoke(filter, TEST_PATH, APPLICATION_JSON, null));

    assertTrue((Boolean) method.invoke(filter, TEST_PATH, "text/html,application/xhtml+xml", null));

    assertFalse((Boolean) method.invoke(filter, null, null, null));
  }

  @Test
  public void testBuildHtmlError() throws Exception {
    Method method = MetadataFilter.class.getDeclaredMethod(BUILD_HTML_ERROR,
        String.class, int.class, String.class, String.class, String.class);
    method.setAccessible(true);

    String result = (String) method.invoke(filter, "cid-1", 500, "GET", TEST_PATH, "error message");
    assertTrue(result.contains(ETENDO_META_ERROR));
    assertTrue(result.contains("error message"));
    assertTrue(result.contains("cid-1"));
  }

  @Test
  public void testBuildHtmlErrorNullMessage() throws Exception {
    Method method = MetadataFilter.class.getDeclaredMethod(BUILD_HTML_ERROR,
        String.class, int.class, String.class, String.class, String.class);
    method.setAccessible(true);

    String result = (String) method.invoke(filter, "cid-2", 500, "GET", TEST_PATH, null);
    assertTrue(result.contains("Unexpected error"));
  }

  @Test
  public void testGetRootCause() throws Exception {
    Method method = MetadataFilter.class.getDeclaredMethod(GET_ROOT_CAUSE, Throwable.class);
    method.setAccessible(true);

    RuntimeException root = new RuntimeException("root");
    RuntimeException wrapper = new RuntimeException("wrapper", root);

    Throwable result = (Throwable) method.invoke(filter, wrapper);
    assertTrue(result.getMessage().contains("root"));
  }

  @Test
  public void testEscapeHtml() throws Exception {
    Method method = MetadataFilter.class.getDeclaredMethod(ESCAPE, String.class);
    method.setAccessible(true);

    String result = (String) method.invoke(filter, "<script>");
    assertTrue(result.contains("&lt;script&gt;"));
  }

  @Test
  public void testGetRootCauseDirectException() throws Exception {
    Method method = getPrivateMethod(GET_ROOT_CAUSE, Throwable.class);

    RuntimeException directException = new RuntimeException("direct");
    Throwable result = (Throwable) method.invoke(filter, directException);

    assertEquals(directException, result);
  }

  @Test
  public void testGetRootCauseNestedChain() throws Exception {
    Method method = getPrivateMethod(GET_ROOT_CAUSE, Throwable.class);

    RuntimeException root = new RuntimeException("root");
    RuntimeException middle = new RuntimeException("middle", root);
    RuntimeException top = new RuntimeException("top", middle);

    Throwable result = (Throwable) method.invoke(filter, top);

    assertEquals(root, result);
  }

  @Test
  public void testGetRootCauseSelfReference() throws Exception {
    Method method = getPrivateMethod(GET_ROOT_CAUSE, Throwable.class);

    RuntimeException selfRef = new RuntimeException("self") {
      @Override
      public synchronized Throwable getCause() {
        return this;
      }
    };

    Throwable result = (Throwable) method.invoke(filter, selfRef);
    assertEquals(selfRef, result);
  }

  @Test
  public void testEscapeNull() throws Exception {
    Method escapeMethod = getPrivateMethod(ESCAPE, String.class);

    String result = (String) escapeMethod.invoke(filter, (String) null);
    assertEquals("", result);
  }

  @Test
  public void testEscapeAmpersand() throws Exception {
    Method escapeMethod = getPrivateMethod(ESCAPE, String.class);

    String result = (String) escapeMethod.invoke(filter, "a&b");
    assertEquals("a&amp;b", result);
  }

  @Test
  public void testEscapeAngleBrackets() throws Exception {
    Method escapeMethod = getPrivateMethod(ESCAPE, String.class);

    String result = (String) escapeMethod.invoke(filter, "<div>");
    assertEquals("&lt;div&gt;", result);
  }

  @Test
  public void testDeriveLegacyClassValid() throws Exception {
    Method method = getPrivateMethod(DERIVE_LEGACY_CLASS, String.class);

    String result = (String) method.invoke(filter, "/etendo/meta/forward/SalesOrder/Header_Edition.html");
    assertNotNull(result);
    assertTrue(result.contains("org.openbravo.erpWindows"));
    assertTrue(result.contains("SalesOrder"));
    assertTrue(result.contains("Header"));
  }

  @Test
  public void testDeriveLegacyClassNoForwardPath() throws Exception {
    Method method = getPrivateMethod(DERIVE_LEGACY_CLASS, String.class);

    String result = (String) method.invoke(filter, "/etendo/meta/other/path");
    assertNull(result);
  }

  @Test
  public void testDeriveLegacyClassTooFewParts() throws Exception {
    Method method = getPrivateMethod(DERIVE_LEGACY_CLASS, String.class);

    String result = (String) method.invoke(filter, "/etendo/meta/forward/onlyOneLevel");
    assertNull(result);
  }

  @Test
  public void testDeriveLegacyClassNoUnderscore() throws Exception {
    Method method = getPrivateMethod(DERIVE_LEGACY_CLASS, String.class);

    String result = (String) method.invoke(filter, "/etendo/meta/forward/Window/Page.html");
    assertNotNull(result);
    assertEquals("org.openbravo.erpWindows.Window.Page", result);
  }

  @Test
  public void testBuildHtmlErrorNullMessageAdditional() throws Exception {
    Method method = getPrivateMethod(BUILD_HTML_ERROR,
        String.class, int.class, String.class, String.class, String.class);

    String result = (String) method.invoke(filter, CID_123, 500, "GET", TEST_PATH, null);
    assertNotNull(result);
    assertTrue(result.contains("Unexpected error"));
  }

  @Test
  public void testBuildHtmlErrorDetailedWithCNF() throws Exception {
    String result = invokeBuildHtmlErrorDetailed(CID_123, 500, "GET",
        FORWARD_SALES_ORDER_HEADER_HTML,
        "java.lang.ClassNotFoundException",
        "org.openbravo.erpWindows.SalesOrder.Header");

    assertNotNull(result);
    assertTrue(result.contains("Hint"));
    assertTrue(result.contains("Legacy WAD servlet not found"));
  }

  @Test
  public void testBuildHtmlErrorDetailedWithoutCNF() throws Exception {
    String result = invokeBuildHtmlErrorDetailed(CID_123, 500, "GET",
        META_API_TEST_URI,
        "java.lang.RuntimeException",
        "some error");

    assertNotNull(result);
    assertFalse(result.contains("Hint"));
  }

  @Test
  public void testBuildHtmlErrorDetailedNonHtmlUri() throws Exception {
    String result = invokeBuildHtmlErrorDetailed(CID_123, 500, "GET",
        META_API_TEST_URI,
        "java.lang.ClassNotFoundException",
        "org.openbravo.erpWindows.SomeClass");

    assertNotNull(result);
    assertFalse(result.contains("Legacy WAD servlet not found"));
  }

  @Test
  public void testBuildLegacyHint() throws Exception {
    Method method = getPrivateMethod("buildLegacyHint", String.class);

    String result = (String) method.invoke(filter, FORWARD_SALES_ORDER_HEADER_HTML);
    assertNotNull(result);
    assertTrue(result.contains("Expected class"));
    assertTrue(result.contains("compile src-wad"));
  }

  @Test
  public void testBuildLegacyHintNoForwardPath() throws Exception {
    Method method = getPrivateMethod("buildLegacyHint", String.class);

    String result = (String) method.invoke(filter, "/etendo/meta/other/path");
    assertNotNull(result);
    assertTrue(result.contains("compile src-wad"));
    assertFalse(result.contains("Expected class"));
  }

  @Test
  public void testHandleExceptionNullResponse() throws Exception {
    javax.servlet.ServletResponse nonHttpResponse = mock(javax.servlet.ServletResponse.class);

    invokeHandleException(request, nonHttpResponse, new RuntimeException("test"));
  }

  @Test
  public void testHandleExceptionCommittedResponse() throws Exception {
    when(response.isCommitted()).thenReturn(true);
    when(request.getRequestURI()).thenReturn(TEST_PATH);
    when(request.getMethod()).thenReturn("GET");

    invokeHandleException(request, response, new RuntimeException("test"));

    verify(response, never()).setStatus(anyInt());
  }

  @Test
  public void testHandleExceptionHtmlUriResponse() throws Exception {
    StringWriter stringWriter = new StringWriter();
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    when(request.getRequestURI()).thenReturn(TEST_HTML_PATH);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

    invokeHandleException(request, response, new RuntimeException("html error"));

    verify(response).setContentType(TEXT_HTML_CHARSET_UTF8);
    assertTrue(stringWriter.toString().contains(ETENDO_META_ERROR));
  }

  @Test
  public void testHandleExceptionIscJsonOverridesHtml() throws Exception {
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    when(request.getRequestURI()).thenReturn(TEST_HTML_PATH);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);
    when(request.getParameter(ISC_DATA_FORMAT)).thenReturn("json");

    invokeHandleException(request, response, new RuntimeException("json error"));

    verify(response, never()).setContentType(TEXT_HTML_CHARSET_UTF8);
  }

  @Test
  public void testDetermineWantsHtmlNullInputs() throws Exception {
    Method method = getPrivateMethod(DETERMINE_WANTS_HTML,
        String.class, String.class, HttpServletRequest.class);

    assertFalse((Boolean) method.invoke(filter, null, null, null));
  }

  @Test
  public void testDetermineWantsHtmlWithHtmlUri() throws Exception {
    Method method = getPrivateMethod(DETERMINE_WANTS_HTML,
        String.class, String.class, HttpServletRequest.class);

    assertTrue((Boolean) method.invoke(filter, TEST_HTML_PATH, null, null));
  }

  @Test
  public void testDetermineWantsHtmlIscJsonOverride() throws Exception {
    Method method = getPrivateMethod(DETERMINE_WANTS_HTML,
        String.class, String.class, HttpServletRequest.class);

    when(request.getParameter(ISC_DATA_FORMAT)).thenReturn("json");

    assertFalse((Boolean) method.invoke(filter, TEST_HTML_PATH, TEXT_HTML, request));
  }

  @Test
  public void testDetermineWantsHtmlWithAcceptHeader() throws Exception {
    Method method = getPrivateMethod(DETERMINE_WANTS_HTML,
        String.class, String.class, HttpServletRequest.class);

    assertTrue((Boolean) method.invoke(filter, TEST_PATH, TEXT_HTML, null));
  }

  @Test
  public void testDoFilterForwardPathMatch() throws Exception {
    when(request.getPathInfo()).thenReturn("/forward/SalesOrder/Header");
    when(request.getRequestURI()).thenReturn("/etendo/meta/forward/SalesOrder/Header");
    when(request.getMethod()).thenReturn("GET");

    try {
      filter.doFilter(request, response, chain);
    } catch (Exception e) {
      // Expected - ForwarderServlet may throw
    }

    verify(chain, never()).doFilter(request, response);
  }

  @Test
  public void testHandleExceptionAcceptHtmlHeader() throws Exception {
    StringWriter stringWriter = new StringWriter();
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    when(request.getRequestURI()).thenReturn("/test/api");
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

    invokeHandleException(request, response, new RuntimeException("accept html"));

    verify(response).setContentType(TEXT_HTML_CHARSET_UTF8);
  }

  @Test
  public void testHandleExceptionChainedCause() throws Exception {
    when(response.isCommitted()).thenReturn(false);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    when(request.getRequestURI()).thenReturn("/test/api");
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(ACCEPT_HEADER)).thenReturn(APPLICATION_JSON);

    Exception root = new IllegalArgumentException("root cause");
    Exception wrapper = new RuntimeException("wrapper", root);

    FilterChain exceptionChain = (req, res) -> {
      throw new javax.servlet.ServletException(wrapper);
    };

    filter.doFilter(request, response, exceptionChain);

    verify(response).setStatus(anyInt());
  }

  @Test
  public void testBuildHtmlErrorDetailedCnfInMessage() throws Exception {
    String result = invokeBuildHtmlErrorDetailed(CID_123, 500, "GET",
        FORWARD_SALES_ORDER_HEADER_HTML,
        "java.lang.RuntimeException",
        "org.openbravo.erpWindows.SalesOrder.Header not found");

    assertNotNull(result);
    assertTrue(result.contains("Hint"));
  }

  @Test
  public void testBuildHtmlErrorDetailedNullExClass() throws Exception {
    String result = invokeBuildHtmlErrorDetailed(CID_123, 500, "GET",
        "/etendo/meta/test.html",
        null,
        "some error");

    assertNotNull(result);
    assertFalse(result.contains("Hint"));
  }
}
