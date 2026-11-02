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
package com.etendoerp.metadata.data;

import static com.etendoerp.metadata.MetadataTestConstants.WINDOW_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.ProcessAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.utils.Constants;

/**
 * Unit tests for {@link ProcessAccessChecker}, which mirrors Classic's rule to disable buttons of
 * processes the role cannot run.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ProcessAccessCheckerTest {

  @Mock
  private Field field;
  @Mock
  private Column column;
  @Mock
  private Window window;
  @Mock
  private Process process;
  @Mock
  private OBContext context;
  @Mock
  private OBDal obDal;
  @Mock
  private OBCriteria<ProcessAccess> criteria;

  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<Preferences> mockedPreferences;

  /**
   * Wires a button field of a window whose classic process has no access row and no explicit
   * permission requirement, with the {@code SecuredProcess} preference unset.
   *
   * @throws PropertyException never: the preference lookup is mocked
   */
  @BeforeEach
  void setUp() throws PropertyException {
    Tab tab = mock(Tab.class);
    when(field.getColumn()).thenReturn(column);
    when(field.getTab()).thenReturn(tab);
    when(tab.getWindow()).thenReturn(window);
    when(window.getId()).thenReturn(WINDOW_ID);
    when(column.getProcess()).thenReturn(process);
    when(process.isRequiresExplicitAccessPermission()).thenReturn(false);

    mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(context);
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
    when(obDal.createCriteria(ProcessAccess.class)).thenReturn(criteria);
    when(criteria.add(any())).thenReturn(criteria);
    when(criteria.setMaxResults(1)).thenReturn(criteria);
    when(criteria.uniqueResult()).thenReturn(null);

    mockedPreferences = mockStatic(Preferences.class);
    mockSecuredProcessPreference(null);
  }

  /**
   * Releases the static mocks.
   */
  @AfterEach
  void tearDown() {
    mockedPreferences.close();
    mockedOBDal.close();
    mockedOBContext.close();
  }

  /**
   * Tests that a field without column or without process is always accessible, legacy buttons
   * included.
   */
  @Test
  void fieldWithoutProcessIsAccessible() {
    when(column.getProcess()).thenReturn(null);
    assertTrue(ProcessAccessChecker.isProcessAccessible(field));

    when(field.getColumn()).thenReturn(null);
    assertTrue(ProcessAccessChecker.isProcessAccessible(field));
  }

  /**
   * Tests that a process definition delegates on the existing hasAccess check.
   */
  @Test
  void processDefinitionDelegatesOnHasAccess() {
    when(column.getOBUIAPPProcess()).thenReturn(mock(org.openbravo.client.application.Process.class));
    try (MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class)) {
      mockedTabProcessor.when(() -> TabProcessor.hasAccessToProcess(field, WINDOW_ID)).thenReturn(false);
      assertFalse(ProcessAccessChecker.isProcessAccessible(field));

      mockedTabProcessor.when(() -> TabProcessor.hasAccessToProcess(field, WINDOW_ID)).thenReturn(true);
      assertTrue(ProcessAccessChecker.isProcessAccessible(field));
    }
  }

  /**
   * Tests that a classic process inherits the window access when it is not secured.
   */
  @Test
  void unsecuredClassicProcessInheritsWindowAccess() {
    assertTrue(ProcessAccessChecker.isProcessAccessible(field));
  }

  /**
   * Tests that a classic process requiring an explicit permission needs an active access row.
   */
  @Test
  void explicitClassicProcessNeedsAccessRow() {
    when(process.isRequiresExplicitAccessPermission()).thenReturn(true);
    assertFalse(ProcessAccessChecker.isProcessAccessible(field));

    when(criteria.uniqueResult()).thenReturn(mock(ProcessAccess.class));
    assertTrue(ProcessAccessChecker.isProcessAccessible(field));
  }

  /**
   * Tests that the {@code SecuredProcess} preference makes every classic process need an access
   * row.
   *
   * @throws PropertyException never: the preference lookup is mocked
   */
  @Test
  void securedProcessPreferenceRequiresAccessRow() throws PropertyException {
    mockSecuredProcessPreference(Preferences.YES);

    assertFalse(ProcessAccessChecker.isProcessAccessible(field));
  }

  /**
   * Tests that an unresolvable {@code SecuredProcess} preference counts as not set.
   *
   * @throws PropertyException never: the preference lookup is mocked
   */
  @Test
  void unresolvedSecuredProcessPreferenceCountsAsNotSet() throws PropertyException {
    mockedPreferences.when(() -> Preferences.getPreferenceValue(eq(Constants.SECURED_PROCESS_PREFERENCE),
        anyBoolean(), any(), any(), any(), any(), any(Window.class))).thenThrow(new PropertyException());

    assertTrue(ProcessAccessChecker.isProcessAccessible(field));
  }

  private void mockSecuredProcessPreference(String value) throws PropertyException {
    mockedPreferences.when(() -> Preferences.getPreferenceValue(eq(Constants.SECURED_PROCESS_PREFERENCE),
        anyBoolean(), any(), any(), any(), any(), any(Window.class))).thenReturn(value);
  }
}
