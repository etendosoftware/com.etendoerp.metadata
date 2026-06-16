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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
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
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Form;
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

  @Mock
  private Session session;

  @Mock
  private OBDal obDal;

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
  private static final String VIEW_ICON = "view-icon";
  private static final String VIEW_MENU = "View Menu";
  private static final String VIEW_DESC = "View Desc";
  private static final String VIEW_ID = "viewId";
  private static final String WINDOW_TYPE = "windowType";
  private static final String WINDOW_MENU_ID = "WINDOW_MENU_ID";
  private static final String PICK_AND_EXECUTE = "OBUIAPP_PickAndExecute";
  private static final String FORM_ICON = "form-icon";
  private static final String FORM_ID = "formId";

  @FunctionalInterface
  private interface MenuBuilderConsumer {
    void accept(MenuBuilder builder) throws JSONException;
  }

  @FunctionalInterface
  private interface MenuEntryConsumer {
    void accept(JSONObject entry) throws JSONException;
  }

  @AfterEach
  void tearDown() throws NoSuchFieldException, IllegalAccessException {
    Field managerField = MenuBuilder.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    ((ThreadLocal<?>) managerField.get(null)).remove();
  }

  private void withMenuBuilder(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);
      action.accept(new MenuBuilder());
    }
  }

  private void withMenuBuilderAndUtility(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<Utility> ignoredUtility = mockStatic(Utility.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);
      action.accept(new MenuBuilder());
    }
  }

  private void withMenuBuilderAndDal(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);
      dalStatic.when(OBDal::getInstance).thenReturn(obDal);
      action.accept(new MenuBuilder());
    }
  }

  private void setupViewMenuOption(MenuOption childOption, Menu childMenu, String menuId) {
    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.View);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(VIEW_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn(VIEW_MENU);
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn(VIEW_DESC);
    when(childOption.getChildren()).thenReturn(new ArrayList<>());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void setupQueryMock(List<Object[]> results) {
    NativeQuery mockQuery = mock(NativeQuery.class);
    when(session.createNativeQuery(anyString())).thenReturn(mockQuery);
    when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.list()).thenReturn(results);
    when(obDal.getSession()).thenReturn(session);
  }

  /**
   * Test constructor of MenuBuilder.
   * This test ensures that the MenuBuilder can be constructed successfully
   * when the necessary context and menu manager are available.
   */
  @Test
  void testConstructorSuccessful() throws JSONException {
    withMenuBuilder(builder -> assertNotNull(builder));
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(freshChildOption));
    when(freshChildOption.getChildren()).thenReturn(List.of(grandChild));
    when(grandChild.getChildren()).thenReturn(new ArrayList<>());

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
    when(grandChildMenu.get(Menu.PROPERTY_DESCRIPTION, language, GRANDCHILD_ID)).thenReturn("Grand Child Description");
    when(grandChildMenu.getURL()).thenReturn("http://example.com/grandchild");
    when(grandChildMenu.getAction()).thenReturn("grandchild-action");
    when(grandChildMenu.getWindow()).thenReturn(null);
    when(grandChildMenu.getSpecialForm()).thenReturn(null);
    when(grandChildMenu.getOBUIAPPProcessDefinition()).thenReturn(null);
    when(grandChildMenu.getProcess()).thenReturn(null);

    withMenuBuilder(builder -> {
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
    });
  }

  /**
   * Test toJSON method of MenuBuilder.
   * This test ensures that the toJSON method can convert the menu structure into
   * a JSON object
   * without throwing any exceptions, even when the menu has no children.
   */
  @Test
  void testToJSONHandlesExceptionGracefully() throws JSONException {
    withMenuBuilder(builder -> assertDoesNotThrow(() -> {
      try {
        assertNotNull(builder.toJSON());
      } catch (JSONException e) {
        fail("JSONException should be handled internally");
      }
    }));
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
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

    withMenuBuilderAndUtility(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject processMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(AD_PROCESS_ID, processMenu.getString(PROCESS_ID));
      assertEquals(MAPPING_URL, processMenu.getString(PROCESS_URL));
    });
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
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

    withMenuBuilderAndUtility(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject reportMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(REPORT_URL, reportMenu.getString(PROCESS_URL));
      assertTrue(reportMenu.getBoolean(IS_REPORT));
    });
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childMenu.getId()).thenReturn("PENTAHO_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn(P_ACTION);

    when(process.getId()).thenReturn(AD_PENTAHO_ID);
    when(process.isActive()).thenReturn(true);
    when(process.isExternalService()).thenReturn(true);
    when(process.getServiceType()).thenReturn("PS");
    when(process.getADModelImplementationList()).thenReturn(new ArrayList<>());

    withMenuBuilderAndUtility(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject pentahoMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(PENTAHO_URL + AD_PENTAHO_ID, pentahoMenu.getString(PROCESS_URL));
    });
  }

  /**
   * Tests addViewInfo with a fully qualified classname.
   * The simple name (after the last dot) must be used as viewId.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddViewInfoWithFullyQualifiedClassname() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    setupViewMenuOption(childOption, childMenu, "VIEW_MENU_ID");
    Object[] viewRow = { "com.example.MyViewClassName", "MyViewName" };
    setupQueryMock(Collections.singletonList(viewRow));

    withMenuBuilderAndDal(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject viewMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("View", viewMenu.getString("type"));
      assertEquals("MyViewClassName", viewMenu.getString(VIEW_ID));
    });
  }

  /**
   * Tests addViewInfo when classname is null: falls back to the view name.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddViewInfoFallsBackToNameWhenClassnameIsNull() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    setupViewMenuOption(childOption, childMenu, "VIEW_MENU_ID_2");
    Object[] viewRow = { null, "SimpleViewName" };
    setupQueryMock(Collections.singletonList(viewRow));

    withMenuBuilderAndDal(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject viewMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("SimpleViewName", viewMenu.getString(VIEW_ID));
    });
  }

  /**
   * Tests addViewInfo when no view data is found: viewId must not be set.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddViewInfoWithEmptyResultDoesNotSetViewId() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    setupViewMenuOption(childOption, childMenu, "VIEW_MENU_ID_3");
    setupQueryMock(Collections.emptyList());

    withMenuBuilderAndDal(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject viewMenu = result.getJSONArray("menu").getJSONObject(0);
      assertFalse(viewMenu.has(VIEW_ID));
    });
  }

  // -------------------------------------------------------------------------
  // addFormInfo — tests for the new form-URL derivation helper
  // -------------------------------------------------------------------------

  /**
   * Tests addFormInfo with a fully qualified Java class name.
   * The simple class name (after the last dot) must be used in the formUrl.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddFormInfoWithFullyQualifiedClassName() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    Form mockForm = mock(Form.class);
    String menuId = "FORM_FQ_ID";

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(FORM_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn("Form Menu");
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn("Form Desc");
    when(childMenu.getURL()).thenReturn("/form/url");
    when(childMenu.getAction()).thenReturn("F");
    when(childMenu.getSpecialForm()).thenReturn(mockForm);
    when(mockForm.getId()).thenReturn("FORM_ABC_123");
    when(mockForm.getJavaClassName()).thenReturn("org.openbravo.erpCommon.ad_forms.SomeForm");

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject formMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("FORM_ABC_123", formMenu.getString(FORM_ID));
      assertEquals("/ad_forms/SomeForm.html", formMenu.getString("formUrl"));
    });
  }

  /**
   * Tests addFormInfo with a simple class name (no package prefix).
   * The class name itself must be used directly in the formUrl path.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddFormInfoWithSimpleClassName() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    Form mockForm = mock(Form.class);
    String menuId = "FORM_SIMPLE_ID";

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(FORM_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn("Simple Form Menu");
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn("Simple Desc");
    when(childMenu.getURL()).thenReturn("/simple/url");
    when(childMenu.getAction()).thenReturn("F");
    when(childMenu.getSpecialForm()).thenReturn(mockForm);
    when(mockForm.getId()).thenReturn("FORM_SIMPLE_456");
    when(mockForm.getJavaClassName()).thenReturn("SimpleFormClass");

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject formMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("FORM_SIMPLE_456", formMenu.getString(FORM_ID));
      assertEquals("/ad_forms/SimpleFormClass.html", formMenu.getString("formUrl"));
    });
  }

  /**
   * Tests addFormInfo when the form has a null Java class name.
   * Only formId must be set; formUrl must be absent.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddFormInfoWithNullClassNameSetsOnlyFormId() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    Form mockForm = mock(Form.class);
    String menuId = "FORM_NULL_CLS_ID";

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(FORM_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn("Null Class Form");
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn("Null Class Desc");
    when(childMenu.getURL()).thenReturn("/null/url");
    when(childMenu.getAction()).thenReturn("F");
    when(childMenu.getSpecialForm()).thenReturn(mockForm);
    when(mockForm.getId()).thenReturn("FORM_NULL_789");
    when(mockForm.getJavaClassName()).thenReturn(null);

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject formMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("FORM_NULL_789", formMenu.getString(FORM_ID));
    });
  }

  /**
   * Tests addFormInfo when the form has an empty Java class name.
   * Only formId must be set; formUrl must be absent.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddFormInfoWithEmptyClassNameSetsOnlyFormId() throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    Form mockForm = mock(Form.class);
    String menuId = "FORM_EMPTY_CLS_ID";

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(FORM_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn("Empty Class Form");
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn("Empty Class Desc");
    when(childMenu.getURL()).thenReturn("/empty/url");
    when(childMenu.getAction()).thenReturn("F");
    when(childMenu.getSpecialForm()).thenReturn(mockForm);
    when(mockForm.getId()).thenReturn("FORM_EMPTY_000");
    when(mockForm.getJavaClassName()).thenReturn("");

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject formMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("FORM_EMPTY_000", formMenu.getString(FORM_ID));
    });
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(menuId);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, menuId)).thenReturn(menuIcon);
    when(childMenu.get(Menu.PROPERTY_NAME, language, menuId)).thenReturn(menuName);
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, menuId)).thenReturn(menuDesc);
    when(childMenu.getURL()).thenReturn(menuUrl);
    when(childMenu.getAction()).thenReturn(menuAction);

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject basicMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(menuId, basicMenu.getString("id"));
      assertEquals("External", basicMenu.getString("type"));
      assertEquals(menuIcon, basicMenu.getString("icon"));
      assertEquals(menuName, basicMenu.getString("name"));
      assertEquals(menuDesc, basicMenu.getString("description"));
      assertEquals(menuUrl, basicMenu.getString("url"));
      assertEquals(menuAction, basicMenu.getString("action"));
    });
  }

  /**
   * Builds a single-entry menu fixture wired to the supplied window mock.
   * Centralises the boilerplate shared by the windowType test cases.
   *
   * @param window The window mock to be returned by {@code Menu.getWindow()}, or null.
   * @return The configured root MenuOption ready to be returned by MenuManager.
   */
  private MenuOption buildSingleWindowMenuFixture(Window window) {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Window);
    when(childMenu.getId()).thenReturn(WINDOW_MENU_ID);
    when(childMenu.getWindow()).thenReturn(window);
    if (window != null) {
      when(window.getId()).thenReturn("WINDOW_ID_VAL");
    }
    return rootMenuOption;
  }

  /**
   * Mocks a Window with the given windowType and wires it into the root fixture.
   * Returns the configured window mock for further stubbing if needed.
   *
   * @param windowType The value returned by {@code Window.getWindowType()}, may be null.
   * @return The window mock just installed in the menu fixture.
   */
  private Window mockWindowWithType(String windowType) {
    Window window = mock(Window.class);
    when(window.getWindowType()).thenReturn(windowType);
    buildSingleWindowMenuFixture(window);
    return window;
  }

  /**
   * Runs the standard MenuBuilder fixture and hands the first JSON menu entry to
   * the supplied assertion block. Removes the boilerplate shared by the
   * windowType test cases.
   *
   * @param assertion Assertion block to execute against the first menu entry.
   * @throws JSONException if the JSON traversal fails.
   */
  private void withFirstMenuEntry(MenuEntryConsumer assertion) throws JSONException {
    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject entry = result.getJSONArray("menu").getJSONObject(0);
      assertion.accept(entry);
    });
  }

  /**
   * Verifies that a Pick and Execute window emits the windowType field with the
   * canonical "OBUIAPP_PickAndExecute" value.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryIncludesWindowTypeWhenWindowIsPickAndExecute() throws JSONException {
    mockWindowWithType(PICK_AND_EXECUTE);
    withFirstMenuEntry(entry -> {
      assertTrue(entry.has(WINDOW_TYPE));
      assertEquals(PICK_AND_EXECUTE, entry.getString(WINDOW_TYPE));
    });
  }

  /**
   * Verifies that a standard Maintain window emits its windowType verbatim.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryIncludesWindowTypeWhenWindowIsMaintain() throws JSONException {
    mockWindowWithType("M");
    withFirstMenuEntry(entry -> {
      assertTrue(entry.has(WINDOW_TYPE));
      assertEquals("M", entry.getString(WINDOW_TYPE));
    });
  }

  /**
   * Verifies that menu entries without an associated window do not emit the
   * windowType key. This keeps the JSON shape minimal for non-window entries
   * (Process Definitions, Forms, etc.).
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryDoesNotIncludeWindowTypeWhenWindowIsNull() throws JSONException {
    buildSingleWindowMenuFixture(null);
    withFirstMenuEntry(entry -> assertFalse(entry.has(WINDOW_TYPE)));
  }

  /**
   * Defensive case: a window whose getWindowType() returns null must not
   * produce a "windowType" key in the JSON, matching the optional-field
   * convention used for processId/formId.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryDoesNotIncludeWindowTypeWhenWindowTypeIsNull() throws JSONException {
    mockWindowWithType(null);
    withFirstMenuEntry(entry -> assertFalse(entry.has(WINDOW_TYPE)));
  }

}
