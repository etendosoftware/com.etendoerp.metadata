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

import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Menu;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

/** Unit tests for {@link MetadataServlet} request routing and error handling. */
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

    servlet.doGet("", req, res);

    verify(res).setStatus(eq(Utils.getHttpStatusFor(new com.etendoerp.metadata.exceptions.NotFoundException())));
    verify(res).setContentType(contains("application/json"));
  }

  @Test
  public void returnsHtmlErrorWhenAcceptHtml() throws Exception {
    when(req.getPathInfo()).thenReturn("/unknown");
    when(req.getHeader("Accept")).thenReturn("text/html");
    StringWriter sw = new StringWriter();
    when(res.getWriter()).thenReturn(new PrintWriter(sw));

    servlet.doGet("", req, res);

    verify(res).setStatus(anyInt());
    verify(res).setContentType(contains("text/html"));
  }

  @Test
  public void menuResponseHasEtagAndCacheControlHeaders() throws Exception {
    when(req.getPathInfo()).thenReturn(Constants.MENU_PATH);
    when(req.getMethod()).thenReturn(Constants.GET);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    servlet.doGet("", req, res);

    verify(res).setHeader(eq(Constants.ETAG_HEADER), anyString());
    verify(res).setHeader(Constants.CACHE_CONTROL_HEADER, Constants.CACHE_CONTROL_PRIVATE_MUST_REVALIDATE);
  }

  @Test
  public void menuReturns304WhenIfNoneMatchMatchesCurrentEtag() throws Exception {
    when(req.getPathInfo()).thenReturn(Constants.MENU_PATH);
    when(req.getMethod()).thenReturn(Constants.GET);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    servlet.doGet("", req, res);

    ArgumentCaptor<String> etagCaptor = ArgumentCaptor.forClass(String.class);
    verify(res).setHeader(eq(Constants.ETAG_HEADER), etagCaptor.capture());
    String etag = etagCaptor.getValue();

    HttpServletRequest req2 = mock(HttpServletRequest.class);
    HttpServletResponse res2 = mock(HttpServletResponse.class);
    when(req2.getPathInfo()).thenReturn(Constants.MENU_PATH);
    when(req2.getMethod()).thenReturn(Constants.GET);
    when(req2.getHeader(Constants.IF_NONE_MATCH_HEADER)).thenReturn(etag);

    new MetadataServlet().doGet("", req2, res2);

    verify(res2).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    verify(res2, never()).getWriter();
  }

  @Test
  public void menuEtagChangesAndReturns200WhenMenuDataChanges() throws Exception {
    when(req.getPathInfo()).thenReturn(Constants.MENU_PATH);
    when(req.getMethod()).thenReturn(Constants.GET);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    servlet.doGet("", req, res);

    ArgumentCaptor<String> firstEtagCaptor = ArgumentCaptor.forClass(String.class);
    verify(res).setHeader(eq(Constants.ETAG_HEADER), firstEtagCaptor.capture());

    Menu menu = (Menu) OBDal.getInstance().createCriteria(Menu.class).setMaxResults(1).uniqueResult();
    menu.setDescription("etag-test-" + System.nanoTime());
    OBDal.getInstance().save(menu);
    OBDal.getInstance().flush();

    HttpServletRequest req2 = mock(HttpServletRequest.class);
    HttpServletResponse res2 = mock(HttpServletResponse.class);
    when(req2.getPathInfo()).thenReturn(Constants.MENU_PATH);
    when(req2.getMethod()).thenReturn(Constants.GET);
    when(res2.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    new MetadataServlet().doGet("", req2, res2);

    ArgumentCaptor<String> secondEtagCaptor = ArgumentCaptor.forClass(String.class);
    verify(res2).setHeader(eq(Constants.ETAG_HEADER), secondEtagCaptor.capture());
    verify(res2, never()).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    assertNotEquals(firstEtagCaptor.getValue(), secondEtagCaptor.getValue());
  }

  @Test
  public void noEtagOrCacheControlHeadersForMutableEndpoints() throws Exception {
    when(req.getPathInfo()).thenReturn("/process-execution");
    when(req.getMethod()).thenReturn(Constants.GET);
    when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    servlet.doGet("", req, res);

    verify(res, never()).setHeader(eq(Constants.ETAG_HEADER), anyString());
    verify(res, never()).setHeader(eq(Constants.CACHE_CONTROL_HEADER), anyString());

    HttpServletRequest tabReq = mock(HttpServletRequest.class);
    HttpServletResponse tabRes = mock(HttpServletResponse.class);
    when(tabReq.getPathInfo()).thenReturn("/tab/someTabId");
    when(tabReq.getMethod()).thenReturn(Constants.GET);
    when(tabRes.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    new MetadataServlet().doGet("", tabReq, tabRes);

    verify(tabRes, never()).setHeader(eq(Constants.ETAG_HEADER), anyString());
    verify(tabRes, never()).setHeader(eq(Constants.CACHE_CONTROL_HEADER), anyString());
  }
}

