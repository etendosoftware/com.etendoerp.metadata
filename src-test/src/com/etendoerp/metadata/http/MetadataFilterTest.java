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

  private static final String FORWARD_PATH = "forwardPath";
  private static final String ACCEPT_HEADER = "Accept";
  private static final String APPLICATION_JSON = "application/json";

  private static final String META_API_TEST_URI = "/etendo/meta/api/test";
  private static final String META_API_PREFIX = "/etendo/meta/api/";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
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
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);
  }

  @Test
  public void testDoFilterWithNullPathInfo() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(req.getPathInfo()).thenReturn(null);

    filter.doFilter(req, res, chain);

    verify(chain).doFilter(req, res);
  }

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

    try {
      filter.doFilter(req, res, chain);
    } catch (Exception e) {
      // expected
    }

    verify(chain, never()).doFilter(req, res);
  }

  @Test
  public void returnsJsonWhenIscDataFormatJsonOnException() throws Exception {
    HttpServletRequest req = mockJsonRequest("/etendo/meta/window/foo", "GET");
    when(req.getParameter("isc_dataFormat")).thenReturn("json");

    HttpServletResponse res = mockJsonResponse();

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("boom");
    };

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains(APPLICATION_JSON));
    verify(res).setStatus(anyInt());
  }

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
    when(req.getHeader(ACCEPT_HEADER)).thenReturn("text/html");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    verify(res).setContentType("text/html; charset=UTF-8");
    verify(res).setStatus(anyInt());
    assertTrue(stringWriter.toString().contains("Etendo Meta Error"));
  }

  @Test
  public void testCommittedResponseNotModifiedOnException() throws Exception {
    HttpServletRequest req = mockJsonRequest(META_API_TEST_URI, "GET");
    HttpServletResponse res = mock(HttpServletResponse.class);

    when(res.isCommitted()).thenReturn(true);

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("boom");
    };

    filter.doFilter(req, res, chain);

    verify(res, never()).setStatus(anyInt());
    verify(res, never()).setContentType(anyString());
  }

  @Test
  public void testJsonResponseWhenAcceptNotHtml() throws Exception {
    HttpServletRequest req = mockJsonRequest(META_API_PREFIX + "endpoint", "POST");
    HttpServletResponse res = mockJsonResponse();

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Test error");
    };

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains(APPLICATION_JSON));
  }

  @Test
  public void testDestroy() {
    filter.destroy();
  }

  @Test
  public void testHtmlExtensionCaseInsensitive() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    when(req.getPathInfo()).thenReturn("/SalesOrder/Header.HTML");
    when(req.getRequestURI()).thenReturn("/etendo/meta/SalesOrder/Header.HTML");
    when(req.getMethod()).thenReturn("GET");
    when(req.getSession()).thenReturn(mock(javax.servlet.http.HttpSession.class));
    when(req.getSession(true)).thenReturn(mock(javax.servlet.http.HttpSession.class));

    try {
      filter.doFilter(req, res, chain);
    } catch (Exception e) {
      // expected
    }

    verify(chain, never()).doFilter(req, res);
  }

  @Test
  public void testErrorResponseWithQueryString() throws Exception {
    HttpServletRequest req = mockJsonRequest(META_API_TEST_URI, "GET");
    when(req.getQueryString()).thenReturn("param1=value1&param2=value2");

    HttpServletResponse res = mockJsonResponse();

    FilterChain chain = (ServletRequest r, ServletResponse s) -> {
      throw new RuntimeException("Error with query");
    };

    filter.doFilter(req, res, chain);

    verify(res).setStatus(anyInt());
  }

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
    when(req.getHeader(ACCEPT_HEADER)).thenReturn("text/html");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(stringWriter));

    filter.doFilter(req, res, chain);

    String output = stringWriter.toString();
    assertFalse(output.contains("<script>"));
    assertTrue(output.contains("&lt;script&gt;"));
  }

  private HttpServletRequest mockJsonRequest(String uri, String method) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn(uri);
    when(req.getMethod()).thenReturn(method);
    when(req.getHeader(ACCEPT_HEADER)).thenReturn(APPLICATION_JSON);
    return req;
  }

  private HttpServletResponse mockJsonResponse() throws Exception {
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    return res;
  }
}
