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

import static com.etendoerp.metadata.MetadataTestConstants.TAB_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.builders.RoleAccessResolver;
import com.etendoerp.metadata.builders.TabBuilder;

/**
 * Unit tests for the role access applied by {@link TabService} when building a tab.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class TabServiceRoleAccessTest {

  @Mock
  private Tab tab;
  @Mock
  private Window window;
  @Mock
  private Role role;
  @Mock
  private OBContext context;

  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<RoleAccessResolver> mockedResolver;
  /** Arguments of every TabBuilder constructed during a test: tab, tabAccess, readOnly, fieldAccesses. */
  private final List<List<?>> tabBuilderArguments = new ArrayList<>();

  /**
   * Wires the current role and the tab's window, and mocks the access resolver.
   */
  @BeforeEach
  void setUp() {
    when(tab.getId()).thenReturn(TAB_ID);
    when(tab.getWindow()).thenReturn(window);
    when(context.getRole()).thenReturn(role);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(context);
    mockedResolver = mockStatic(RoleAccessResolver.class);
  }

  /**
   * Releases the static mocks.
   */
  @AfterEach
  void tearDown() {
    mockedResolver.close();
    mockedOBContext.close();
  }

  /**
   * Tests that without window access the tab is served as before (editable, no tab access), so the
   * Pick and Execute grids that read this endpoint keep working.
   */
  @Test
  void withoutWindowAccessServesTheTabAsBefore() {
    mockedResolver.when(() -> RoleAccessResolver.findActiveWindowAccess(role, window)).thenReturn(null);

    buildTab();

    List<?> arguments = tabBuilderArguments.get(0);
    assertSame(tab, arguments.get(0));
    assertNull(arguments.get(1));
    assertEquals(false, arguments.get(2));
  }

  /**
   * Tests that with window access the tab gets its active tab access, its field accesses and the
   * read-only state resolved with Classic's rule.
   */
  @Test
  void withWindowAccessAppliesTheRoleAccess() {
    WindowAccess windowAccess = mock(WindowAccess.class);
    TabAccess tabAccess = mock(TabAccess.class);
    List<TabAccess> tabAccesses = List.of(tabAccess);
    List<FieldAccess> fieldAccesses = List.of(mock(FieldAccess.class));
    mockedResolver.when(() -> RoleAccessResolver.findActiveWindowAccess(role, window)).thenReturn(windowAccess);
    mockedResolver.when(() -> RoleAccessResolver.findTabAccesses(windowAccess)).thenReturn(tabAccesses);
    mockedResolver.when(() -> RoleAccessResolver.indexActiveTabAccessesByTabId(tabAccesses))
        .thenReturn(Map.of(TAB_ID, tabAccess));
    mockedResolver.when(() -> RoleAccessResolver.isTabReadOnly(windowAccess, tab)).thenReturn(true);
    mockedResolver.when(() -> RoleAccessResolver.findFieldAccesses(any())).thenReturn(fieldAccesses);

    buildTab();

    List<?> arguments = tabBuilderArguments.get(0);
    assertSame(tabAccess, arguments.get(1));
    assertEquals(true, arguments.get(2));
    assertSame(fieldAccesses, arguments.get(3));
  }

  private void buildTab() {
    try (MockedConstruction<TabBuilder> ignored = mockConstruction(TabBuilder.class,
        (mock, ctx) -> tabBuilderArguments.add(new ArrayList<>(ctx.arguments())))) {
      TabService.createTabBuilder(tab);
    }
  }
}
