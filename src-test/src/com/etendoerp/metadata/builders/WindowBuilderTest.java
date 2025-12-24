package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ROLE_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TAB_ID_HYPHEN;
import static com.etendoerp.metadata.MetadataTestConstants.WINDOW_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
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
      when(mockWindowAccess.getADTabAccessList()).thenReturn(new ArrayList<>());
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
         MockedConstruction<DataToJsonConverter> ignored1 = createDataToJsonConverterMock();
         MockedConstruction<TabBuilder> ignored2 = createTabBuilderMock(tabId)) {
      return windowBuilder.toJSON();
    }
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
    assertTrue(result.has("tabs"));
    JSONArray tabs = result.getJSONArray("tabs");
    assertNotNull(tabs);
    assertEquals(0, tabs.length());
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
    assertTrue(result.has("tabs"));
    JSONArray tabs = result.getJSONArray("tabs");
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
   * Tests that the toJSON method excludes tabs that are inactive or have no read access.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window but not to certain tabs.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithInactiveTabAccessExcludesTab() throws Exception {
    setupWindowAccess(true, true);
    setupTabAccess(false, true);
    
    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSON(windowBuilder);

    assertNotNull(result);
    assertTrue(result.has("tabs"));
    JSONArray tabs = result.getJSONArray("tabs");
    assertEquals(0, tabs.length());
  }

  /**
   * Tests that the toJSON method excludes tabs that have no read access.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window but not to certain tabs.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithNoReadAccessTabAccessExcludesTab() throws Exception {
    setupWindowAccess(true, true);
    setupTabAccess(true, false);
    
    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSON(windowBuilder);

    assertNotNull(result);
    assertTrue(result.has("tabs"));
    JSONArray tabs = result.getJSONArray("tabs");
    assertEquals(0, tabs.length());
  }

  /**
   * Tests that the toJSON method includes tabs when the user has active tab access and read permission.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window and tabs.
   *
   * @throws Exception if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithActiveTabAccessAndReadPermissionIncludesTab() throws Exception {
    setupWindowAccess(true, true);
    setupTabAccess(true, true);
    
    WindowBuilder windowBuilder = createWindowBuilder();
    JSONObject result = executeToJSONWithTabs(windowBuilder, TAB_ID_HYPHEN);

    assertNotNull(result);
    assertTrue(result.has("tabs"));
    JSONArray tabs = result.getJSONArray("tabs");
    assertEquals(1, tabs.length());

    JSONObject tabJson = tabs.getJSONObject(0);
    assertEquals(TAB_ID_HYPHEN, tabJson.getString("id"));
  }

  /**
   * Sets up the mock behavior for TabAccess and Tab entities.
   *
   * @param isActive   whether the tab access is active
   * @param allowRead  whether the tab access allows read
   */
  private void setupTabAccess(boolean isActive, boolean allowRead) {
    TabAccess mockTabAccess = mock(TabAccess.class);
    Tab mockTab = mock(Tab.class);

    List<TabAccess> tabAccessList = List.of(mockTabAccess);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(tabAccessList);

    when(mockTabAccess.isActive()).thenReturn(isActive);
    when(mockTabAccess.isAllowRead()).thenReturn(allowRead);
    when(mockTabAccess.getTab()).thenReturn(mockTab);
    when(mockTab.getId()).thenReturn(TAB_ID_HYPHEN);
    when(mockTab.getDisplayLogic()).thenReturn(null);
  }
}
