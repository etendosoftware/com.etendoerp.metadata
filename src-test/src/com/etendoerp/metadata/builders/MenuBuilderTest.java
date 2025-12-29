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

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
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
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

/**
 * Test class for MenuBuilder.
 * This class tests the functionality of the MenuBuilder, ensuring it can construct a menu
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
   * Test constructor of MenuBuilder when MenuManager throws a NullPointerException.
   * This test ensures that the MenuBuilder can still be constructed even if the
   * MenuManager throws a NullPointerException, which is a common scenario in real-world applications.
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
   * This test verifies that the JSON output correctly represents a menu structure with nested children.
   * It also ensures that no JSONException is thrown during the process.
   *
   * @throws JSONException
   *     if there is an error during JSON construction (should not occur in this test)
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
   * This test ensures that the toJSON method can convert the menu structure into a JSON object
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
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testToJSONWithProcessDefaultMapping() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);
    ModelImplementation mi = mock(ModelImplementation.class);
    ModelImplementationMapping mim = mock(ModelImplementationMapping.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Process);
    when(childMenu.getId()).thenReturn("PROCESS_ID");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn("P");

    when(process.getId()).thenReturn("AD_PROCESS_ID");
    when(process.isActive()).thenReturn(true);
    when(process.getUIPattern()).thenReturn("Standard");
    when(process.getADModelImplementationList()).thenReturn(List.of(mi));
    when(mi.getADModelImplementationMappingList()).thenReturn(List.of(mim));
    when(mim.isDefault()).thenReturn(true);
    when(mim.getMappingName()).thenReturn("/mapping/url");

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
             (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject processMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("AD_PROCESS_ID", processMenu.getString("processId"));
      assertEquals("/mapping/url", processMenu.getString("processUrl"));
      assertEquals("Process", processMenu.getString("processType"));
    }
  }

  /**
   * Tests the toJSON method with a report process.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testToJSONWithReportProcess() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);
    ModelImplementation mi = mock(ModelImplementation.class);
    ModelImplementationMapping mim = mock(ModelImplementationMapping.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Report);
    when(childMenu.getId()).thenReturn("REPORT_ID");
    when(childMenu.getProcess()).thenReturn(process);

    when(process.getId()).thenReturn("AD_REPORT_ID");
    when(process.isActive()).thenReturn(true);
    when(process.isReport()).thenReturn(true);
    when(process.getADModelImplementationList()).thenReturn(List.of(mi));
    when(mi.getADModelImplementationMappingList()).thenReturn(List.of(mim));
    when(mim.isDefault()).thenReturn(true);
    when(mim.getMappingName()).thenReturn("/report/url");

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
             (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject reportMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("/report/url", reportMenu.getString("processUrl"));
      assertEquals("Report", reportMenu.getString("processType"));
      assertTrue(reportMenu.getBoolean("isReport"));
    }
  }

  /**
   * Tests the toJSON method with an external service process (Pentaho).
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testToJSONWithExternalServiceProcess() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);

    List<MenuOption> rootChildren = List.of(childOption);
    when(rootMenuOption.getChildren()).thenReturn(rootChildren);

    when(childOption.getMenu()).thenReturn(childMenu);
    when(childMenu.getId()).thenReturn("PENTAHO_ID");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn("P");

    when(process.getId()).thenReturn("AD_PENTAHO_ID");
    when(process.isActive()).thenReturn(true);
    when(process.isExternalService()).thenReturn(true);
    when(process.getServiceType()).thenReturn("PS");
    when(process.getADModelImplementationList()).thenReturn(new ArrayList<>());

    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
         MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
             (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);

      MenuBuilder builder = new MenuBuilder();
      JSONObject result = builder.toJSON();

      JSONObject pentahoMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("/utility/OpenPentaho.html?inpadProcessId=AD_PENTAHO_ID", pentahoMenu.getString("processUrl"));
    }
  }

}
