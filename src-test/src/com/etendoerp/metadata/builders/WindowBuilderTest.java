package com.etendoerp.metadata.builders;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
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
@ExtendWith(MockitoExtension.class)
class WindowBuilderTest {

  private static final String WINDOW_ID = "test-window-id";
  private static final String ROLE_NAME = "Test Role";

  /**
   * Tests that the WindowBuilder constructor creates an instance successfully.
   * It mocks the OBContext and Language to avoid dependencies on the actual Openbravo context.
   */
  @Test
  void constructorCreatesInstanceSuccessfully() {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);

      assertNotNull(windowBuilder);
    }
  }

  /**
   * Tests that the toJSON method throws NotFoundException when the window does not exist.
   * It mocks the OBDal to return null for the specified window ID.
   */
  @Test
  void toJSONWithNonExistentWindowThrowsNotFoundException() {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);

      assertThrows(NotFoundException.class, windowBuilder::toJSON);
    }
  }

  /**
   * Tests that the toJSON method throws UnauthorizedException when the user does not have access to the window.
   * It mocks the OBDal to return a WindowAccess object that indicates no access for the current role.
   */
  @Test
  void toJSONWithUnauthorizedRoleThrowsUnauthorizedException() {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);
    when(mockRole.getName()).thenReturn(ROLE_NAME);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);

      UnauthorizedException exception = assertThrows(UnauthorizedException.class,
          windowBuilder::toJSON);

      assertTrue(exception.getMessage().contains(ROLE_NAME));
      assertTrue(exception.getMessage().contains(WINDOW_ID));
    }
  }

  /**
   * Tests that the toJSON method returns valid JSON when the user has authorized access.
   * This test mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role is authorized for the specified window.
   *
   * @throws Exception
   *     if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithAuthorizedAccessReturnsValidJSON() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    WindowAccess mockWindowAccess = mock(WindowAccess.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockWindowAccess);

    when(mockWindowAccess.getWindow()).thenReturn(mockWindow);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(new ArrayList<>());
    when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               windowJson.put("id", WINDOW_ID);
               windowJson.put("name", "Test Window");
               when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);
      JSONObject result = windowBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("tabs"));
      JSONArray tabs = result.getJSONArray("tabs");
      assertNotNull(tabs);
      assertEquals(0, tabs.length());
    }
  }

  /**
   * Tests that the toJSON method returns valid JSON with tabs when the user has authorized access.
   * This test mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role is authorized for the specified window and tabs.
   *
   * @throws Exception
   *     if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithTabsHavingDisplayLogicFiltersCorrectly() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    WindowAccess mockWindowAccess = mock(WindowAccess.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    Tab mockTab1 = mock(Tab.class);
    Tab mockTab2 = mock(Tab.class);
    Tab mockTab3 = mock(Tab.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockWindowAccess);

    when(mockWindowAccess.getWindow()).thenReturn(mockWindow);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(new ArrayList<>());

    List<Tab> tabList = new ArrayList<>();
    tabList.add(mockTab1);
    tabList.add(mockTab2);
    tabList.add(mockTab3);
    when(mockWindow.getADTabList()).thenReturn(tabList);

    when(mockTab1.getId()).thenReturn("tab1-id");
    when(mockTab1.getDisplayLogic()).thenReturn(null);

    when(mockTab2.getId()).thenReturn("tab2-id");
    when(mockTab2.getDisplayLogic()).thenReturn("");

    when(mockTab3.getId()).thenReturn("tab3-id");
    when(mockTab3.getDisplayLogic()).thenReturn("@SQL=SELECT 1 FROM DUAL");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               windowJson.put("id", WINDOW_ID);
               when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
             });
         MockedConstruction<TabBuilder> ignored = mockConstruction(TabBuilder.class,
             (mock, context) -> {
               JSONObject tabJson = new JSONObject();
               tabJson.put("id", "mock-tab-id");
               when(mock.toJSON()).thenReturn(tabJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);
      JSONObject result = windowBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("tabs"));
      JSONArray tabs = result.getJSONArray("tabs");

      assertEquals(2, tabs.length());
    }
  }

  /**
   * Tests that the toJSON method excludes tabs that are inactive or have no read access.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window but not to certain tabs.
   *
   * @throws Exception
   *     if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithInactiveTabAccessExcludesTab() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    WindowAccess mockWindowAccess = mock(WindowAccess.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    TabAccess mockTabAccess = mock(TabAccess.class);
    Tab mockTab = mock(Tab.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockWindowAccess);

    when(mockWindowAccess.getWindow()).thenReturn(mockWindow);

    List<TabAccess> tabAccessList = new ArrayList<>();
    tabAccessList.add(mockTabAccess);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(tabAccessList);
    when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());

    when(mockTabAccess.isActive()).thenReturn(false);
    when(mockTabAccess.getTab()).thenReturn(mockTab);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);
      JSONObject result = windowBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("tabs"));
      JSONArray tabs = result.getJSONArray("tabs");
      assertEquals(0, tabs.length());
    }
  }

  /**
   * Tests that the toJSON method excludes tabs that have no read access.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window but not to certain tabs.
   *
   * @throws Exception
   *     if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithNoReadAccessTabAccessExcludesTab() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    WindowAccess mockWindowAccess = mock(WindowAccess.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    TabAccess mockTabAccess = mock(TabAccess.class);
    Tab mockTab = mock(Tab.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockWindowAccess);

    when(mockWindowAccess.getWindow()).thenReturn(mockWindow);

    List<TabAccess> tabAccessList = new ArrayList<>();
    tabAccessList.add(mockTabAccess);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(tabAccessList);
    when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());

    when(mockTabAccess.isActive()).thenReturn(true);
    when(mockTabAccess.isAllowRead()).thenReturn(false);
    when(mockTabAccess.getTab()).thenReturn(mockTab);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);
      JSONObject result = windowBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("tabs"));
      JSONArray tabs = result.getJSONArray("tabs");
      assertEquals(0, tabs.length());
    }
  }

  /**
   * Tests that the toJSON method includes tabs when the user has active tab access and read permission.
   * It mocks the OBDal, OBContext, and DataToJsonConverter to simulate a scenario
   * where the current role has access to the window and tabs.
   *
   * @throws Exception
   *     if any unexpected error occurs during the test execution.
   */
  @Test
  void toJSONWithActiveTabAccessAndReadPermissionIncludesTab() throws Exception {
    OBContext mockContext = mock(OBContext.class);
    Language mockLanguage = mock(Language.class);
    OBDal mockOBDal = mock(OBDal.class);
    Window mockWindow = mock(Window.class);
    WindowAccess mockWindowAccess = mock(WindowAccess.class);
    Role mockRole = mock(Role.class);
    OBCriteria<WindowAccess> mockCriteria = mock(OBCriteria.class);

    TabAccess mockTabAccess = mock(TabAccess.class);
    Tab mockTab = mock(Tab.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
    when(mockContext.getRole()).thenReturn(mockRole);

    when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
    when(mockOBDal.createCriteria(WindowAccess.class)).thenReturn(mockCriteria);
    when(mockCriteria.add(any())).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockWindowAccess);

    when(mockWindowAccess.getWindow()).thenReturn(mockWindow);

    List<TabAccess> tabAccessList = new ArrayList<>();
    tabAccessList.add(mockTabAccess);
    when(mockWindowAccess.getADTabAccessList()).thenReturn(tabAccessList);
    when(mockWindow.getADTabList()).thenReturn(new ArrayList<>());

    when(mockTabAccess.isActive()).thenReturn(true);
    when(mockTabAccess.isAllowRead()).thenReturn(true);
    when(mockTabAccess.getTab()).thenReturn(mockTab);
    when(mockTab.getId()).thenReturn("tab-id");
    when(mockTab.getDisplayLogic()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               when(mock.toJsonObject(any(), any())).thenReturn(windowJson);
             });
         MockedConstruction<TabBuilder> ignored1 = mockConstruction(TabBuilder.class,
             (mock, context) -> {
               JSONObject tabJson = new JSONObject();
               tabJson.put("id", "tab-id");
               when(mock.toJSON()).thenReturn(tabJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(mockOBDal);

      WindowBuilder windowBuilder = new WindowBuilder(WINDOW_ID);
      JSONObject result = windowBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("tabs"));
      JSONArray tabs = result.getJSONArray("tabs");
      assertEquals(1, tabs.length());

      JSONObject tabJson = tabs.getJSONObject(0);
      assertEquals("tab-id", tabJson.getString("id"));
    }
  }
}
