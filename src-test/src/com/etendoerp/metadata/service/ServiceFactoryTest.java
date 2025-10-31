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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceFactoryTest {

  private final String EXAMPLE_MUTABLE_SESSION_ATTRIBUTTE  = "143|C_ORDER_ID";

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
