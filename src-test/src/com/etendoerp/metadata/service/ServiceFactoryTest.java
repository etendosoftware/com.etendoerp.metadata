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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceFactoryTest {
  private static final String EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE  = "143|C_ORDER_ID";
  private static final String SERVICE_NOT_NULL = "Service should not be null";

  @Mock
  private HttpServletRequest mockRequest;

  @Mock
  private HttpServletResponse mockResponse;

  @Test
  void getServiceReturnsSessionService() {
    HttpServletRequest req = mockRequestWithPath("/com.etendoerp.metadata.meta/session");
    HttpServletResponse res = mock(HttpServletResponse.class);

    MetadataService service = ServiceFactory.getService(req, res);
    assertInstanceOf(SessionService.class, service);
  }

  @Test
  void getServiceReturnsMenuService() {
    HttpServletRequest req = mockRequestWithPath("/com.etendoerp.metadata.meta/menu");
    HttpServletResponse res = mock(HttpServletResponse.class);

    MetadataService service = ServiceFactory.getService(req, res);
    assertInstanceOf(MenuService.class, service);
  }

  @Test
  void getServiceReturnsLegacyServiceForLegacyPath() {
    HttpServletRequest req = mockRequestWithPath("/com.etendoerp.metadata.meta/legacy/test");
    HttpServletResponse res = mock(HttpServletResponse.class);

    MetadataService service = ServiceFactory.getService(req, res);
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
    HttpServletRequest req = mockRequestWithPath("/com.etendoerp.metadata.meta/unknown");
    HttpServletResponse res = mock(HttpServletResponse.class);

    assertThrows(NotFoundException.class, () -> ServiceFactory.getService(req, res));
  }

  // -------- Tests for buildLegacyForwardService --------

  @Test
  void buildLegacyForwardServiceForwardsSuccessfully() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);

    when(req.getServletContext()).thenReturn(context);
    when(req.getSession(true)).thenReturn(session);
    when(req.getParameter("recordId")).thenReturn("A123");
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE)).thenReturn(true);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertNotNull(service);

      // execute process() method
      service.process();

      verify(session).setAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE, "A123");
      verify(dispatcher).forward(req, res);
    }
  }

  @Test
  void buildLegacyForwardServiceThrowsWhenAttributeNotAllowed() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);

    when(req.getSession(true)).thenReturn(session);
    when(req.getParameter("recordId")).thenReturn("A123");

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE)).thenReturn(false);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertThrows(InternalServerException.class, service::process);
    }
  }

  @Test
  void buildLegacyForwardServiceThrowsWhenDispatcherIsNull() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    ServletContext context = mock(ServletContext.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(null);
    when(req.getSession(true)).thenReturn(session);

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE)).thenReturn(true);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertThrows(InternalServerException.class, service::process);
    }
  }

  @Test
  void buildLegacyForwardServiceCatchesExceptionFromDispatcher() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    ServletContext context = mock(ServletContext.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);
    when(req.getSession(true)).thenReturn(session);

    doThrow(new IOException("fail")).when(dispatcher).forward(req, res);

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE)).thenReturn(true);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertThrows(InternalServerException.class, service::process);
    }
  }

  /**
   * Tests that ServiceFactory returns SessionService for session path.
   */
  @Test
  public void testGetSessionService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/session");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return SessionService", service instanceof SessionService);
  }

  /**
   * Tests that ServiceFactory returns MenuService for menu path.
   */
  @Test
  public void testGetMenuService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/menu");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return MenuService", service instanceof MenuService);
  }

  /**
   * Tests that ServiceFactory returns WindowService for window path.
   */
  @Test
  public void testGetWindowService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/window/123");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return WindowService", service instanceof WindowService);
  }

  /**
   * Tests that ServiceFactory returns TabService for tab path.
   */
  @Test
  public void testGetTabService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/tab/456");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return TabService", service instanceof TabService);
  }

  /**
   * Tests that ServiceFactory returns LanguageService for language path.
   */
  @Test
  public void testGetLanguageService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/language/en_US");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return LanguageService", service instanceof LanguageService);
  }

  /**
   * Tests that ServiceFactory returns MessageService for message path.
   */
  @Test
  public void testGetMessageService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/message");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return MessageService", service instanceof MessageService);
  }

  /**
   * Tests that ServiceFactory returns LabelsService for labels path.
   */
  @Test
  public void testGetLabelsService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/labels");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return LabelsService", service instanceof LabelsService);
  }

  /**
   * Tests that ServiceFactory returns LocationMetadataService for location path.
   */
  @Test
  public void testGetLocationMetadataService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/location/test");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return LocationMetadataService", service instanceof LocationMetadataService);
  }

  /**
   * Tests that ServiceFactory returns ToolbarService for toolbar path.
   */
  @Test
  public void testGetToolbarService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/toolbar");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return ToolbarService", service instanceof ToolbarService);
  }

  /**
   * Tests that ServiceFactory returns LegacyService for legacy path.
   */
  @Test
  public void testGetLegacyService() {
    when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/legacy/test");

    MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);

    assertNotNull(SERVICE_NOT_NULL, service);
    assertTrue("Should return LegacyService", service instanceof LegacyService);
  }

  // ---------- Helpers ----------

  private static MetadataService invokeBuildLegacyForwardService(HttpServletRequest req, HttpServletResponse res, String path) {
    try {
      var method = ServiceFactory.class.getDeclaredMethod("buildLegacyForwardService", HttpServletRequest.class, HttpServletResponse.class, String.class);
      method.setAccessible(true);
      return (MetadataService) method.invoke(null, req, res, path);
    } catch (Exception e) {
      throw new InternalServerException("Failed to forward legacy request: " + e.getMessage());
    }
  }

  private static HttpServletRequest mockRequestWithPath(String pathInfo) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getPathInfo()).thenReturn(pathInfo);
    return req;
  }
}
/**
 * Test class for ServiceFactory.
 * Tests the factory pattern implementation for creating appropriate service instances.
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceFactoryTest {

}
>>>>>>> 552c576adf0a922807e65d6a253a2c3220878b61
