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
package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ROLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TAB_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.WindowAccessData;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Unit tests for {@link RoleAccessResolver}.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class RoleAccessResolverTest {

  private static final String TAB_ACCESS_ID = "tab-access-id";

  @Mock
  private OBDal obDal;
  @Mock
  private Role role;
  @Mock
  private Tab tab;
  @Mock
  private WindowAccess windowAccess;

  private MockedStatic<OBDal> mockedOBDal;

  /**
   * Wires the read-only OBDal instance and the common entity mocks.
   */
  @BeforeEach
  void setUp() {
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
    when(role.getId()).thenReturn(ROLE_ID);
    when(tab.getId()).thenReturn(TAB_ID);
    when(windowAccess.getRole()).thenReturn(role);
  }

  /**
   * Releases the static OBDal mock.
   */
  @AfterEach
  void tearDown() {
    mockedOBDal.close();
  }

  /**
   * Tests that a role without window access gets the implicit read-only fallback without querying
   * Classic's rule.
   */
  @Test
  void tabIsReadOnlyWithoutWindowAccess() {
    try (MockedStatic<WindowAccessData> mocked = mockStatic(WindowAccessData.class)) {
      assertTrue(RoleAccessResolver.isTabReadOnly(null, tab));
      mocked.verify(() -> WindowAccessData.hasReadOnlyAccess(any(), anyString(), anyString()), never());
    }
  }

  /**
   * Tests that the read-only state is Classic's answer for the role and the tab.
   */
  @Test
  void tabReadOnlyStateComesFromClassicRule() {
    assertTrue(resolveWithClassicAnswer(true));
    assertFalse(resolveWithClassicAnswer(false));
  }

  /**
   * Tests that an error evaluating Classic's rule fails closed (read-only).
   */
  @Test
  void tabIsReadOnlyWhenClassicRuleFails() {
    try (MockedStatic<WindowAccessData> mocked = mockStatic(WindowAccessData.class);
        MockedConstruction<DalConnectionProvider> ignored = mockConstruction(DalConnectionProvider.class)) {
      mocked.when(() -> WindowAccessData.hasReadOnlyAccess(any(), eq(ROLE_ID), eq(TAB_ID)))
          .thenThrow(new ServletException("query failed"));

      assertTrue(RoleAccessResolver.isTabReadOnly(windowAccess, tab));
    }
  }

  /**
   * Tests that only active tab accesses are indexed, keyed by their tab ID.
   */
  @Test
  void indexesOnlyActiveTabAccesses() {
    TabAccess active = mockTabAccess(true);
    TabAccess inactive = mockTabAccess(false);

    Map<String, TabAccess> result = RoleAccessResolver.indexActiveTabAccessesByTabId(List.of(inactive, active));

    assertEquals(1, result.size());
    assertSame(active, result.get(TAB_ID));
  }

  /**
   * Tests the lookup of a tab access in a batch-loaded map, including a missing tab access.
   */
  @Test
  void fieldAccessesOfReadsTheBatchLoadedMap() {
    TabAccess tabAccess = mockTabAccess(true);
    List<FieldAccess> fieldAccesses = List.of(mock(FieldAccess.class));
    Map<String, List<FieldAccess>> byTabAccessId = Map.of(TAB_ACCESS_ID, fieldAccesses);

    assertSame(fieldAccesses, RoleAccessResolver.fieldAccessesOf(tabAccess, byTabAccessId));
    assertTrue(RoleAccessResolver.fieldAccessesOf(null, byTabAccessId).isEmpty());
    assertTrue(RoleAccessResolver.fieldAccessesOf(tabAccess, Collections.emptyMap()).isEmpty());
  }

  /**
   * Tests that no query is run when there is no tab access to load field accesses for.
   */
  @Test
  void noFieldAccessQueryWithoutTabAccess() {
    assertTrue(RoleAccessResolver.findFieldAccesses(null).isEmpty());
    assertTrue(RoleAccessResolver.findFieldAccessesByTabAccessId(List.of()).isEmpty());
    verify(obDal, never()).createQuery(eq(FieldAccess.class), anyString(), anyMap());
  }

  /**
   * Tests that the field accesses of a tab access are loaded and grouped by tab access.
   */
  @Test
  @SuppressWarnings("unchecked")
  void findFieldAccessesLoadsTheRowsOfTheTabAccess() {
    TabAccess tabAccess = mockTabAccess(true);
    FieldAccess fieldAccess = mock(FieldAccess.class);
    when(fieldAccess.getTabAccess()).thenReturn(tabAccess);
    OBQuery<FieldAccess> query = mock(OBQuery.class);
    when(obDal.createQuery(eq(FieldAccess.class), anyString(), anyMap())).thenReturn(query);
    when(query.setFilterOnActive(anyBoolean())).thenReturn(query);
    when(query.list()).thenReturn(List.of(fieldAccess));

    assertEquals(List.of(fieldAccess), RoleAccessResolver.findFieldAccesses(tabAccess));
  }

  /**
   * Tests that the tab accesses of a window access are loaded, inactive ones included.
   */
  @Test
  @SuppressWarnings("unchecked")
  void findTabAccessesLoadsAllRows() {
    TabAccess tabAccess = mockTabAccess(false);
    OBQuery<TabAccess> query = mock(OBQuery.class);
    when(obDal.createQuery(eq(TabAccess.class), anyString(), anyMap())).thenReturn(query);
    when(query.setFilterOnActive(false)).thenReturn(query);
    when(query.list()).thenReturn(List.of(tabAccess));

    assertEquals(List.of(tabAccess), RoleAccessResolver.findTabAccesses(windowAccess));
  }

  /**
   * Tests that the active window access of the role is returned.
   */
  @Test
  @SuppressWarnings("unchecked")
  void findActiveWindowAccessReturnsTheRoleAccess() {
    OBCriteria<WindowAccess> criteria = mock(OBCriteria.class);
    when(obDal.createCriteria(WindowAccess.class)).thenReturn(criteria);
    when(criteria.add(any())).thenReturn(criteria);
    when(criteria.setMaxResults(1)).thenReturn(criteria);
    when(criteria.uniqueResult()).thenReturn(windowAccess);

    assertSame(windowAccess, RoleAccessResolver.findActiveWindowAccess(role, mock(Window.class)));
  }

  private boolean resolveWithClassicAnswer(boolean classicAnswer) {
    try (MockedStatic<WindowAccessData> mocked = mockStatic(WindowAccessData.class);
        MockedConstruction<DalConnectionProvider> ignored = mockConstruction(DalConnectionProvider.class)) {
      mocked.when(() -> WindowAccessData.hasReadOnlyAccess(any(), eq(ROLE_ID), eq(TAB_ID))).thenReturn(classicAnswer);
      return RoleAccessResolver.isTabReadOnly(windowAccess, tab);
    }
  }

  private TabAccess mockTabAccess(boolean active) {
    TabAccess tabAccess = mock(TabAccess.class);
    when(tabAccess.isActive()).thenReturn(active);
    when(tabAccess.getTab()).thenReturn(tab);
    when(tabAccess.getId()).thenReturn(TAB_ACCESS_ID);
    return tabAccess;
  }
}
