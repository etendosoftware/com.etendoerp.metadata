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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the MetadataFilter class.
 */
public class MetadataFilterTest extends WeldBaseTest {

  private MetadataFilter filter;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;

  private static final String FORWARD_PATH = "forwardPath";
  private static final String ACCEPT_HEADER = "Accept";
  private static final String APPLICATION_JSON = "application/json";
  private static final String TEXT_HTML = "text/html";

  private static final String META_API_TEST_URI = "/etendo/meta/api/test";
  private static final String META_API_PREFIX = "/etendo/meta/api/";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
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
    when(request.getParameter("isc_dataFormat")).thenReturn("json");
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

    verify(response).setContentType("text/html; charset=UTF-8");
    verify(response).setStatus(anyInt());
    assertTrue(stringWriter.toString().contains("Etendo Meta Error"));
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

  private void setupJsonRequest(String uri, String method) {
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getMethod()).thenReturn(method);
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
      throw new RuntimeException(message);
    };
  }
}
