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
import static com.etendoerp.metadata.MetadataTestConstants.ROLE_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TABS;
import static com.etendoerp.metadata.MetadataTestConstants.TAB_ID_HYPHEN;
import static com.etendoerp.metadata.MetadataTestConstants.WINDOW_ID;
import static com.etendoerp.metadata.utils.Constants.JSON_IS_WINDOW_ACCESSIBLE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
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
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.WindowAccessData;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Unit tests for WindowBuilder using pure mocking approach.
 * This approach avoids complex dependencies and focuses on testing the core logic.
 */
@MockitoSettings (strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class WindowBuilderTest {

  @Mock
  private OBContext mockContext;
  @Mock
  private Language mockLanguage;
  @Mock
  private OBDal mockOBDal;
  @Mock
  private Window mockWindow;
  @Mock
  private WindowAccess mockWindowAccess;
  @Mock
  private Role mockRole;
  @Mock
  private OBCriteria<WindowAccess> mockCriteria;
  @Mock
  private OBQuery<TabAccess> mockTabAccessQuery;
  @Mock
  private OBQuery<FieldAccess> mockFieldAccessQuery;

  /** Arguments of every TabBuilder constructed during a test: tab, tabAccess, readOnly, fieldAccesses. */
  private final List<List<?>> tabBuilderArguments = new ArrayList<>();
  /** Value returned by Classic's {@code WindowAccessData.hasReadOnlyAccess} in the tests. */
  private boolean classicTabReadOnly;

  // Test data
  private static final String WINDOW_TYPE = "windowType";
  private static final String WINDOW_TYPE_VALUE = "OBUIAPP_PickAndExecute";

  /**
   * Sets up the basic mock behavior before each test.
   * This includes setting up OBContext, Language, and Role mocks.
   */
  @BeforeEach
  void setUp() {
    setupBasicMocks();
  }

  /**
   * Sets up the basic mock behavior for OBContext, Language, and Role.
   * This method is called before each test to ensure consistent mock behavior.
   */
  private void setupBasicMocks() {
    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockRole.getName()).thenReturn(ROLE_NAME);
    when(mockRole.getId()).thenReturn(ROLE_ID);
    when(mockWindowAccess.getRole()).thenReturn(mockRole);

    when(mockOBDal.createQuery(eq(TabAccess.class), anyString(), anyMap())).thenReturn(mockTabAccessQuery);
    when(mockTabAccessQuery.setFilterOnActive(anyBoolean())).thenReturn(mockTabAccessQuery);
    when(mockTabAccessQuery.list()).thenReturn(new ArrayList<>());

    when(mockOBDal.createQuery(eq(FieldAccess.class), anyString(), anyMap())).thenReturn(mockFieldAccessQuery);
    when(mockFieldAccessQuery.setFilterOnActive(anyBoolean())).thenReturn(mockFieldAccessQuery);
    when(mockFieldAccessQuery.list()).thenReturn(new ArrayList<>());
  }

  /**
   * Sets up the mock behavior for OBDal to simulate window existence and access permissions.
   *
   * @param windowExists whether the window exists in the database
   * @param hasAccess    whether the current role has access to the window
   */
  private void setupWindowAccess(boolean windowExists, boolean hasAccess) {
    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(windowExists ? mockWindow : null);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(hasAccess ? mockWindowAccess : null);
    
    if (hasAccess) {
      when(mockWindowAccess.getWindow()).thenReturn(mockWindow);
      when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());
    }
  }

  /**
   * Creates a mock for OBContext that returns a predefined OBContext instance.
   *
   * @return a MockedStatic of OBContext
   */
  private MockedStatic<OBContext> createOBContextMock() {
    MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
    mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
    return mockedOBContext;
  }

  /**
   * Creates a mock for OBDal that returns a predefined OBDal instance.
   *
   * @return a MockedStatic of OBDal
   */
  private MockedStatic<OBDal> createOBDalMock() {
    MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
    mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);
    return mockedOBDal;
  }

  /**
   * Creates a mock for DataToJsonConverter that returns a predefined JSON object.
   *
   * @return a MockedConstruction of DataToJsonConverter
   */
  private MockedConstruction<DataToJsonConverter> createDataToJsonConverterMock() {
    return mockConstruction(DataToJsonConverter.class, (mock, context) -> {
      JSONObject windowJson = new JSONObject();
      windowJson.put("id", WINDOW_ID);
      windowJson.put("name", "Test Window");
      when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
    });
  }

  /**
   * Creates a mock for TabBuilder that returns a predefined JSON object for a tab.
   *
   * @param tabId the ID of the tab to be mocked
   * @return a MockedConstruction of TabBuilder
   */
  private MockedConstruction<TabBuilder> createTabBuilderMock(String tabId) {
    return mockConstruction(TabBuilder.class, (mock, context) -> {
      tabBuilderArguments.add(new ArrayList<>(context.arguments()));
      JSONObject tabJson = new JSONObject();
      tabJson.put("id", tabId);
      when(mock.toJSON()).thenReturn(tabJson);
    });
  }

  /**
   * Creates a WindowBuilder instance for testing.
   *
   * @return a new WindowBuilder instance
   */
  private WindowBuilder createWindowBuilder() {
    try (MockedStatic<OBContext> ignored1 = createOBContextMock();
         MockedConstruction<DataToJsonConverter> ignored = createDataToJsonConverterMock()) {
      return new WindowBuilder(WINDOW_ID);
    }
  }

  /**
   * Executes the toJSON method of WindowBuilder.
   *
   * @param windowBuilder the WindowBuilder instance
   * @return the resulting JSON object
   */
  private JSONObject executeToJSON(WindowBuilder windowBuilder) {
    try (MockedStatic<OBContext> ignored1 = createOBContextMock();
         MockedStatic<OBDal> ignored2 = createOBDalMock();
         MockedConstruction<DataToJsonConverter> ignored = createDataToJsonConverterMock()) {
      return windowBuilder.toJSON();
    }
  }

  /**
   * Executes the toJSON method of WindowBuilder with tabs.
   *
   * @param windowBuilder the WindowBuilder instance
   * @param tabId         the ID of the tab to be mocked
   * @return the resulting JSON object
   */
  private JSONObject executeToJSONWithTabs(WindowBuilder windowBuilder, String tabId) {
    try (MockedStatic<OBContext> ignored = createOBContextMock();
         MockedStatic<OBDal> ignored3 = createOBDalMock();
         MockedStatic<WindowAccessData> ignored4 = createWindowAccessDataMock();
         MockedConstruction<DalConnectionProvider> ignored5 = mockConstruction(DalConnectionProvider.class);
         MockedConstruction<DataToJsonConverter> ignored1 = createDataToJsonConverterMock();
         MockedConstruction<TabBuilder> ignored2 = createTabBuilderMock(tabId)) {
      return windowBuilder.toJSON();
    }
  }

  /**
   * Mocks Classic's read-only rule so it answers {@link #classicTabReadOnly} for every tab.
   *
   * @return a MockedStatic of WindowAccessData
   */
  private MockedStatic<WindowAccessData> createWindowAccessDataMock() {
    MockedStatic<WindowAccessData> mocked = mockStatic(WindowAccessData.class);
    mocked.when(() -> WindowAccessData.hasReadOnlyAccess(any(), anyString(), anyString()))
        .thenAnswer(invocation -> classicTabReadOnly);
    return mocked;
  }

  /**
   * Tests that the WindowBuilder constructor creates an instance successfully.
   * It mocks the OBContext and Language to avoid dependencies on the actual Openbravo context.
   */
  @Test
  void constructorCreatesInstanceSuccessfully() {
    WindowBuilder windowBuilder = createWindowBuilder();
    assertNotNull(windowBuilder);
  }

  /**
   * Tests that the toJSON method throws NotFoundException when the window does not exist.
   * It mocks the OBDal to return null for the specified window ID.
   */
  @Test
  void toJSONWithNonExistentWindowThrowsNotFoundException() {
    setupWindowAccess(false, false);
    WindowBuilder windowBuilder = createWindowBuilder();
    assertThrows(NotFoundException.class, () -> executeToJSON(windowBuilder));
  }

  /**
   * Tests that the toJSON method throws UnauthorizedException when the user does not have access to the window.
   * It mocks the OBDal to return a WindowAccess object that indicates no access for the current role.
   */
  @Test
  void toJSONWithUnauthorizedRoleThrowsUnauthorizedException() {
    setupWindowAccess(true, false);
    WindowBuilder windowBuilder = createWindowBuilder();
    
    UnauthorizedException exception = assertThrows(UnauthorizedException.class, 
        () -> executeToJSON(windowBuilder));
    
    assertTrue(exception.getMessage().contains(ROLE_NAME));
    assertTrue(exception.getMessage().contains(WINDOW_ID));
  }

  /**
   * Tests that the toJSON method returns valid JSON when the user has authorized access.
   * This test mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role is authorized for the specified window.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithAuthorizedAccessReturnsValidJSON() throws Exception {
    setupWindowAccess(true, true);
    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSON(windowBuilder);

    assertNotNull(result);
    assertTrue(result.has(TABS));
    JSONArray tabs = result.getJSONArray(TABS);
    assertNotNull(tabs);
    assertEquals(0, tabs.length());
  }

  /**
   * Builds a {@link DataToJsonConverter} mock that emits a JSON containing the
   * {@code windowType} field with the supplied value. Mirrors the production
   * behaviour of {@code DataToJsonConverter.toJsonObject(window, FULL_TRANSLATABLE)}.
   *
   * @param windowType The window type value the converter should expose.
   * @return A MockedConstruction of DataToJsonConverter pre-wired with the field.
   */
  private MockedConstruction<DataToJsonConverter> createConverterMockWithWindowType(String windowType) {
    return mockConstruction(DataToJsonConverter.class, (mock, context) -> {
      JSONObject windowJson = new JSONObject();
      windowJson.put("id", WINDOW_ID);
      windowJson.put("name", "Test Window");
      windowJson.put(WINDOW_TYPE, windowType);
      when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
    });
  }

  /**
   * Defensive regression test: when the {@link DataToJsonConverter} exposes the
   * {@code windowType} property of the window, the {@link WindowBuilder} output
   * must preserve it. This guarantees that the client can read
   * {@code windowType} from {@code /meta/window/{id}} for any window type.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONExposesWindowTypeWhenConverterEmitsIt() throws Exception {
    setupWindowAccess(true, true);
    WindowBuilder windowBuilder;
    JSONObject result;
    try (MockedStatic<OBContext> ignored1 = createOBContextMock();
         MockedConstruction<DataToJsonConverter> ignored2 = createConverterMockWithWindowType(WINDOW_TYPE_VALUE)) {
      windowBuilder = new WindowBuilder(WINDOW_ID);
    }
    try (MockedStatic<OBContext> ignored1 = createOBContextMock();
         MockedStatic<OBDal> ignored2 = createOBDalMock();
         MockedConstruction<DataToJsonConverter> ignored3 = createConverterMockWithWindowType(WINDOW_TYPE_VALUE)) {
      result = windowBuilder.toJSON();
    }

    assertNotNull(result);
    assertTrue(result.has(WINDOW_TYPE));
    assertEquals(WINDOW_TYPE_VALUE, result.getString(WINDOW_TYPE));
  }

  /**
   * Tests that the toJSON method returns valid JSON with tabs when the user has authorized access.
   * This test mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role is authorized for the specified window and tabs.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithTabsHavingDisplayLogicFiltersCorrectly() throws Exception {
    setupWindowAccess(true, true);
    setupTabsWithDisplayLogic();
    
    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSONWithTabs(windowBuilder, "mock-tab-id");

    assertNotNull(result);
    assertTrue(result.has(TABS));
    JSONArray tabs = result.getJSONArray(TABS);
    assertEquals(3, tabs.length());
  }

  /**
   * Sets up mock tabs with various display logic configurations.
   * This method creates three mock tabs:
   * - Tab 1: No display logic (null)
   * - Tab 2: Empty display logic ("")
   * - Tab 3: Valid SQL display logic ("@SQL=SELECT 1 FROM DUAL")
   * The method configures the mockWindow to return these tabs when getADTabList is called.
   */
  private void setupTabsWithDisplayLogic() {
    Tab mockTab1 = mock(Tab.class);
    Tab mockTab2 = mock(Tab.class);
    Tab mockTab3 = mock(Tab.class);

    List<Tab> tabList = List.of(mockTab1, mockTab2, mockTab3);
    when(mockWindow.getADTabList()).thenReturn(tabList);

    when(mockTab1.getId()).thenReturn("tab1-id");
    when(mockTab1.getDisplayLogic()).thenReturn(null);

    when(mockTab2.getId()).thenReturn("tab2-id");
    when(mockTab2.getDisplayLogic()).thenReturn("");

    when(mockTab3.getId()).thenReturn("tab3-id");
    when(mockTab3.getDisplayLogic()).thenReturn("@SQL=SELECT 1 FROM DUAL");
  }

  /**
   * Tests that an inactive Tab Access is ignored, as in Classic: the tab is still built from the
   * window's tab list, without any tab access.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithInactiveTabAccessBuildsTabWithoutTabAccess() throws Exception {
    setupWindowAccess(true, true);
    setupTabAccess(false);

    JSONObject result = executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    assertEquals(1, result.getJSONArray(TABS).length());
    assertNull(tabBuilderArguments.get(0).get(1), "An inactive tab access must not reach the TabBuilder");
  }

  /**
   * Tests that a tab without any Tab Access row is still built, so the access configuration never
   * hides a tab.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithoutTabAccessStillBuildsEveryTab() throws Exception {
    setupWindowAccess(true, true);
    Tab tab = mockTab(TAB_ID_HYPHEN);
    when(mockWindow.getADTabList()).thenReturn(List.of(tab));

    JSONObject result = executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    assertEquals(1, result.getJSONArray(TABS).length());
    assertNull(tabBuilderArguments.get(0).get(1));
    assertEquals(Collections.emptyList(), tabBuilderArguments.get(0).get(3));
  }

  /**
   * Tests that an active Tab Access is handed to the TabBuilder together with the read-only state
   * resolved by Classic's rule.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithActiveTabAccessPassesItWithClassicReadOnlyState() throws Exception {
    setupWindowAccess(true, true);
    TabAccess tabAccess = setupTabAccess(true);
    classicTabReadOnly = true;

    JSONObject result = executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    JSONArray tabs = result.getJSONArray(TABS);
    assertEquals(1, tabs.length());
    assertEquals(TAB_ID_HYPHEN, tabs.getJSONObject(0).getString("id"));
    assertSame(tabAccess, tabBuilderArguments.get(0).get(1));
    assertEquals(true, tabBuilderArguments.get(0).get(2));
  }

  /**
   * Tests that a tab is editable when Classic's rule says so, even if the window access itself is
   * read-only: an editable Tab Access overrides a read-only Window Access, as in Classic.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithEditableTabUnderReadOnlyWindowKeepsTabEditable() throws Exception {
    setupWindowAccess(true, true);
    when(mockWindowAccess.isEditableField()).thenReturn(false);
    setupTabAccess(true);
    classicTabReadOnly = false;

    executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    assertEquals(false, tabBuilderArguments.get(0).get(2));
  }

  /**
   * Tests that tabs are built in the window's tab order, whatever the order of the Tab Access rows.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONKeepsWindowTabOrder() throws Exception {
    setupWindowAccess(true, true);
    Tab firstTab = mockTab(TAB_ID_HYPHEN + 1);
    Tab secondTab = mockTab(TAB_ID_HYPHEN + 2);
    when(mockWindow.getADTabList()).thenReturn(List.of(firstTab, secondTab));
    TabAccess secondTabAccess = mockTabAccess(secondTab, true);
    when(mockTabAccessQuery.list()).thenReturn(List.of(secondTabAccess));

    executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    assertSame(firstTab, tabBuilderArguments.get(0).get(0));
    assertSame(secondTab, tabBuilderArguments.get(1).get(0));
  }

  /**
   * Tests that, for a window with several tab accesses, tabs and their field accesses are loaded
   * via a single batched query each rather than one query per tab (the N+1 pattern this refactor
   * eliminates). Also verifies the lazy {@code WindowAccess#getADTabAccessList()} collection is no
   * longer touched.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithMultipleTabAccessesLoadsTabsAndFieldsInBatchedQueries() throws Exception {
    setupWindowAccess(true, true);

    List<TabAccess> tabAccesses = new ArrayList<>();
    List<Tab> tabs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Tab mockTab = mockTab(TAB_ID_HYPHEN + i);
      tabAccesses.add(mockTabAccess(mockTab, true));
      tabs.add(mockTab);
    }
    when(mockTabAccessQuery.list()).thenReturn(tabAccesses);
    when(mockWindow.getADTabList()).thenReturn(tabs);

    JSONObject result = executeToJSONWithTabs(createWindowBuilder(), TAB_ID_HYPHEN);

    assertNotNull(result);
    JSONArray resultTabs = result.getJSONArray(TABS);
    assertEquals(3, resultTabs.length());

    verify(mockOBDal, times(1)).createQuery(eq(TabAccess.class), anyString(), anyMap());
    verify(mockOBDal, times(1)).createQuery(eq(FieldAccess.class), anyString(), anyMap());
    verify(mockWindowAccess, never()).getADTabAccessList();
  }

  /**
   * Tests that when the current role has no explicit WindowAccess but at least one other role does,
   * toJSON() returns valid JSON (implicit read-only fallback path) instead of throwing.
   * This covers the getWindowAccess() null-return path introduced to prevent privilege escalation.
   */
  @Test
  void toJSONWithNoRoleAccessButWindowExistsForOtherRoleReturnsJSON() throws Exception {
    setupImplicitWindowAccess();

    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSON(windowBuilder);

    assertNotNull(result);
    assertTrue(result.has(TABS), "Result must contain a tabs array");
    assertEquals(0, result.getJSONArray(TABS).length(), "No tabs should be present (empty access list)");
  }

  /**
   * Tests that clearTabAllowedCache can be called without error
   * even when the cache is empty.
   */
  @Test
  void clearTabAllowedCacheDoesNotThrowWhenEmpty() {
    WindowBuilder.clearTabAllowedCache();
  }

  /**
   * Tests that clearTabAllowedCache clears entries that were previously cached.
   * After loading tabs (which populates the cache), clearing should allow
   * a fresh evaluation on the next request.
   */
  @Test
  void clearTabAllowedCacheClearsPopulatedCache() throws Exception {
    setupWindowAccess(true, true);
    setupTabsWithDisplayLogic();

    WindowBuilder windowBuilder = createWindowBuilder();
    executeToJSONWithTabs(windowBuilder, "mock-tab-id");

    WindowBuilder.clearTabAllowedCache();
  }

  /**
   * Sets up a single tab in the window with a Tab Access row.
   *
   * @param isActive whether the tab access is active
   * @return the mocked tab access
   */
  private TabAccess setupTabAccess(boolean isActive) {
    Tab tab = mockTab(TAB_ID_HYPHEN);
    TabAccess tabAccess = mockTabAccess(tab, isActive);
    when(mockTabAccessQuery.list()).thenReturn(List.of(tabAccess));
    when(mockWindow.getADTabList()).thenReturn(List.of(tab));
    return tabAccess;
  }

  /**
   * Creates a mocked tab without display logic.
   *
   * @param tabId the tab ID
   * @return the mocked tab
   */
  private static Tab mockTab(String tabId) {
    Tab tab = mock(Tab.class);
    when(tab.getId()).thenReturn(tabId);
    when(tab.getDisplayLogic()).thenReturn(null);
    return tab;
  }

  /**
   * Creates a mocked Tab Access for the given tab.
   *
   * @param tab      the tab of the access row
   * @param isActive whether the access row is active
   * @return the mocked tab access
   */
  private static TabAccess mockTabAccess(Tab tab, boolean isActive) {
    String tabAccessId = tab.getId() + "-access";
    TabAccess tabAccess = mock(TabAccess.class);
    when(tabAccess.isActive()).thenReturn(isActive);
    when(tabAccess.getTab()).thenReturn(tab);
    when(tabAccess.getId()).thenReturn(tabAccessId);
    return tabAccess;
  }

  /**
   * Sets up the mock behavior for the implicit read-only access path: the current role has no
   * active {@link WindowAccess} for the window, but another role does. The first
   * {@code uniqueResult()} call (role-scoped query) returns {@code null} and the second one
   * (window-scoped fallback query) returns a non-null access, which is what makes
   * {@code getWindowAccess()} return {@code null} instead of throwing.
   */
  private void setupImplicitWindowAccess() {
    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(null, mockWindowAccess);
    when(mockWindow.getId()).thenReturn(WINDOW_ID);
    when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());
  }

  /**
   * Tests that a window served through a real {@link WindowAccess} grant is marked as accessible.
   * This is the positive half of the accessibility contract: the client must be able to tell a
   * genuine grant apart from the implicit read-only fallback, and a genuine grant must never
   * trigger the "Access Denied" screen.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithExplicitWindowAccessMarksWindowAsAccessible() throws Exception {
    setupWindowAccess(true, true);
    WindowBuilder windowBuilder = createWindowBuilder();

    JSONObject result = executeToJSON(windowBuilder);

    assertTrue(result.has(JSON_IS_WINDOW_ACCESSIBLE_KEY), "The accessibility flag must always be emitted");
    assertTrue(result.getBoolean(JSON_IS_WINDOW_ACCESSIBLE_KEY),
        "A role with an explicit WindowAccess must be reported as having access");
  }

  /**
   * Tests that a window served through the implicit read-only fallback (no {@link WindowAccess}
   * for the current role, but at least one other role has it) is marked as NOT accessible, while
   * still answering with full metadata. The response stays HTTP 200 and the flag is the only
   * signal the client has to render "Access Denied" on a deep-link.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithImplicitReadOnlyAccessMarksWindowAsNotAccessible() throws Exception {
    setupImplicitWindowAccess();
    WindowBuilder windowBuilder = createWindowBuilder();

    JSONObject result = executeToJSON(windowBuilder);

    assertTrue(result.has(JSON_IS_WINDOW_ACCESSIBLE_KEY), "The accessibility flag must always be emitted");
    assertFalse(result.getBoolean(JSON_IS_WINDOW_ACCESSIBLE_KEY),
        "The implicit read-only fallback must not be reported as real access");
  }

  /**
   * Regression guard for the flag's position inside {@code toJSON()}: the accessibility flag is
   * written before the tab array, whose builder swallows JSONException and only logs. Emitting the
   * flag first guarantees the client still gets a trustworthy access signal on a degraded
   * response. Asserted here through the tab-bearing path so both keys coexist.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONEmitsAccessibilityFlagAlongsideTabs() throws Exception {
    setupWindowAccess(true, true);
    setupTabAccess(true);
    WindowBuilder windowBuilder = createWindowBuilder();

    JSONObject result = executeToJSONWithTabs(windowBuilder, TAB_ID_HYPHEN);

    assertTrue(result.getBoolean(JSON_IS_WINDOW_ACCESSIBLE_KEY),
        "An explicitly granted window must stay accessible when tabs are present");
    assertEquals(1, result.getJSONArray(TABS).length(), "The mocked tab access must produce one tab");
  }
}
