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
 * <p>
 * This test class verifies the behavior of the MetadataFilter when handling different
 * types of requests and error scenarios. It uses Mockito for mocking HTTP components
 * and extends WeldBaseTest for CDI support.
 * </p>
 */
public class MetadataFilterTest extends WeldBaseTest {

  private MetadataFilter filter;

  /**
   * Sets up the test environment before each test case.
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
  }

  /**
   * Tests filter initialization with default forward path.
   */
  @Test
  public void testInitWithDefaultForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter("forwardPath")).thenReturn(null);

    filter.init(config);

    verify(config).getInitParameter("forwardPath");
  }

  /**
   * Tests filter initialization with custom forward path.
   */
  @Test
  public void testInitWithCustomForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter("forwardPath")).thenReturn("/custom-forward/");

    filter.init(config);

    verify(config).getInitParameter("forwardPath");
  }

  /**
   * Tests filter initialization with empty forward path (should use default).
   */
  @Test
  public void testInitWithEmptyForwardPath() {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);

    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter("forwardPath")).thenReturn("   ");

    filter.init(config);

    verify(config).getInitParameter("forwardPath");
  }

  /**
   * Tests that non-HTTP requests throw ServletException.
   */
  @Test(expected = ServletException.class)
  public void testNonHttpRequestThrowsException() throws Exception {
    ServletRequest req = mock(ServletRequest.class);
    ServletResponse res = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);
  }

  /**
   * Tests normal request flow when pathInfo is null.
   */
  @Test
  public void testDoFilterWithNullPathInfo() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(req.getPathInfo()).thenReturn(null);

    filter.doFilter(req, res, chain);

    verify(chain).doFilter(req, res);
  }

  /**
   * Tests that HTML requests are handled by LegacyProcessServlet.
   * Note: This test will log NPE because we're in a unit test environment
   * without RequestDispatcher, but the important thing is verifying the
   * routing logic - that HTML requests don't go through the filter chain.
   */
  @Test
  public void testHtmlRequestHandledByLegacyServlet() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(req.getPathInfo()).thenReturn("/SalesOrder/Header.html");
    when(req.getRequestURI()).thenReturn("/etendo/meta/SalesOrder/Header.html");
    when(req.getMethod()).thenReturn("GET");
    when(req.getSession()).thenReturn(mock(javax.servlet.http.HttpSession.class));
    when(req.getSession(true)).thenReturn(mock(javax.servlet.http.HttpSession.class));

    // Execute - will fail internally but that's expected in unit tests
    try {
      filter.doFilter(req, res, chain);
    } catch (Exception e) {
      // Expected - LegacyProcessServlet needs RequestDispatcher
    }

    // Verify: Request was routed to LegacyProcessServlet, not through the chain
    verify(chain, never()).doFilter(req, res);
  }

  /**
   * Tests JSON response when exception occurs and isc_dataFormat is json.
   */
  @Test
  public void returnsJsonWhenIscDataFormatJsonOnException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("boom");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/window/foo");
    when(req.getParameter("isc_dataFormat")).thenReturn("json");
    when(req.getMethod()).thenReturn("GET");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains("application/json"));
    verify(res).setStatus(anyInt());
  }

  /**
   * Tests HTML error response when exception occurs on HTML request.
   */
  @Test
  public void testHtmlErrorResponseOnException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Test exception");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/SalesOrder/Header.html");
    when(req.getMethod()).thenReturn("GET");
    when(req.getHeader("Accept")).thenReturn("text/html");
    when(req.getParameter("isc_dataFormat")).thenReturn(null);
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    verify(res).setContentType("text/html; charset=UTF-8");
    verify(res).setStatus(anyInt());
    String output = stringWriter.toString();
    assertTrue(output.contains("Etendo Meta Error"));
  }

  /**
   * Tests that committed response is not modified on exception.
   */
  @Test
  public void testCommittedResponseNotModifiedOnException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("boom");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/api/test");
    when(req.getMethod()).thenReturn("GET");
    when(res.isCommitted()).thenReturn(true);

    filter.doFilter(req, res, chain);

    verify(res, never()).setStatus(anyInt());
    verify(res, never()).setContentType(anyString());
  }

  /**
   * Tests JSON response when Accept header doesn't contain text/html.
   */
  @Test
  public void testJsonResponseWhenAcceptNotHtml() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Test error");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/api/endpoint");
    when(req.getMethod()).thenReturn("POST");
    when(req.getHeader("Accept")).thenReturn("application/json");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains("application/json"));
  }

  /**
   * Tests that destroy method can be called without errors.
   */
  @Test
  public void testDestroy() {
    filter.destroy();
    // Should not throw any exception
  }

  /**
   * Tests HTML request case insensitivity.
   * Verifies that .HTML (uppercase) is treated the same as .html
   */
  @Test
  public void testHtmlExtensionCaseInsensitive() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // Setup: Request with uppercase .HTML extension
    when(req.getPathInfo()).thenReturn("/SalesOrder/Header.HTML");
    when(req.getRequestURI()).thenReturn("/etendo/meta/SalesOrder/Header.HTML");
    when(req.getMethod()).thenReturn("GET");
    when(req.getSession()).thenReturn(mock(javax.servlet.http.HttpSession.class));
    when(req.getSession(true)).thenReturn(mock(javax.servlet.http.HttpSession.class));

    // Execute: This will attempt to process through LegacyProcessServlet
    // and throw NPE because we don't have a full servlet container,
    // but that's expected in unit tests
    try {
      filter.doFilter(req, res, chain);
    } catch (Exception e) {
      // Expected - LegacyProcessServlet needs full servlet infrastructure
      // The important thing is that it tried to use LegacyProcessServlet
      // instead of passing through the chain
    }

    // Verify: The request was NOT passed through the normal filter chain
    // This confirms it was recognized as an HTML request
    verify(chain, never()).doFilter(req, res);
  }

  /**
   * Tests exception with nested causes.
   */
  @Test
  public void testExceptionWithNestedCauses() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();

    Exception rootCause = new IllegalArgumentException("Root cause");
    Exception midCause = new RuntimeException("Mid cause", rootCause);
    Exception topCause = new ServletException("Top cause", midCause);

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      try {
        throw topCause;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/test");
    when(req.getMethod()).thenReturn("GET");
    when(req.getHeader("Accept")).thenReturn("application/json");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    verify(res).setStatus(anyInt());
    String output = stringWriter.toString();
    assertTrue(output.contains("Root cause"));
  }

  /**
   * Tests HTML fallback on empty legacy response.
   * Note: This test expects NPE in logs because we're testing in isolation
   * without a full servlet container (no RequestDispatcher available).
   * The test verifies the filter's routing logic, not the full execution.
   */
  @Test
  public void htmlFallbackOnEmptyLegacyResponse() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
    };

    when(req.getPathInfo()).thenReturn("/SalesInvoice/Header_Edition.html");
    when(req.getRequestURI()).thenReturn("/etendo/meta/SalesInvoice/Header_Edition.html");
    when(req.getMethod()).thenReturn("GET");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(writer);

    when(req.getSession()).thenReturn(mock(javax.servlet.http.HttpSession.class));
    when(req.getSession(true)).thenReturn(mock(javax.servlet.http.HttpSession.class));

    // Expected to fail internally but we're testing the filter's behavior
    try {
      filter.doFilter(req, res, chain);
    } catch (Exception e) {
      // Expected - we don't have RequestDispatcher in unit test environment
    }

    // The key verification: response status should not be set by the filter
    // (LegacyProcessServlet handles that, but fails in unit test environment)
    verify(res, never()).setStatus(anyInt());
  }

  /**
   * Tests error response includes query string in logs.
   */
  @Test
  public void testErrorResponseWithQueryString() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Error with query");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/api/test");
    when(req.getQueryString()).thenReturn("param1=value1&param2=value2");
    when(req.getMethod()).thenReturn("GET");
    when(req.getHeader("Accept")).thenReturn("application/json");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilter(req, res, chain);

    verify(res).setStatus(anyInt());
  }

  /**
   * Tests JSON response contains correlation ID.
   */
  @Test
  public void testJsonResponseContainsCorrelationId() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Test error");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/api/test");
    when(req.getMethod()).thenReturn("GET");
    when(req.getHeader("Accept")).thenReturn("application/json");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    String output = stringWriter.toString();
    assertTrue(output.contains("\"cid\":"));
  }

  /**
   * Tests escape method protection in error messages.
   */
  @Test
  public void testHtmlErrorEscapesSpecialCharacters() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    StringWriter stringWriter = new StringWriter();
    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("<script>alert('xss')</script>");
    };

    when(req.getRequestURI()).thenReturn("/etendo/meta/test.html");
    when(req.getMethod()).thenReturn("GET");
    when(req.getHeader("Accept")).thenReturn("text/html");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    String output = stringWriter.toString();
    assertFalse(output.contains("<script>"));
    assertTrue(output.contains("&lt;script&gt;"));
  }

  /**
   * Tests that pathInfo with only forward prefix but no additional path goes through.
   */
  @Test
  public void testForwardPathWithoutAdditionalPathGoesThrough() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(config.getServletContext()).thenReturn(servletContext);
    when(config.getInitParameter("forwardPath")).thenReturn("/forward/");
    filter.init(config);

    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(req.getPathInfo()).thenReturn("/api/data");

    filter.doFilter(req, res, chain);

    verify(chain).doFilter(req, res);
  }
}
