package com.etendoerp.metadata.http;

import com.etendoerp.metadata.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

public class MetadataServletTest extends WeldBaseTest {

  private MetadataServlet servlet;
  private HttpServletRequest req;
  private HttpServletResponse res;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    servlet = new MetadataServlet();
    req = mock(HttpServletRequest.class);
    res = mock(HttpServletResponse.class);
  }

  @Test
  public void returnsJsonErrorWhenIscDataFormatJson() throws Exception {
    when(req.getPathInfo()).thenReturn("/unknown");
    when(req.getParameter("isc_dataFormat")).thenReturn("json");
    StringWriter sw = new StringWriter();
    when(res.getWriter()).thenReturn(new PrintWriter(sw));

    servlet.doGet(req, res);

    verify(res).setStatus(eq(Utils.getHttpStatusFor(new com.etendoerp.metadata.exceptions.NotFoundException())));
    verify(res).setContentType(contains("application/json"));
  }

  @Test
  public void returnsHtmlErrorWhenAcceptHtml() throws Exception {
    when(req.getPathInfo()).thenReturn("/unknown");
    when(req.getHeader("Accept")).thenReturn("text/html");
    StringWriter sw = new StringWriter();
    when(res.getWriter()).thenReturn(new PrintWriter(sw));

    servlet.doGet(req, res);

    verify(res).setStatus(anyInt());
    verify(res).setContentType(contains("text/html"));
  }
}

