package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

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
   * <p>
   * Initializes the MetadataFilter instance that will be tested.
   * </p>
   *
   * @throws Exception if an error occurs during setup
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
  }

  /**
   * Tests that the filter returns a JSON response when an exception occurs
   * and the client explicitly requests JSON format.
   * <p>
   * This test verifies that when:
   * <ul>
   * <li>An exception is thrown during filter chain processing</li>
   * <li>The request parameter "isc_dataFormat" is set to "json"</li>
   * <li>The response is not yet committed</li>
   * </ul>
   * The filter should respond with JSON content type and set an appropriate HTTP status.
   * </p>
   *
   * @throws Exception if the filter processing fails
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
   * Tests that the filter properly delegates HTML requests to the LegacyProcessServlet
   * without interfering with the response.
   * <p>
   * This test verifies that when:
   * <ul>
   * <li>A request path ends with ".html"</li>
   * <li>The request has valid session mocks</li>
   * <li>No exceptions occur during processing</li>
   * </ul>
   * The filter should delegate to LegacyProcessServlet and not modify the response
   * status or headers itself, as that responsibility belongs to the servlet.
   * </p>
   *
   * @throws Exception if the filter processing fails
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

    filter.doFilter(req, res, chain);

    verify(res, never()).setStatus(anyInt());
  }
}
