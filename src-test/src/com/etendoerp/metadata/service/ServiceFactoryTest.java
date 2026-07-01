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
package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.LegacyPaths;
import com.etendoerp.metadata.utils.LegacyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServiceFactory}.
 * Tests that the correct MetadataService implementation is returned for each path,
 * and validates behavior of legacy forward handling.
 */
@ExtendWith(MockitoExtension.class)
class ServiceFactoryTest {

  private static final String SERVICE_NOT_NULL = "Service should not be null";

  @Mock
  private HttpServletRequest mockRequest;

  @Mock
  private HttpServletResponse mockResponse;

  @Test
  void getServiceReturnsSessionService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/session"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(SessionService.class, service);
  }

  @Test
  void getServiceReturnsMenuService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/menu"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(MenuService.class, service);
  }

  @Test
  void getServiceReturnsWindowService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/window/123"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(WindowService.class, service);
  }

  @Test
  void getServiceReturnsTabService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/tab/456"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(TabService.class, service);
  }

  @Test
  void getServiceReturnsLanguageService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/language/en_US"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(LanguageService.class, service);
  }

  @Test
  void getServiceReturnsMessageService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/message"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(MessageService.class, service);
  }

  @Test
  void getServiceReturnsLabelsService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/labels"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(LabelsService.class, service);
  }

  @Test
  void getServiceReturnsLocationMetadataService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/location/test"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(LocationMetadataService.class, service);
  }

  @Test
  void getServiceReturnsToolbarService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/toolbar"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(ToolbarService.class, service);
  }

  @Test
  void getServiceReturnsSavedViewService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/saved-views/abc-123"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(SavedViewService.class, service);
  }

  @Test
  void getServiceReturnsSavedViewServiceForBasePathWithoutId() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/saved-views"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(SavedViewService.class, service);
  }

  @Test
  void getServiceReturnsProcessMetadataService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/process/123"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(ProcessMetadataService.class, service);
  }

  @Test
  void getServiceReturnsPreferencesService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/preferences"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(PreferencesService.class, service);
  }

  @Test
  void getServiceReturnsEmailSendService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/email/send"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(EmailSendService.class, service);
  }

  @Test
  void getServiceReturnsEmailConfigService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/email/config"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(EmailConfigService.class, service);
  }

  @Test
  void getServiceReturnsEmailAttachmentService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/email/attachments"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(EmailAttachmentService.class, service);
  }

  @Test
  void getServiceReturnsEmailService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/email/other"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(EmailService.class, service);
  }

  @Test
  void getServiceReturnsReportAndProcessService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/report-and-process/123"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(ReportAndProcessService.class, service);
  }

  @Test
  void getServiceReturnsProcessExecutionService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/process-execution"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(ProcessExecutionService.class, service);
  }

  @Test
  void getServiceReturnsLegacyService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/legacy/test"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(LegacyService.class, service);
  }

  @Test
  void getServiceReturnsForwardServiceWhenLegacyUtilsMatches() {
    HttpServletRequest req = mockRequestWithPath(LegacyPaths.USED_BY_LINK);
    HttpServletResponse res = mock(HttpServletResponse.class);

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isLegacyPath(LegacyPaths.USED_BY_LINK)).thenReturn(true);
      MetadataService service = ServiceFactory.getService(req, res);
      assertNotNull(service);
      assertEquals(MetadataService.class, service.getClass().getSuperclass());
    }
  }

  @Test
  void getServiceThrowsNotFoundForUnknownPath() {
    assertThrows(NotFoundException.class, () ->
            ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/unknown"), mockResponse)
    );
  }

  @Test
  void getServiceThrowsNotFoundForNullOrEmptyPath() {
    assertThrows(NotFoundException.class, () ->
            ServiceFactory.getService(mockRequestWithPath(null), mockResponse)
    );
    assertThrows(NotFoundException.class, () ->
            ServiceFactory.getService(mockRequestWithPath(""), mockResponse)
    );
    assertThrows(NotFoundException.class, () ->
            ServiceFactory.getService(mockRequestWithPath("/"), mockResponse)
    );
  }



  @Test
  void normalizePathStripsMetaAndSwsPrefixes() {
    assertEquals("/menu", ServiceFactory.normalizePath(mockRequestWithPath("/com.etendoerp.metadata.meta/menu")));
    assertEquals("/menu", ServiceFactory.normalizePath(mockRequestWithPath("/com.etendoerp.metadata.sws/menu")));
    assertEquals("", ServiceFactory.normalizePath(mockRequestWithPath(null)));
  }

  /** Helper to create a mock HttpServletRequest with the specified path info. */
  private HttpServletRequest mockRequestWithPath(String path) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getPathInfo()).thenReturn(path);
    return req;
  }

}
