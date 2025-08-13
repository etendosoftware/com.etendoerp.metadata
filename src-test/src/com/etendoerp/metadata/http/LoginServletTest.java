package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

public class LoginServletTest extends WeldBaseTest {

  private LoginServlet servlet;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    servlet = new LoginServlet();
  }

  @Test
  public void doGetReturnsJsonWhenIscDataFormatJson() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(req.getParameter("isc_dataFormat")).thenReturn("json");
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    servlet.doGet(req, res);

    verify(res).setStatus(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED));
    verify(res).setContentType(contains("application/json"));
  }
}

