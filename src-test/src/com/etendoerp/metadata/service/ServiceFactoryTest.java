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
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

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
  private static final String PARAM_WINDOW_ID = "windowId";
  private static final String PARAM_ENTITY_NAME = "entityName";
  private static final String PARAM_RECORD_ID = "recordId";
  private static final String ENTITY_ORDER = "Order";
  private static final String RECORD_ID_VALUE = "RECORD_1";
  private static final String NON_USED_BY_LINK_PATH = "/some/legacy/path";

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
  void getServiceReturnsProcessMetadataService() {
    MetadataService service = ServiceFactory.getService(mockRequestWithPath("/com.etendoerp.metadata.meta/process/123"), mockResponse);
    assertNotNull(service, SERVICE_NOT_NULL);
    assertInstanceOf(ProcessMetadataService.class, service);
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
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);

    MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
    assertNotNull(service);

    service.process();

    verify(dispatcher).forward(req, res);
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
    ServletContext context = mock(ServletContext.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(null);

    MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
    assertThrows(ServletException.class, service::process);
  }

  @Test
  void buildLegacyForwardServiceCatchesExceptionFromDispatcher() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    ServletContext context = mock(ServletContext.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);
    doThrow(new IOException("fail")).when(dispatcher).forward(req, res);

    MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
    assertThrows(IOException.class, service::process);
  }


  @Test
  void handleLegacySessionSetsAttributeWhenAllParamsPresent() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);
    Entity entity = mock(Entity.class);
    Property idProp = mock(Property.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);
    when(req.getSession(true)).thenReturn(session);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(List.of(idProp));
    when(idProp.getColumnName()).thenReturn("C_Order_ID");

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(session).setAttribute("143|C_Order_ID", RECORD_ID_VALUE);
      verify(dispatcher).forward(req, res);
    }
  }

  @Test
  void handleLegacySessionSkipsWhenEntityNotFound() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn("Unknown");
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity("Unknown")).thenReturn(null);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(dispatcher).forward(req, res);
    }
  }

  @Test
  void handleLegacySessionSkipsWhenMultipleIdProperties() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);
    Entity entity = mock(Entity.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(dispatcher);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(List.of(mock(Property.class), mock(Property.class)));

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      MetadataService service = invokeBuildLegacyForwardService(req, res, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(dispatcher).forward(req, res);
    }
  }

  @Test
  void buildLegacyForwardServiceSkipsSessionForNonUsedByLinkPath() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    ServletContext context = mock(ServletContext.class);

    when(req.getServletContext()).thenReturn(context);
    when(context.getRequestDispatcher(NON_USED_BY_LINK_PATH)).thenReturn(dispatcher);

    MetadataService service = invokeBuildLegacyForwardService(req, res, NON_USED_BY_LINK_PATH);
    service.process();

    verify(dispatcher).forward(req, res);
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
      throw new InternalServerException("Failed to invoke buildLegacyForwardService" + e.getMessage());
    }
  }
}
