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
package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.ui.Tab;
import java.util.List;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

import javax.servlet.http.HttpServletRequest;

/**
 * Unit tests for {@link LegacyUtils}.
 * Tests all public utility methods of the LegacyUtils class.
 */
@ExtendWith(MockitoExtension.class)
class LegacyUtilsTest {

  private static final String LEGACY_PROCESS_NAME = "Legacy Process Placeholder";
  private static final String TABLE_ID = "319";
  private static final String WINDOW_ID = "169";
  private static final String ENTITY_ORDER = "Order";
  private static final String COLUMN_ORDER_ID = "C_Order_ID";
  private static final String ENTITY_UNKNOWN = "Unknown";
  private static final String PARAM_WINDOW_ID = "windowId";
  private static final String PARAM_ENTITY_NAME = "entityName";
  private static final String PARAM_RECORD_ID = "recordId";
  private static final String RECORD_ID_VALUE = "RECORD_1";
  private static final String USED_BY_LINK_WINDOW_ID = "143";

  /**
   * Tests the getLegacyProcess method to ensure it creates and populates a Process instance correctly.
   */
  @Test
  void getLegacyProcessCreatesStubbedProcess() {
    String processId = "3663";
    Process mockProcess = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class)).thenReturn(mockProcess);

      Process result = LegacyUtils.getLegacyProcess(processId);

      assertNotNull(result);
      verify(mockProcess).setId(processId);
      verify(mockProcess).setName(LEGACY_PROCESS_NAME);
      verify(mockProcess).setActive(true);
    }
  }

  @Test
  void getLegacyProcessReturnsConfiguredProcess() {
    String testProcessId = "TEST123";
    Process mockProcess = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class)).thenReturn(mockProcess);

      Process result = LegacyUtils.getLegacyProcess(testProcessId);

      assertNotNull(result, "Legacy process should not be null");
      verify(mockProcess).setId(testProcessId);
      verify(mockProcess).setName(LEGACY_PROCESS_NAME);
      verify(mockProcess).setActive(true);
    }
  }

  @Test
  void getLegacyProcessWithDifferentIdsCreatesDifferentInstances() {
    Process mockProcess1 = mock(Process.class);
    Process mockProcess2 = mock(Process.class);

    try (MockedStatic<OBProvider> mockedOBProvider = mockStatic(OBProvider.class)) {
      OBProvider mockProvider = mock(OBProvider.class);
      when(OBProvider.getInstance()).thenReturn(mockProvider);
      when(mockProvider.get(Process.class))
              .thenReturn(mockProcess1)
              .thenReturn(mockProcess2);

      Process result1 = LegacyUtils.getLegacyProcess("ID1");
      Process result2 = LegacyUtils.getLegacyProcess("ID2");

      assertNotNull(result1);
      assertNotNull(result2);

      verify(mockProcess1).setId("ID1");
      verify(mockProcess2).setId("ID2");
      verify(mockProcess1).setName(LEGACY_PROCESS_NAME);
      verify(mockProcess2).setName(LEGACY_PROCESS_NAME);
    }
  }

  /**
   * Tests the isLegacyPath method for a path that exists in the legacy paths set.
   */
  @Test
  void isLegacyPathWithExistingPathReturnsTrue() {
    assertTrue(LegacyUtils.isLegacyPath(LegacyPaths.USED_BY_LINK));
  }

  /**
   * Tests the isLegacyPath method for a path that does not exist in the legacy paths set.
   */
  @Test
  void isLegacyPathWithNonExistingPathReturnsFalse() {
    assertFalse(LegacyUtils.isLegacyPath("/not/legacy/path.html"));
  }

  /**
   * Tests the isMutableSessionAttribute method for an attribute that exists in the mutable session attributes set.
   */
  @Test
  void isMutableSessionAttributeWithExistingAttributeReturnsTrue() {
    assertTrue(LegacyUtils.isMutableSessionAttribute("143|C_ORDER_ID"));
  }

  /**
   * Tests the isMutableSessionAttribute method for an attribute that does not exist in the mutable session attributes set.
   */
  @Test
  void isMutableSessionAttributeWithNonExistingAttributeReturnsFalse() {
    assertFalse(LegacyUtils.isMutableSessionAttribute("143|C_INVOICE_ID"));
  }

  /**
   * Tests that the legacy paths and mutable session attributes sets are initialized.
   */
  @Test
  void staticSetsAreInitializedCorrectly() throws Exception {
    // Using reflection to verify that sets are initialized
    var pathsField = LegacyUtils.class.getDeclaredField("LEGACY_PATHS");
    var attributesField = LegacyUtils.class.getDeclaredField("MUTABLE_SESSION_ATTRIBUTES");

    pathsField.setAccessible(true);
    attributesField.setAccessible(true);

    Set<?> paths = (Set<?>) pathsField.get(null);
    Set<?> attributes = (Set<?>) attributesField.get(null);

    assertNotNull(paths);
    assertNotNull(attributes);

    assertFalse(paths.isEmpty());
    assertFalse(attributes.isEmpty());
  }

  @Test
  void isMutableSessionAttributeCreateFromTabIdReturnsTrue() {
    assertTrue(LegacyUtils.isMutableSessionAttribute("CREATEFROM|TABID"));
  }

  @Test
  void findTabIdByWindowAndTableReturnsNullForNullWindowId() {
    assertNull(LegacyUtils.findTabIdByWindowAndTable(null, TABLE_ID));
  }

  @Test
  void findTabIdByWindowAndTableReturnsNullForNullTableId() {
    assertNull(LegacyUtils.findTabIdByWindowAndTable(WINDOW_ID, null));
  }

  @Test
  void findTabIdByWindowAndTableReturnsNullForEmptyWindowId() {
    assertNull(LegacyUtils.findTabIdByWindowAndTable("", TABLE_ID));
  }

  @SuppressWarnings("unchecked")
  @Test
  void findTabIdByWindowAndTableReturnsTabIdWhenFound() {
    Tab mockTab = mock(Tab.class);
    when(mockTab.getId()).thenReturn("257");

    OBQuery<Tab> mockQuery = mock(OBQuery.class);
    when(mockQuery.list()).thenReturn(List.of(mockTab));

    OBDal mockDal = mock(OBDal.class);
    when(mockDal.createQuery(eq(Tab.class), anyString())).thenReturn(mockQuery);

    try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class)) {
      mockedOBDal.when(OBDal::getInstance).thenReturn(mockDal);

      String result = LegacyUtils.findTabIdByWindowAndTable(WINDOW_ID, TABLE_ID);

      assertEquals("257", result);
      verify(mockQuery).setNamedParameter("windowId", WINDOW_ID);
      verify(mockQuery).setNamedParameter("tableId", TABLE_ID);
      verify(mockQuery).setMaxResult(1);
    }
  }

  @Test
  void resolveSingleIdColumnNameReturnsColumnNameWhenSingleIdProperty() {
    Entity entity = mock(Entity.class);
    Property idProp = mock(Property.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(entity.getIdProperties()).thenReturn(List.of(idProp));
    when(idProp.getColumnName()).thenReturn(COLUMN_ORDER_ID);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      String result = LegacyUtils.resolveSingleIdColumnName(ENTITY_ORDER);

      assertEquals(COLUMN_ORDER_ID, result);
    }
  }

  @Test
  void resolveSingleIdColumnNameReturnsNullWhenEntityNotFound() {
    ModelProvider modelProvider = mock(ModelProvider.class);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_UNKNOWN)).thenReturn(null);

      assertNull(LegacyUtils.resolveSingleIdColumnName(ENTITY_UNKNOWN));
    }
  }

  @Test
  void resolveSingleIdColumnNameReturnsNullWhenMultipleIdProperties() {
    Entity entity = mock(Entity.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(entity.getIdProperties()).thenReturn(List.of(mock(Property.class), mock(Property.class)));

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      assertNull(LegacyUtils.resolveSingleIdColumnName(ENTITY_ORDER));
    }
  }

  @Test
  void resolveSingleIdColumnNameReturnsNullWhenIdPropsIsNull() {
    Entity entity = mock(Entity.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(entity.getIdProperties()).thenReturn(null);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      assertNull(LegacyUtils.resolveSingleIdColumnName(ENTITY_ORDER));
    }
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsKeyWhenAllParamsResolve() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    Entity entity = mock(Entity.class);
    Property idProp = mock(Property.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn(USED_BY_LINK_WINDOW_ID);
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);
    when(entity.getIdProperties()).thenReturn(List.of(idProp));
    when(idProp.getColumnName()).thenReturn(COLUMN_ORDER_ID);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_ORDER)).thenReturn(entity);

      LegacyUtils.UsedByLinkSessionKey key =
          LegacyUtils.resolveUsedByLinkSessionKey(req, LegacyPaths.USED_BY_LINK);

      assertNotNull(key);
      assertEquals(USED_BY_LINK_WINDOW_ID, key.windowId());
      assertEquals(COLUMN_ORDER_ID, key.columnName());
      assertEquals(RECORD_ID_VALUE, key.recordId());
    }
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsNullForNonUsedByLinkPath() {
    HttpServletRequest req = mock(HttpServletRequest.class);

    assertNull(LegacyUtils.resolveUsedByLinkSessionKey(req, "/some/other/path.html"));
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsNullWhenWindowIdMissing() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn(null);
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    assertNull(LegacyUtils.resolveUsedByLinkSessionKey(req, LegacyPaths.USED_BY_LINK));
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsNullWhenEntityNameMissing() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn(USED_BY_LINK_WINDOW_ID);
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(null);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    assertNull(LegacyUtils.resolveUsedByLinkSessionKey(req, LegacyPaths.USED_BY_LINK));
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsNullWhenRecordIdMissing() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn(USED_BY_LINK_WINDOW_ID);
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_ORDER);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(null);

    assertNull(LegacyUtils.resolveUsedByLinkSessionKey(req, LegacyPaths.USED_BY_LINK));
  }

  @Test
  void resolveUsedByLinkSessionKeyReturnsNullWhenColumnNameUnresolved() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    ModelProvider modelProvider = mock(ModelProvider.class);

    when(req.getParameter(PARAM_WINDOW_ID)).thenReturn(USED_BY_LINK_WINDOW_ID);
    when(req.getParameter(PARAM_ENTITY_NAME)).thenReturn(ENTITY_UNKNOWN);
    when(req.getParameter(PARAM_RECORD_ID)).thenReturn(RECORD_ID_VALUE);

    try (MockedStatic<ModelProvider> staticModelProvider = mockStatic(ModelProvider.class)) {
      staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getEntity(ENTITY_UNKNOWN)).thenReturn(null);

      assertNull(LegacyUtils.resolveUsedByLinkSessionKey(req, LegacyPaths.USED_BY_LINK));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  void findTabIdByWindowAndTableReturnsNullWhenNoTabFound() {
    OBQuery<Tab> mockQuery = mock(OBQuery.class);
    when(mockQuery.list()).thenReturn(List.of());

    OBDal mockDal = mock(OBDal.class);
    when(mockDal.createQuery(eq(Tab.class), anyString())).thenReturn(mockQuery);

    try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class)) {
      mockedOBDal.when(OBDal::getInstance).thenReturn(mockDal);

      String result = LegacyUtils.findTabIdByWindowAndTable(WINDOW_ID, "999");

      assertNull(result);
    }
  }
}
