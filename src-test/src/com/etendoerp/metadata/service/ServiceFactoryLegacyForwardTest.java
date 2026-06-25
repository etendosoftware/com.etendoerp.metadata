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
import com.etendoerp.metadata.utils.LegacyPaths;
import com.etendoerp.metadata.utils.LegacyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServiceFactory} legacy forward handling.
 */
@ExtendWith(MockitoExtension.class)
class ServiceFactoryLegacyForwardTest {

  private static final String EXAMPLE_MUTABLE_SESSION_ATTRIBUTE = "143|C_ORDER_ID";
  private static final String PARAM_WINDOW_ID = "windowId";
  private static final String PARAM_ENTITY_NAME = "entityName";
  private static final String PARAM_RECORD_ID = "recordId";
  private static final String ENTITY_ORDER = "Order";
  private static final String RECORD_ID_VALUE = "RECORD_1";
  @SuppressWarnings("java:S1075")
  private static final String NON_USED_BY_LINK_PATH = "/some/legacy/path";

  @Mock
  private HttpServletRequest legacyReq;

  @Mock
  private HttpServletResponse legacyRes;

  @Mock
  private RequestDispatcher legacyDispatcher;

  @Mock
  private ServletContext legacyServletContext;

  @BeforeEach
  void setUpLegacyMocks() {
    lenient().when(legacyReq.getServletContext()).thenReturn(legacyServletContext);
    lenient().when(legacyServletContext.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(legacyDispatcher);
  }

  @Test
  void buildLegacyForwardServiceForwardsSuccessfully() throws Exception {
    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
    assertNotNull(service);

    service.process();

    verify(legacyDispatcher).forward(legacyReq, legacyRes);
  }

  @Test
  void buildLegacyForwardServiceThrowsWhenAttributeNotAllowed() {
    HttpSession session = mock(HttpSession.class);

    when(legacyReq.getSession(true)).thenReturn(session);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn("A123");

    try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class)) {
      legacy.when(() -> LegacyUtils.isMutableSessionAttribute(EXAMPLE_MUTABLE_SESSION_ATTRIBUTE)).thenReturn(false);

      MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
      assertThrows(InternalServerException.class, service::process);
    }
  }

  @Test
  void buildLegacyForwardServiceThrowsWhenDispatcherIsNull() {
    when(legacyServletContext.getRequestDispatcher(LegacyPaths.USED_BY_LINK)).thenReturn(null);

    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
    assertThrows(ServletException.class, service::process);
  }

  @Test
  void buildLegacyForwardServiceCatchesExceptionFromDispatcher() throws Exception {
    doThrow(new IOException("fail")).when(legacyDispatcher).forward(legacyReq, legacyRes);

    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
    assertThrows(IOException.class, service::process);
  }

  @Test
  void handleLegacySessionSetsAttributeWhenAllParamsPresent() throws Exception {
    HttpSession session = mock(HttpSession.class);
    Entity entity = mock(Entity.class);
    Property idProp = mock(Property.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(legacyReq.getSession(true)).thenReturn(session);
    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(List.of(idProp));
    when(idProp.getColumnName()).thenReturn("C_Order_ID");

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(session).setAttribute("143|C_Order_ID", RECORD_ID_VALUE);
      verify(legacyDispatcher).forward(legacyReq, legacyRes);
    }
  }

  @Test
  void handleLegacySessionSkipsWhenEntityNotFound() throws Exception {
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn("Unknown");
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity("Unknown")).thenReturn(null);

      MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(legacyDispatcher).forward(legacyReq, legacyRes);
    }
  }

  @Test
  void handleLegacySessionSkipsWhenMultipleIdProperties() throws Exception {
    Entity entity = mock(Entity.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(List.of(mock(Property.class), mock(Property.class)));

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(legacyDispatcher).forward(legacyReq, legacyRes);
    }
  }

  @Test
  void buildLegacyForwardServiceSkipsSessionForNonUsedByLinkPath() throws Exception {
    when(legacyServletContext.getRequestDispatcher(NON_USED_BY_LINK_PATH)).thenReturn(legacyDispatcher);

    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, NON_USED_BY_LINK_PATH);
    service.process();

    verify(legacyDispatcher).forward(legacyReq, legacyRes);
  }

  @Test
  void handleLegacySessionSkipsWhenIdPropsIsNull() throws Exception {
    Entity entity = mock(Entity.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(null);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
      service.process();

      verify(legacyDispatcher).forward(legacyReq, legacyRes);
    }
  }

  @Test
  void handleLegacySessionSkipsWhenWindowIdIsNull() throws Exception {
    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn(null);
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
    service.process();

    verify(legacyDispatcher).forward(legacyReq, legacyRes);
  }

  @Test
  void handleLegacySessionSkipsWhenRecordIdIsNull() throws Exception {
    when(legacyReq.getParameter(PARAM_WINDOW_ID)).thenReturn("143");
    when(legacyReq.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(legacyReq.getParameter(PARAM_RECORD_ID)).thenReturn(null);

    MetadataService service = invokeBuildLegacyForwardService(legacyReq, legacyRes, LegacyPaths.USED_BY_LINK);
    service.process();

    verify(legacyDispatcher).forward(legacyReq, legacyRes);
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
