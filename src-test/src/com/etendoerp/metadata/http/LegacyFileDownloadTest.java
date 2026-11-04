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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link LegacyFileDownload}: file name extraction and the single-use
 * session storage used by the legacy process popup downloads.
 */
public class LegacyFileDownloadTest {

  private static final String BANK_FILE = "BANK.DAT";
  private static final String APPLICATION_RTF = "application/rtf";
  private static final String ATTACHMENT_PREFIX = "attachment; filename=";
  private static final byte[] CONTENT = "line".getBytes(StandardCharsets.UTF_8);

  /**
   * Builds a session mock backed by a map, so attributes can be stored and removed.
   *
   * @return the session mock
   */
  static HttpSession mapBackedSession() {
    Map<String, Object> attributes = new HashMap<>();
    HttpSession session = Mockito.mock(HttpSession.class);
    Mockito.doAnswer(inv -> attributes.put(inv.getArgument(0), inv.getArgument(1)))
        .when(session).setAttribute(Mockito.anyString(), Mockito.any());
    Mockito.when(session.getAttribute(Mockito.anyString()))
        .thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));
    Mockito.doAnswer(inv -> attributes.remove(inv.<String>getArgument(0)))
        .when(session).removeAttribute(Mockito.anyString());
    return session;
  }

  private static LegacyFileDownload file(String contentDisposition) {
    return new LegacyFileDownload(CONTENT, APPLICATION_RTF, contentDisposition);
  }

  /**
   * Tests that an unquoted file name is extracted.
   */
  @Test
  public void getFileNameReturnsUnquotedName() {
    assertEquals(BANK_FILE, file(ATTACHMENT_PREFIX + BANK_FILE).getFileName());
  }

  /**
   * Tests that a quoted file name is extracted without the quotes.
   */
  @Test
  public void getFileNameReturnsQuotedName() {
    assertEquals(BANK_FILE, file(ATTACHMENT_PREFIX + "\"" + BANK_FILE + "\"").getFileName());
  }

  /**
   * Tests that a disposition without file name yields an empty name.
   */
  @Test
  public void getFileNameReturnsEmptyWithoutFileName() {
    assertEquals("", file("attachment").getFileName());
  }

  /**
   * Tests that a missing disposition yields an empty name.
   */
  @Test
  public void getFileNameReturnsEmptyWhenDispositionIsNull() {
    assertEquals("", file(null).getFileName());
  }

  /**
   * Tests that the content and headers are kept as given.
   */
  @Test
  public void keepsContentAndHeaders() {
    LegacyFileDownload download = file(ATTACHMENT_PREFIX + BANK_FILE);

    assertArrayEquals(CONTENT, download.getContent());
    assertEquals(APPLICATION_RTF, download.getContentType());
    assertEquals(ATTACHMENT_PREFIX + BANK_FILE, download.getContentDisposition());
  }

  /**
   * Tests that a stored file is returned once and then removed from the session.
   */
  @Test
  public void takeReturnsStoredFileOnlyOnce() {
    HttpSession session = mapBackedSession();
    LegacyFileDownload download = file(ATTACHMENT_PREFIX + BANK_FILE);

    String id = LegacyFileDownload.store(session, download);

    assertNotNull(id);
    assertSame(download, LegacyFileDownload.take(session, id));
    assertNull(LegacyFileDownload.take(session, id));
  }

  /**
   * Tests that identifiers that are not UUIDs are rejected.
   */
  @Test
  public void takeReturnsNullForInvalidId() {
    HttpSession session = mapBackedSession();

    assertNull(LegacyFileDownload.take(session, "../other"));
    assertNull(LegacyFileDownload.take(session, null));
  }

  /**
   * Tests that an unknown identifier yields no file.
   */
  @Test
  public void takeReturnsNullForUnknownId() {
    assertNull(LegacyFileDownload.take(mapBackedSession(), "123e4567-e89b-12d3-a456-426614174000"));
  }

  /**
   * Tests that a missing session yields no file.
   */
  @Test
  public void takeReturnsNullWithoutSession() {
    assertNull(LegacyFileDownload.take(null, "123e4567-e89b-12d3-a456-426614174000"));
  }
}
