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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServiceFactory}.
 * Tests that the correct MetadataService implementation is returned for each path,
 * and validates behavior of legacy forward handling.
 */
@ExtendWith(MockitoExtension.class)
class ServiceFactoryTest {

  private static final String EXAMPLE_MUTABLE_SESSION_ATTRIBUTE = "143|C_ORDER_ID";
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
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE)).thenReturn(true);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertNotNull(service);

      service.process();

      verify(session).setAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE, "A123");
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
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE)).thenReturn(false);

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
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE)).thenReturn(true);

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
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE)).thenReturn(true);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      assertThrows(InternalServerException.class, service::process);
    }
  }


  /** Helper to create a mock HttpServletRequest with the specified path info. */
  private HttpServletRequest mockRequestWithPath(String path) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getPathInfo()).thenReturn(path);
    return req;
  }

  /**
   * Invokes the private static method buildLegacyForwardService using reflection.
   */
  private MetadataService invokeBuildLegacyForwardService(HttpServletRequest req, HttpServletResponse res, String path) {
    try {
      var method = ServiceFactory.class.getDeclaredMethod("buildLegacyForwardService", HttpServletRequest.class, HttpServletResponse.class, String.class);
      method.setAccessible(true);
      return (MetadataService) method.invoke(null, req, res, path);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke buildLegacyForwardService", e);
    }
  }
}
