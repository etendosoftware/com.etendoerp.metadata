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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.erpCommon.utility.OBMessageUtils;

/**
 * Unit tests for the file download support of {@link LegacyProcessServlet}: files answered by
 * Classic servlets (e.g. Create Remittance File, Intrastat Generate File) and Classic
 * {@code PopUp_Download} pages (e.g. Reconciliation reports) are short-circuited with a forwarder
 * page that reports success to the new UI and downloads the file intact.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LegacyProcessServletFileDownloadTest {

  private static final String CONTEXT_PATH = "/etendo";
  private static final String SUCCESS_TITLE = "Process completed successfully";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String APPLICATION_RTF = "application/rtf";
  private static final String BANK_FILE = "BANK.DAT";
  private static final String ATTACHMENT_BANK_FILE = "attachment; filename=" + BANK_FILE;
  private static final String HTML_UTF8_CONTENT_TYPE = "text/html; charset=UTF-8";
  private static final String SHOW_PROCESS_MESSAGE_SNIPPET = "action:'showProcessMessage'";
  private static final String LOCATION_REPLACE_SNIPPET = "window.location.replace(";
  private static final String DOWNLOAD_URL_SNIPPET = "download?fileId=";
  private static final String DOWNLOAD_REPORT_URL = "/etendo/utility/DownloadReport.html?report=Rec-1.pdf";
  private static final String HANDLE_RESPONSE = "handleResponse";
  private static final String COMMAND_DEFAULT = "DEFAULT";
  private static final String POPUP_RESPONSE_BODY =
      "<body onload=\"submitThisPage('/etendo/FinancialAccount/Account_Relation.html');\" id=\"paramHref\">";
  private static final byte[] BINARY_CONTENT = {0, (byte) 0xFF, 0x50, 0x4B, (byte) 0x80};

  private LegacyProcessServlet servlet;
  private HttpServletResponse response;
  private StringWriter written;
  private ByteArrayOutputStream streamed;
  private HttpSession session;
  private MockedStatic<OBMessageUtils> messageUtilsMock;

  /**
   * Prepares the servlet, a response capturing both its writer and output stream, a map-backed
   * session and the success message lookup.
   *
   * @throws IOException if the mocked response streams cannot be created
   */
  @Before
  public void setUp() throws IOException {
    servlet = new LegacyProcessServlet();
    response = mock(HttpServletResponse.class);
    written = new StringWriter();
    streamed = new ByteArrayOutputStream();
    when(response.getWriter()).thenReturn(new PrintWriter(written));
    when(response.getOutputStream()).thenReturn(capturingStream(streamed));
    session = LegacyFileDownloadTest.mapBackedSession();
    messageUtilsMock = mockStatic(OBMessageUtils.class);
    messageUtilsMock.when(() -> OBMessageUtils.messageBD("Success")).thenReturn(SUCCESS_TITLE);
  }

  /**
   * Releases the static mock.
   */
  @After
  public void tearDown() {
    messageUtilsMock.close();
  }

  private static ServletOutputStream capturingStream(ByteArrayOutputStream target) {
    return new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
        // Not needed for synchronous tests.
      }

      @Override
      public void write(int b) {
        target.write(b);
      }
    };
  }

  /**
   * Builds a wrapper whose included servlet streamed a binary file with the given headers.
   *
   * @throws IOException if the wrapper output stream fails
   */
  private HttpServletResponseLegacyWrapper fileWrapper(String contentType, String disposition)
      throws IOException {
    HttpServletResponseLegacyWrapper wrapper = new HttpServletResponseLegacyWrapper(mock(HttpServletResponse.class));
    wrapper.setContentType(contentType);
    wrapper.setHeader(CONTENT_DISPOSITION, disposition);
    wrapper.getOutputStream().write(BINARY_CONTENT);
    return wrapper;
  }

  private HttpServletRequest downloadRequest(String fileId) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(session);
    when(request.getParameter(LegacyProcessServlet.FILE_ID_PARAM)).thenReturn(fileId);
    return request;
  }

  private String storedFileId(String forwarderHtml) {
    int start = forwarderHtml.indexOf(DOWNLOAD_URL_SNIPPET) + DOWNLOAD_URL_SNIPPET.length();
    return forwarderHtml.substring(start, forwarderHtml.indexOf('"', start));
  }

  private void invokeHandleResponse(HttpServletResponseLegacyWrapper wrapper) throws Exception {
    Method method = LegacyProcessServlet.class.getDeclaredMethod(HANDLE_RESPONSE, HttpServletResponse.class,
        HttpServletResponseLegacyWrapper.class, String.class, String.class);
    method.setAccessible(true);
    method.invoke(servlet, response, wrapper, "/remittance/CreateFile.html", CONTEXT_PATH);
  }

  /**
   * Invokes {@code handleResponse} with {@link RequestContext} returning the given request.
   *
   * @throws Exception if the reflective call fails
   */
  private void invokeHandleResponseWithin(HttpServletRequest request, HttpServletResponseLegacyWrapper wrapper)
      throws Exception {
    RequestContext requestContext = mock(RequestContext.class);
    when(requestContext.getRequest()).thenReturn(request);
    try (MockedStatic<RequestContext> contextMock = mockStatic(RequestContext.class)) {
      contextMock.when(RequestContext::get).thenReturn(requestContext);
      invokeHandleResponse(wrapper);
    }
  }

  private void assertSuccessForwarder(String html) {
    assertTrue(html.contains(SHOW_PROCESS_MESSAGE_SNIPPET));
    assertTrue(html.contains("\"type\":\"success\""));
    assertTrue(html.contains(SUCCESS_TITLE));
    assertTrue(html.contains(LOCATION_REPLACE_SNIPPET));
  }

  /**
   * Tests that only paths ending with {@code /download} reach the download endpoint.
   */
  @Test
  public void isDownloadRequestMatchesDownloadPathOnly() {
    assertTrue(LegacyProcessServlet.isDownloadRequest("/download"));
    assertTrue(LegacyProcessServlet.isDownloadRequest("/Download"));
    assertFalse(LegacyProcessServlet.isDownloadRequest("/remittance/CreateFile.html"));
    assertFalse(LegacyProcessServlet.isDownloadRequest(null));
  }

  /**
   * Tests that the DownloadReport URL of a Classic PopUp_Download page is extracted.
   */
  @Test
  public void extractDownloadReportUrlReturnsUrlOfDownloadPage() {
    String body = "<body onload=\"onLoadDo('" + DOWNLOAD_REPORT_URL + "');\" id=\"paramHref\">";

    assertEquals(DOWNLOAD_REPORT_URL, LegacyProcessServlet.extractDownloadReportUrl(body));
  }

  /**
   * Tests that regular pages and a missing body yield no download URL.
   */
  @Test
  public void extractDownloadReportUrlReturnsNullForRegularPages() {
    assertNull(LegacyProcessServlet.extractDownloadReportUrl("<body onload=\"onLoadDo('xx');\">"));
    assertNull(LegacyProcessServlet.extractDownloadReportUrl(null));
  }

  /**
   * Tests that the forwarder reports success with the file name and navigates to the file URL.
   *
   * @throws IOException if the response writer fails
   */
  @Test
  public void writeFileDownloadForwarderReportsSuccessAndNavigatesToFile() throws IOException {
    servlet.writeFileDownloadForwarder(response, DOWNLOAD_REPORT_URL, BANK_FILE);

    String html = written.toString();
    assertSuccessForwarder(html);
    assertTrue(html.contains(BANK_FILE));
    assertTrue(html.contains("DownloadReport.html?report=Rec-1.pdf"));
    verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  /**
   * Tests that a captured file is stored in the session and the forwarder points at the
   * download endpoint with its identifier.
   *
   * @throws IOException if the wrapper or response streams fail
   */
  @Test
  public void writeCapturedFileForwarderStoresFileAndPointsToDownloadEndpoint() throws IOException {
    servlet.writeCapturedFileForwarder(response, session, fileWrapper(APPLICATION_RTF, ATTACHMENT_BANK_FILE),
        CONTEXT_PATH);

    String html = written.toString();
    assertSuccessForwarder(html);
    assertTrue(html.contains(BANK_FILE));
    assertTrue(html.contains("legacy\\/download?fileId="));
    LegacyFileDownload stored = LegacyFileDownload.take(session, storedFileId(html));
    assertArrayEquals(BINARY_CONTENT, stored.getContent());
    assertEquals(APPLICATION_RTF, stored.getContentType());
  }

  /**
   * Tests that the download endpoint replays the original headers and bytes once.
   *
   * @throws IOException if the response stream fails
   */
  @Test
  public void processDownloadRequestServesOriginalFileOnce() throws IOException {
    String id = LegacyFileDownload.store(session, new LegacyFileDownload(BINARY_CONTENT, APPLICATION_RTF,
        ATTACHMENT_BANK_FILE));
    HttpServletRequest request = downloadRequest(id);

    servlet.processDownloadRequest(request, response);

    assertArrayEquals(BINARY_CONTENT, streamed.toByteArray());
    verify(response).setContentType(APPLICATION_RTF);
    verify(response).setHeader(CONTENT_DISPOSITION, ATTACHMENT_BANK_FILE);
    verify(response).setContentLength(BINARY_CONTENT.length);

    servlet.processDownloadRequest(request, response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  /**
   * Tests that a file without declared content type is served as octet-stream.
   *
   * @throws IOException if the response stream fails
   */
  @Test
  public void processDownloadRequestDefaultsToOctetStream() throws IOException {
    String id = LegacyFileDownload.store(session, new LegacyFileDownload(BINARY_CONTENT, null,
        ATTACHMENT_BANK_FILE));

    servlet.processDownloadRequest(downloadRequest(id), response);

    verify(response).setContentType("application/octet-stream");
  }

  /**
   * Tests that an unknown file identifier answers 404 without writing a body.
   *
   * @throws IOException if the error response fails
   */
  @Test
  public void processDownloadRequestAnswersNotFoundForUnknownFile() throws IOException {
    servlet.processDownloadRequest(downloadRequest("unknown"), response);

    verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
    verify(response, never()).getOutputStream();
  }

  /**
   * Tests that a captured file wins over the redirect the servlet sends after streaming it
   * (Intrastat Generate File), and that binary bytes are kept intact.
   *
   * @throws Exception if the reflective call fails
   */
  @Test
  public void handleResponseDeliversFileBeforeRedirect() throws Exception {
    HttpServletResponseLegacyWrapper wrapper = fileWrapper("application/txt", "inline; filename=decl.txt");
    wrapper.sendRedirect("INTR_GenerateFile.html?Command=DEFAULT");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(true)).thenReturn(session);

    invokeHandleResponseWithin(request, wrapper);

    String html = written.toString();
    assertSuccessForwarder(html);
    assertTrue(html.contains("decl.txt"));
    assertFalse(html.contains("INTR_GenerateFile.html"));
    assertArrayEquals(BINARY_CONTENT, LegacyFileDownload.take(session, storedFileId(html)).getContent());
  }

  /**
   * Tests that a Classic PopUp_Download page is replaced by the forwarder to its download URL.
   *
   * @throws Exception if the reflective call fails
   */
  @Test
  public void handleResponseForwardsDownloadPage() throws Exception {
    HttpServletResponseLegacyWrapper wrapper = new HttpServletResponseLegacyWrapper(mock(HttpServletResponse.class));
    wrapper.getWriter().write("<html><body onload=\"onLoadDo('" + DOWNLOAD_REPORT_URL + "');\"></body></html>");

    invokeHandleResponse(wrapper);

    String html = written.toString();
    assertSuccessForwarder(html);
    assertTrue(html.contains("DownloadReport.html?report=Rec-1.pdf"));
  }

  /**
   * Tests that the PopUp_Response answered to the opening request ({@code Command=DEFAULT}) is
   * detected, with and without a tab URL.
   */
  @Test
  public void isClosePopupOnOpenDetectsPopUpResponseOnOpening() {
    assertTrue(LegacyProcessServlet.isClosePopupOnOpen(POPUP_RESPONSE_BODY, COMMAND_DEFAULT));
    assertTrue(LegacyProcessServlet.isClosePopupOnOpen(
        "<body onload=\"submitThisPage(null);\" id=\"paramHref\">", COMMAND_DEFAULT));
  }

  /**
   * Tests that later submits, missing commands (multipart submits) and regular pages are ignored.
   */
  @Test
  public void isClosePopupOnOpenIgnoresOtherRequestsAndPages() {
    assertFalse(LegacyProcessServlet.isClosePopupOnOpen(POPUP_RESPONSE_BODY, "IMPORT"));
    assertFalse(LegacyProcessServlet.isClosePopupOnOpen(POPUP_RESPONSE_BODY, null));
    assertFalse(LegacyProcessServlet.isClosePopupOnOpen("<body onload=\"init();\"><form></form>", COMMAND_DEFAULT));
    assertFalse(LegacyProcessServlet.isClosePopupOnOpen(null, COMMAND_DEFAULT));
  }

  /**
   * Tests that a PopUp_Response answered when opening the popup (e.g. Import Statement without
   * bank file formats) is replaced by a forwarder that asks the new UI to read the tab message.
   *
   * @throws Exception if the reflective call fails
   */
  @Test
  public void handleResponseForwardsProcessOrderWhenPopupClosesOnOpen() throws Exception {
    HttpServletResponseLegacyWrapper wrapper = new HttpServletResponseLegacyWrapper(mock(HttpServletResponse.class));
    wrapper.getWriter().write("<html><body " + POPUP_RESPONSE_BODY.substring("<body ".length()) + "</html>");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("Command")).thenReturn(COMMAND_DEFAULT);

    invokeHandleResponseWithin(request, wrapper);

    String html = written.toString();
    assertTrue(html.contains("action:'processOrder'"));
    assertFalse(html.contains("submitThisPage"));
    verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
  }

  /**
   * Tests that the injected submit hook retargets {@code _top}/{@code _parent} forms to the
   * popup iframe itself, as {@code _top} is the popup window in Classic.
   *
   * @throws ReflectiveOperationException if the script constant cannot be read
   */
  @Test
  public void postMessageScriptRetargetsTopFormsToSelf() throws ReflectiveOperationException {
    Field field = LegacyProcessServlet.class.getDeclaredField("POST_MESSAGE_SCRIPT");
    field.setAccessible(true);
    String script = (String) field.get(null);

    assertTrue(script.contains("this.target==='_top'||this.target==='_parent'))this.target='_self'"));
  }

  /**
   * Sanity check that the binary fixture is not valid UTF-8, so the tests above would fail
   * if the bytes were decoded to a String.
   */
  @Test
  public void binaryFixtureIsNotUtf8() {
    byte[] roundTrip = new String(BINARY_CONTENT, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);

    assertFalse(Arrays.equals(BINARY_CONTENT, roundTrip));
  }
}
