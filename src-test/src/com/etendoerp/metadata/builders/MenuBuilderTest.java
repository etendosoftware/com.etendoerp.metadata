package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.CHILDREN;
import static com.etendoerp.metadata.MetadataTestConstants.GRANDCHILD_ID;
import static com.etendoerp.metadata.MetadataTestConstants.PARENT_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.application.GlobalMenu;
import org.openbravo.client.application.MenuManager;
import org.openbravo.client.application.MenuManager.MenuOption;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

/**
 * Test class for MenuBuilder.
 * This class tests the functionality of the MenuBuilder, ensuring it can
 * construct a menu
 * and convert it to JSON format correctly, including handling nested children.
 */
@ExtendWith(MockitoExtension.class)
class MenuBuilderTest {

  @Mock
  private MenuOption rootMenuOption;

  @Mock
  private OBContext obContext;

  @Mock
  private Language language;

  private static final String PROCESS_URL = "processUrl";
  private static final String PROCESS_ID = "processId";
  private static final String IS_REPORT = "isReport";
  private static final String STANDARD = "Standard";
  private static final String MAPPING_URL = "/mapping/url";
  private static final String REPORT_URL = "/report/url";
  private static final String PENTAHO_URL = "/utility/OpenPentaho.html?inpadProcessId=";
  private static final String AD_PROCESS_ID = "AD_PROCESS_ID";
  private static final String AD_REPORT_ID = "AD_REPORT_ID";
  private static final String AD_PENTAHO_ID = "AD_PENTAHO_ID";
  private static final String P_ACTION = "P";

  @AfterEach
  void tearDown() throws NoSuchFieldException, IllegalAccessException {
    Field managerField = MenuBuilder.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    ((ThreadLocal<?>) managerField.get(null)).remove();
  }

  /**
   * Test constructor of MenuBuilder.
   * This test ensures that the MenuBuilder can be constructed successfully
   * when the necessary context and menu manager are available.
   */
  @Test
  void testConstructorSuccessful() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      assertDoesNotThrow(MenuBuilder::new);
    }
  }

  /**
   * Test constructor of MenuBuilder when MenuManager throws a
   * NullPointerException.
   * This test ensures that the MenuBuilder can still be constructed even if the
   * MenuManager throws a NullPointerException, which is a common scenario in
   * real-world applications.
   */
  @Test
  void testConstructorWithNullPointerException() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> {
              when(mock.getMenu()).thenThrow(new NullPointerException());
              doNothing().when(mock).setGlobalMenuOptions(any(GlobalMenu.class));
            })) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      assertDoesNotThrow(MenuBuilder::new);
    }
  }

  /**
   * Tests the toJSON method of MenuBuilder with nested children.
   * This test verifies that the JSON output correctly represents a menu structure
   * with nested children.
   * It also ensures that no JSONException is thrown during the process.
   *
   * @throws JSONException
   *                       if there is an error during JSON construction (should
   *                       not occur in this test)
   */
  @Test
  void testToJSONWithNestedChildren() throws JSONException {
    MenuOption grandChild = mock(MenuOption.class);
    Menu grandChildMenu = mock(Menu.class);
    MenuOption freshChildOption = mock(MenuOption.class);
    Menu freshParentMenu = mock(Menu.class);

    List<MenuOption> rootChildren = new ArrayList<>();
    rootChildren.add(freshChildOption);

    List<MenuOption> childChildren = new ArrayList<>();
    childChildren.add(grandChild);

    List<MenuOption> grandChildChildren = new ArrayList<>();

    when(rootMenuOption.getChildren()).thenReturn(rootChildren);
    when(freshChildOption.getChildren()).thenReturn(childChildren);
    when(grandChild.getChildren()).thenReturn(grandChildChildren);

    when(freshChildOption.getMenu()).thenReturn(freshParentMenu);
    when(freshChildOption.getType()).thenReturn(MenuManager.MenuEntryType.Summary);
    when(freshParentMenu.getId()).thenReturn(PARENT_ID);
    when(freshParentMenu.get(Menu.PROPERTY_ETMETAICON, language, PARENT_ID)).thenReturn("folder-icon");
    when(freshParentMenu.get(Menu.PROPERTY_NAME, language, PARENT_ID)).thenReturn("Parent Menu");
    when(freshParentMenu.get(Menu.PROPERTY_DESCRIPTION, language, PARENT_ID)).thenReturn("Parent Description");
    when(freshParentMenu.getURL()).thenReturn(null);
    when(freshParentMenu.getAction()).thenReturn(null);
    when(freshParentMenu.getWindow()).thenReturn(null);
    when(freshParentMenu.getSpecialForm()).thenReturn(null);
    when(freshParentMenu.getOBUIAPPProcessDefinition()).thenReturn(null);
    when(freshParentMenu.getProcess()).thenReturn(null);

    when(grandChild.getMenu()).thenReturn(grandChildMenu);
    when(grandChild.getType()).thenReturn(MenuManager.MenuEntryType.Window);
    when(grandChildMenu.getId()).thenReturn(GRANDCHILD_ID);
    when(grandChildMenu.get(Menu.PROPERTY_ETMETAICON, language, GRANDCHILD_ID)).thenReturn("child-icon");
    when(grandChildMenu.get(Menu.PROPERTY_NAME, language, GRANDCHILD_ID)).thenReturn("Grand Child");
    when(grandChildMenu.get(Menu.PROPERTY_DESCRIPTION, language, GRANDCHILD_ID)).thenReturn(
        "Grand Child Description");
    when(grandChildMenu.getURL()).thenReturn("http://example.com/grandchild");
    when(grandChildMenu.getAction()).thenReturn("grandchild-action");
    when(grandChildMenu.getWindow()).thenReturn(null);
    when(grandChildMenu.getSpecialForm()).thenReturn(null);
    when(grandChildMenu.getOBUIAPPProcessDefinition()).thenReturn(null);
    when(grandChildMenu.getProcess()).thenReturn(null);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      assertNotNull(result);
      JSONArray menuArray = result.getJSONArray("menu");
      assertEquals(1, menuArray.length());

      JSONObject parentMenu = menuArray.getJSONObject(0);
      assertEquals(PARENT_ID, parentMenu.getString("id"));
      assertTrue(parentMenu.has(CHILDREN));

      JSONArray children = parentMenu.getJSONArray(CHILDREN);
      assertEquals(1, children.length());

      JSONObject childMenu = children.getJSONObject(0);
      assertEquals(GRANDCHILD_ID, childMenu.getString("id"));
      assertEquals("Window", childMenu.getString("type"));
      assertFalse(childMenu.has(CHILDREN));
    }
  }

  /**
   * Test toJSON method of MenuBuilder.
   * This test ensures that the toJSON method can convert the menu structure into
   * a JSON object
   * without throwing any exceptions, even when the menu has no children.
   */
  @Test
  void testToJSONHandlesExceptionGracefully() {
    MenuOption freshChildOption = mock(MenuOption.class);
    MenuOption freshRootOption = mock(MenuOption.class);

    List<MenuOption> rootChildren = new ArrayList<>();
    rootChildren.add(freshChildOption);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(freshRootOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();

      assertDoesNotThrow(() -> {
        try {
          JSONObject result = builder.toJSON();
          assertNotNull(result);
        } catch (JSONException e) {
          fail("JSONException should be handled internally");
        }
      });
    }
  }

  /**
   * Tests the toJSON method with a process that has a default mapping.
   * This covers addProcessInfo and getDefaultMapping.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddProcessInfoWithDefaultMapping() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);
    ModelImplementation mi = mock(ModelImplementation.class);
    ModelImplementationMapping mim = mock(ModelImplementationMapping.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Process);
    when(childMenu.getId()).thenReturn("PROCESS_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn(P_ACTION);

    when(process.getId()).thenReturn(AD_PROCESS_ID);
    when(process.isActive()).thenReturn(true);
    when(process.getUIPattern()).thenReturn(STANDARD);
    when(process.getADModelImplementationList()).thenReturn(List.of(mi));
    when(mi.getADModelImplementationMappingList()).thenReturn(List.of(mim));
    when(mim.isDefault()).thenReturn(true);
    when(mim.getMappingName()).thenReturn(MAPPING_URL);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<Utility> utilityStatic = mockStatic(Utility.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject processMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(AD_PROCESS_ID, processMenu.getString(PROCESS_ID));
      assertEquals(MAPPING_URL, processMenu.getString(PROCESS_URL));
    }
  }

  /**
   * Tests the toJSON method with a report process.
   * This covers addProcessInfo with report logic.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddProcessInfoWithReport() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);
    ModelImplementation mi = mock(ModelImplementation.class);
    ModelImplementationMapping mim = mock(ModelImplementationMapping.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Report);
    when(childMenu.getId()).thenReturn("REPORT_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);

    when(process.getId()).thenReturn(AD_REPORT_ID);
    when(process.isActive()).thenReturn(true);
    when(process.isReport()).thenReturn(true);
    when(process.getADModelImplementationList()).thenReturn(List.of(mi));
    when(mi.getADModelImplementationMappingList()).thenReturn(List.of(mim));
    when(mim.isDefault()).thenReturn(true);
    when(mim.getMappingName()).thenReturn(REPORT_URL);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<Utility> utilityStatic = mockStatic(Utility.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject reportMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(REPORT_URL, reportMenu.getString(PROCESS_URL));
      assertTrue(reportMenu.getBoolean(IS_REPORT));
    }
  }

  /**
   * Tests the toJSON method with an external service process (Pentaho).
   * This covers getProcessUrl.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddProcessInfoWithExternalService() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childMenu.getId()).thenReturn("PENTAHO_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn(P_ACTION);

    when(process.getId()).thenReturn(AD_PENTAHO_ID);
    when(process.isActive()).thenReturn(true);
    when(process.isExternalService()).thenReturn(true);
    when(process.getServiceType()).thenReturn("PS");
    when(process.getADModelImplementationList()).thenReturn(new ArrayList<>());

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<Utility> utilityStatic = mockStatic(Utility.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject pentahoMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(PENTAHO_URL + AD_PENTAHO_ID, pentahoMenu.getString(PROCESS_URL));
    }
  }

  /**
   * Tests addBasicMenuInfo via toJSON.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddBasicMenuInfo() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    String menuId = "BASIC_MENU_ID";
    String menuName = "Basic Menu";
    String menuDesc = "Basic Description";
    String menuIcon = "basic-icon";
    String menuUrl = "/basic/url";
    String menuAction = "X";

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(menuIcon);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn(menuName);
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn(menuDesc);
    when(childMenu.getURL()).thenReturn(menuUrl);
    when(childMenu.getAction()).thenReturn(menuAction);

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject basicMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(menuId, basicMenu.getString("id"));
      assertEquals("External", basicMenu.getString("type"));
      assertEquals(menuIcon, basicMenu.getString("icon"));
      assertEquals(menuName, basicMenu.getString("name"));
      assertEquals(menuDesc, basicMenu.getString("description"));
      assertEquals(menuUrl, basicMenu.getString("url"));
      assertEquals(menuAction, basicMenu.getString("action"));
    }
  }

}
