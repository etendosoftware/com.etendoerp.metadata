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

public class MetadataFilterTest extends WeldBaseTest {

  private MetadataFilter filter;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    filter = new MetadataFilter();
  }

  @Test
  public void returnsJsonWhenIscDataFormatJsonOnException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> { throw new RuntimeException("boom"); };

    when(req.getRequestURI()).thenReturn("/etendo/meta/window/foo");
    when(req.getParameter("isc_dataFormat")).thenReturn("json");
    when(req.getMethod()).thenReturn("GET");
    when(res.isCommitted()).thenReturn(false);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains("application/json"));
    verify(res).setStatus(anyInt());
  }

  @Test
  public void htmlFallbackOnEmptyLegacyResponse() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    FilterChain chain = (ServletRequest r, ServletResponse s) -> { /* do nothing to simulate empty body */ };

    when(req.getRequestURI()).thenReturn("/etendo/meta/forward/SalesInvoice/Header_Edition.html");
    when(req.getMethod()).thenReturn("GET");
    when(res.isCommitted()).thenReturn(false);
    when(res.getBufferSize()).thenReturn(1024);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilter(req, res, chain);

    verify(res).setContentType(contains("text/html"));
    verify(res).setStatus(anyInt());
  }
}

