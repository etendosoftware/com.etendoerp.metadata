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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for the Content-Type / Content-Disposition capture of
 * {@link HttpServletResponseLegacyWrapper}, used to detect the files answered by
 * Classic servlets included in the legacy process popup.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class HttpServletResponseLegacyWrapperFileCaptureTest {

  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String ATTACHMENT_BANK_FILE = "attachment; filename=BANK.DAT";
  private static final String INLINE_TXT_FILE = "inline; filename=declaration.txt";
  private static final String TEXT_HTML = "text/html;charset=UTF-8";
  private static final String APPLICATION_TXT = "application/txt";
  private static final String APPLICATION_RTF = "application/rtf";

  @Mock
  private HttpServletResponse mockResponse;

  private HttpServletResponseLegacyWrapper wrapper;

  /**
   * Creates a fresh wrapper around the mocked response for each test.
   */
  @Before
  public void setUp() {
    wrapper = new HttpServletResponseLegacyWrapper(mockResponse);
  }

  /**
   * Tests that the declared content type is recorded and still delegated.
   */
  @Test
  public void setContentTypeRecordsAndDelegatesTheType() {
    wrapper.setContentType(APPLICATION_RTF);

    assertEquals(APPLICATION_RTF, wrapper.getCapturedContentType());
    verify(mockResponse).setContentType(APPLICATION_RTF);
  }

  /**
   * Tests that {@code setHeader} records the Content-Disposition regardless of the
   * header name case (Intrastat uses {@code Content-disposition}) and delegates it.
   */
  @Test
  public void setHeaderRecordsContentDispositionIgnoringCase() {
    wrapper.setHeader("Content-disposition", INLINE_TXT_FILE);

    assertEquals(INLINE_TXT_FILE, wrapper.getCapturedContentDisposition());
    verify(mockResponse).setHeader("Content-disposition", INLINE_TXT_FILE);
  }

  /**
   * Tests that {@code addHeader} records the Content-Disposition and delegates it.
   */
  @Test
  public void addHeaderRecordsContentDisposition() {
    wrapper.addHeader(CONTENT_DISPOSITION, ATTACHMENT_BANK_FILE);

    assertEquals(ATTACHMENT_BANK_FILE, wrapper.getCapturedContentDisposition());
    verify(mockResponse).addHeader(CONTENT_DISPOSITION, ATTACHMENT_BANK_FILE);
  }

  /**
   * Tests that headers other than Content-Disposition are not recorded.
   */
  @Test
  public void otherHeadersAreNotRecorded() {
    wrapper.setHeader("Cache-Control", "no-cache");
    wrapper.addHeader("Pragma", "no-cache");

    assertNull(wrapper.getCapturedContentDisposition());
  }

  /**
   * Tests that an attachment is a file even when declared as HTML.
   */
  @Test
  public void isFileResponseWhenAttachment() {
    wrapper.setContentType(TEXT_HTML);
    wrapper.setHeader(CONTENT_DISPOSITION, " Attachment; filename=page.html");

    assertTrue(wrapper.isFileResponse());
  }

  /**
   * Tests that an inline disposition of a non-HTML type is a file (Intrastat case).
   */
  @Test
  public void isFileResponseWhenInlineNonHtml() {
    wrapper.setContentType(APPLICATION_TXT);
    wrapper.setHeader(CONTENT_DISPOSITION, INLINE_TXT_FILE);

    assertTrue(wrapper.isFileResponse());
  }

  /**
   * Tests that a disposition without a declared content type is a file.
   */
  @Test
  public void isFileResponseWhenDispositionWithoutContentType() {
    wrapper.setHeader(CONTENT_DISPOSITION, INLINE_TXT_FILE);

    assertTrue(wrapper.isFileResponse());
  }

  /**
   * Tests that the inline HTML page rendered by Classic {@code renderJR} is not a file.
   */
  @Test
  public void isNotFileResponseWhenInlineHtml() {
    wrapper.setContentType(TEXT_HTML);
    wrapper.setHeader(CONTENT_DISPOSITION, "inline; filename=Report-1.html");

    assertFalse(wrapper.isFileResponse());
  }

  /**
   * Tests that a response without Content-Disposition is never a file.
   */
  @Test
  public void isNotFileResponseWithoutDisposition() {
    wrapper.setContentType(APPLICATION_TXT);

    assertFalse(wrapper.isFileResponse());
  }

  /**
   * Tests that {@code reset()} clears the recorded headers.
   */
  @Test
  public void resetClearsCapturedHeaders() {
    wrapper.setContentType(APPLICATION_RTF);
    wrapper.setHeader(CONTENT_DISPOSITION, ATTACHMENT_BANK_FILE);

    wrapper.reset();

    assertNull(wrapper.getCapturedContentType());
    assertNull(wrapper.getCapturedContentDisposition());
    assertFalse(wrapper.isFileResponse());
  }
}
