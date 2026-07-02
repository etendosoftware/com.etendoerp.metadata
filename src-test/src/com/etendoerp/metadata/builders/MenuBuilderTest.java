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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.hibernate.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.GlobalMenu;
import org.openbravo.client.application.MenuManager;
import org.openbravo.client.application.MenuManager.MenuOption;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
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

  @Mock
  private Role role;

  @Mock
  private GlobalMenu globalMenu;

  private static final String TEST_ROLE_ID = "TEST_ROLE_ID";
  private static final String TEST_LANG_ID = "TEST_LANG_ID";
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
  private static final String VIEW_MENU_ID = "VIEW_MENU_ID";
  private static final String FORM_MENU_ID = "FORM_MENU_ID";
  private static final String FORM_URL = "formUrl";
  private static final String WINDOW_TYPE = "windowType";
  private static final String WINDOW_MENU_ID = "WINDOW_MENU_ID";
  private static final String WINDOW_ID = "WINDOW_ID_VAL";
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
    MenuBuilder.clearMenuCache();
  }

  /**
   * Wires the shared OBContext stubs used by every builder helper: the static
   * {@code getOBContext()} accessor, the language (read by the {@code Builder} superclass
   * constructor) and the role/language identifiers used to compose the menu cache key. The
   * role and identifier stubs are lenient because constructor-only tests never reach the
   * cache-key path.
   *
   * @param obContextStatic The mocked static OBContext.
   */
  private void stubContext(MockedStatic<OBContext> obContextStatic) {
    obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getLanguage()).thenReturn(language);
    lenient().when(obContext.getRole()).thenReturn(role);
    lenient().when(role.getId()).thenReturn(TEST_ROLE_ID);
    lenient().when(language.getId()).thenReturn(TEST_LANG_ID);
  }

  /**
   * Stubs the CDI lookup performed by the {@code MenuBuilder} constructor so it resolves to the
   * shared {@link GlobalMenu} mock instead of hitting the real bean manager.
   *
   * @param weldStatic The mocked static WeldUtils.
   */
  private void stubWeld(MockedStatic<WeldUtils> weldStatic) {
    weldStatic.when(() -> WeldUtils.getInstanceFromStaticBeanManager(GlobalMenu.class))
        .thenReturn(globalMenu);
  }

  private void withMenuBuilder(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<WeldUtils> weldStatic = mockStatic(WeldUtils.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      stubContext(obContextStatic);
      stubWeld(weldStatic);
      action.accept(new MenuBuilder());
    }
  }

  private void withMenuBuilderAndUtility(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<WeldUtils> weldStatic = mockStatic(WeldUtils.class);
        MockedStatic<Utility> ignoredUtility = mockStatic(Utility.class);
        MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      stubContext(obContextStatic);
      stubWeld(weldStatic);
      dalStatic.when(OBDal::getInstance).thenReturn(obDal);
      action.accept(new MenuBuilder());
    }
  }

  private void withMenuBuilderAndDal(MenuBuilderConsumer action) throws JSONException {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<WeldUtils> weldStatic = mockStatic(WeldUtils.class);
        MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
        MockedConstruction<MenuManager> ignored = mockConstruction(MenuManager.class,
            (mock, context) -> when(mock.getMenu()).thenReturn(rootMenuOption))) {
      stubContext(obContextStatic);
      stubWeld(weldStatic);
      dalStatic.when(OBDal::getInstance).thenReturn(obDal);
      action.accept(new MenuBuilder());
    }
  }

  /**
   * Stubs the raw Hibernate query used by {@code MenuBuilder.loadDefaultMappings()} so it
   * returns the supplied default mappings. Mirrors the batch-load path (a single typed
   * {@code createQuery(...).list()}), which replaces the previous per-process lazy iteration.
   *
   * @param mappings The default mappings the query must return.
   */
  @SuppressWarnings("unchecked")
  private void setupDefaultMappingsQuery(List<ModelImplementationMapping> mappings) {
    Query<ModelImplementationMapping> mappingsQuery = mock(Query.class);
    when(obDal.getSession()).thenReturn(session);
    when(session.createQuery(anyString(), eq(ModelImplementationMapping.class))).thenReturn(mappingsQuery);
    when(mappingsQuery.list()).thenReturn(mappings);
  }

  /**
   * Builds a default {@link ModelImplementationMapping} mock wired to the given process id and
   * mapping name, replicating the {@code modelObject.process.id} navigation that
   * {@code loadDefaultMappings()} uses to index the mapping.
   *
   * @param processId   The owning process id.
   * @param mappingName The mapping name (used as the process URL).
   * @return The configured mapping mock.
   */
  private ModelImplementationMapping mockDefaultMapping(String processId, String mappingName) {
    ModelImplementationMapping mim = mock(ModelImplementationMapping.class);
    ModelImplementation mi = mock(ModelImplementation.class);
    org.openbravo.model.ad.ui.Process owner = mock(org.openbravo.model.ad.ui.Process.class);
    when(mim.getModelObject()).thenReturn(mi);
    when(mi.getProcess()).thenReturn(owner);
    when(owner.getId()).thenReturn(processId);
    lenient().when(mim.getMappingName()).thenReturn(mappingName);
    return mim;
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
   * Verifies that the constructor points the MenuManager at the shared, CDI-managed
   * {@link GlobalMenu} singleton (resolved via {@link WeldUtils}) rather than a per-thread
   * instance. Sharing the singleton is what lets the classic menu-cache invalidation reach the
   * tree the new-UI menu is rebuilt from, so a renamed window/menu is reflected on any thread.
   */
  @Test
  void testConstructorUsesSharedGlobalMenuSingleton() {
    try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
        MockedStatic<WeldUtils> weldStatic = mockStatic(WeldUtils.class);
        MockedConstruction<MenuManager> managerConstruction = mockConstruction(MenuManager.class)) {

      obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
      when(obContext.getLanguage()).thenReturn(language);
      stubWeld(weldStatic);

      new MenuBuilder();

      MenuManager constructedManager = managerConstruction.constructed().get(0);
      verify(constructedManager).setGlobalMenuOptions(globalMenu);
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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Process);
    when(childMenu.getId()).thenReturn("PROCESS_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);
    when(childMenu.getAction()).thenReturn(P_ACTION);

    when(process.getId()).thenReturn(AD_PROCESS_ID);
    when(process.isActive()).thenReturn(true);
    when(process.getUIPattern()).thenReturn(STANDARD);
    setupDefaultMappingsQuery(List.of(mockDefaultMapping(AD_PROCESS_ID, MAPPING_URL)));

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

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.Report);
    when(childMenu.getId()).thenReturn("REPORT_ID_VAL");
    when(childMenu.getProcess()).thenReturn(process);

    when(process.getId()).thenReturn(AD_REPORT_ID);
    when(process.isActive()).thenReturn(true);
    when(process.isReport()).thenReturn(true);
    setupDefaultMappingsQuery(List.of(mockDefaultMapping(AD_REPORT_ID, REPORT_URL)));

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
    setupDefaultMappingsQuery(Collections.emptyList());

    withMenuBuilderAndUtility(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject pentahoMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(PENTAHO_URL + AD_PENTAHO_ID, pentahoMenu.getString(PROCESS_URL));
    });
  }

  /**
   * Builds a Standard, active process menu entry wired to the given menu and process ids.
   * Centralises the boilerplate shared by the batch-loading tests.
   *
   * @param menuId    The menu entry id.
   * @param processId The process id.
   * @return The configured MenuOption.
   */
  private MenuOption mockProcessEntry(String menuId, String processId) {
    MenuOption option = mock(MenuOption.class);
    Menu menu = mock(Menu.class);
    org.openbravo.model.ad.ui.Process process = mock(org.openbravo.model.ad.ui.Process.class);
    when(option.getMenu()).thenReturn(menu);
    when(option.getType()).thenReturn(MenuManager.MenuEntryType.Process);
    when(menu.getId()).thenReturn(menuId);
    when(menu.getProcess()).thenReturn(process);
    when(process.getId()).thenReturn(processId);
    when(process.isActive()).thenReturn(true);
    when(process.getUIPattern()).thenReturn(STANDARD);
    return option;
  }

  /**
   * Verifies the N+1 fix: resolving default mappings for several process entries executes the
   * batch query exactly once (it is loaded lazily on the first process and memoised), instead of
   * one lazy load per process. The batch runs on the raw Hibernate session, which applies no
   * active/client/organization filter, matching the original lazy-collection semantics.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testDefaultMappingsQueryRunsOnceForMultipleProcesses() throws JSONException {
    MenuOption firstProcess = mockProcessEntry("MENU_1", "PROC_1");
    MenuOption secondProcess = mockProcessEntry("MENU_2", "PROC_2");
    when(rootMenuOption.getChildren()).thenReturn(List.of(firstProcess, secondProcess));
    setupDefaultMappingsQuery(
        List.of(mockDefaultMapping("PROC_1", MAPPING_URL), mockDefaultMapping("PROC_2", REPORT_URL)));

    withMenuBuilderAndUtility(builder -> {
      builder.toJSON();
      verify(session, times(1)).createQuery(anyString(), eq(ModelImplementationMapping.class));
    });
  }

  /**
   * Verifies that a second build for the same role and language is served from the cache: the
   * cached JSON instance is returned verbatim rather than rebuilt.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testCacheHitReturnsSameMenuInstance() throws JSONException {
    when(rootMenuOption.getChildren()).thenReturn(Collections.emptyList());

    withMenuBuilder(builder -> {
      JSONObject first = builder.toJSON();
      JSONObject second = builder.toJSON();
      assertSame(first, second);
    });
  }

  /**
   * Verifies that a different role produces a different cache key and therefore a freshly built
   * menu (cache miss), so roles never share cached menus.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testCacheMissForDifferentRoleRebuildsMenu() throws JSONException {
    when(rootMenuOption.getChildren()).thenReturn(Collections.emptyList());

    withMenuBuilder(builder -> {
      when(role.getId()).thenReturn("ROLE_A", "ROLE_B");
      JSONObject first = builder.toJSON();
      JSONObject second = builder.toJSON();
      assertNotSame(first, second);
    });
  }

  /**
   * Verifies that clearing the menu cache forces the next build to reconstruct the JSON.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testClearMenuCacheForcesRebuild() throws JSONException {
    when(rootMenuOption.getChildren()).thenReturn(Collections.emptyList());

    withMenuBuilder(builder -> {
      JSONObject first = builder.toJSON();
      MenuBuilder.clearMenuCache();
      JSONObject second = builder.toJSON();
      assertNotSame(first, second);
    });
  }

  /**
   * Tests addViewInfo across its three derivation paths, all emitting {@code type = "View"}:
   * a fully qualified classname yields its simple name as viewId; a null classname falls back to
   * the view name; and an empty query result leaves viewId unset. {@code rowPresent} distinguishes
   * "a row with a null classname" from "no row at all".
   *
   * @param rowPresent     Whether the native query returns a view row.
   * @param className      The classname column of the returned row (null when absent).
   * @param viewName       The name column of the returned row.
   * @param expectedViewId The expected viewId, or null when it must be omitted.
   * @throws JSONException if there is an error during JSON construction
   */
  @ParameterizedTest
  @CsvSource(nullValues = "NULL", value = {
      "true, com.example.MyViewClassName, MyViewName, MyViewClassName",
      "true, NULL, SimpleViewName, SimpleViewName",
      "false, NULL, NULL, NULL"
  })
  void testAddViewInfoDerivesViewId(boolean rowPresent, String className, String viewName,
      String expectedViewId) throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    setupViewMenuOption(childOption, childMenu, VIEW_MENU_ID);
    List<Object[]> rows = rowPresent
        ? Collections.singletonList(new Object[] { className, viewName })
        : Collections.emptyList();
    setupQueryMock(rows);

    withMenuBuilderAndDal(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject viewMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals("View", viewMenu.getString("type"));
      if (expectedViewId == null) {
        assertFalse(viewMenu.has(VIEW_ID));
      } else {
        assertEquals(expectedViewId, viewMenu.getString(VIEW_ID));
      }
    });
  }

  // -------------------------------------------------------------------------
  // addFormInfo — tests for the new form-URL derivation helper
  // -------------------------------------------------------------------------

  /**
   * Tests addFormInfo across its Java-class-name derivation paths: a fully qualified name and a
   * simple name both yield a {@code /ad_forms/<SimpleName>.html} formUrl, while a null or empty
   * class name sets only formId and omits formUrl. {@code formId} is always emitted verbatim.
   *
   * @param javaClassName   The form's Java class name (null/empty for the formId-only cases).
   * @param formId          The form id, expected verbatim in the JSON.
   * @param expectedFormUrl The expected formUrl, or null when it must be omitted.
   * @throws JSONException if there is an error during JSON construction
   */
  @ParameterizedTest
  @CsvSource(nullValues = "NULL", value = {
      "org.openbravo.erpCommon.ad_forms.SomeForm, FORM_ABC_123, /ad_forms/SomeForm.html",
      "SimpleFormClass, FORM_SIMPLE_456, /ad_forms/SimpleFormClass.html",
      "NULL, FORM_NULL_789, NULL",
      "'', FORM_EMPTY_000, NULL"
  })
  void testAddFormInfoDerivesFormUrlFromJavaClassName(String javaClassName, String formId,
      String expectedFormUrl) throws JSONException {
    MenuOption childOption = mock(MenuOption.class);
    Menu childMenu = mock(Menu.class);
    Form mockForm = mock(Form.class);

    when(rootMenuOption.getChildren()).thenReturn(List.of(childOption));
    when(childOption.getMenu()).thenReturn(childMenu);
    when(childOption.getType()).thenReturn(MenuManager.MenuEntryType.External);
    when(childMenu.getId()).thenReturn(FORM_MENU_ID);
    when(childMenu.get(Menu.PROPERTY_ETMETAICON, language, FORM_MENU_ID)).thenReturn(FORM_ICON);
    when(childMenu.get(Menu.PROPERTY_NAME, language, FORM_MENU_ID)).thenReturn("Form Menu");
    when(childMenu.get(Menu.PROPERTY_DESCRIPTION, language, FORM_MENU_ID)).thenReturn("Form Desc");
    when(childMenu.getURL()).thenReturn("/form/url");
    when(childMenu.getAction()).thenReturn("F");
    when(childMenu.getSpecialForm()).thenReturn(mockForm);
    when(mockForm.getId()).thenReturn(formId);
    when(mockForm.getJavaClassName()).thenReturn(javaClassName);

    withMenuBuilder(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject formMenu = result.getJSONArray("menu").getJSONObject(0);
      assertEquals(formId, formMenu.getString(FORM_ID));
      if (expectedFormUrl == null) {
        assertFalse(formMenu.has(FORM_URL));
      } else {
        assertEquals(expectedFormUrl, formMenu.getString(FORM_URL));
      }
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
      when(window.getId()).thenReturn(WINDOW_ID);
    }
    return rootMenuOption;
  }

  /**
   * Builds the windowType fixture reproducing the production scenario: the window
   * held by the menu tree is a (potentially detached) proxy, while the actual type
   * is read from the instance re-fetched by {@code OBDal.get(Window.class, id)}.
   * The detached window only answers {@code getId()}; reading {@code getWindowType()}
   * on it is never expected.
   *
   * @param windowType The value returned by the persistent {@code Window.getWindowType()}, may be null.
   * @return The detached window mock installed in the menu tree, for verification.
   */
  private Window mockPersistentWindowWithType(String windowType) {
    Window detachedWindow = mock(Window.class);
    buildSingleWindowMenuFixture(detachedWindow);

    Window persistentWindow = mock(Window.class);
    when(persistentWindow.getWindowType()).thenReturn(windowType);
    when(obDal.get(Window.class, WINDOW_ID)).thenReturn(persistentWindow);
    return detachedWindow;
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
   * Same as {@link #withFirstMenuEntry} but under the DAL-mocking context, required
   * since resolving the windowType re-fetches the window via {@code OBDal.getInstance()}.
   *
   * @param assertion Assertion block to execute against the first menu entry.
   * @throws JSONException if the JSON traversal fails.
   */
  private void withFirstMenuEntryAndDal(MenuEntryConsumer assertion) throws JSONException {
    withMenuBuilderAndDal(builder -> {
      JSONObject result = builder.toJSON();
      JSONObject entry = result.getJSONArray("menu").getJSONObject(0);
      assertion.accept(entry);
    });
  }

  /**
   * Regression for the LazyInitializationException: the window held by the menu
   * tree is a detached proxy, so the type must be read from the instance re-fetched
   * via the DAL and never directly from the detached proxy. Verifies both the
   * emitted value and that {@code getWindowType()} is never called on the proxy.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryReadsWindowTypeFromRefetchedWindow() throws JSONException {
    Window detachedWindow = mockPersistentWindowWithType(PICK_AND_EXECUTE);
    withFirstMenuEntryAndDal(entry -> {
      assertTrue(entry.has(WINDOW_TYPE));
      assertEquals(PICK_AND_EXECUTE, entry.getString(WINDOW_TYPE));
    });
    verify(detachedWindow, never()).getWindowType();
  }

  /**
   * Verifies that a standard Maintain window emits its windowType verbatim.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryIncludesWindowTypeWhenWindowIsMaintain() throws JSONException {
    mockPersistentWindowWithType("M");
    withFirstMenuEntryAndDal(entry -> {
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
    mockPersistentWindowWithType(null);
    withFirstMenuEntryAndDal(entry -> assertFalse(entry.has(WINDOW_TYPE)));
  }

  /**
   * Defensive case: when the window can no longer be re-fetched (e.g. removed),
   * {@code OBDal.get} returns null and the entry must omit the windowType key
   * without raising any exception.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testMenuEntryDoesNotIncludeWindowTypeWhenWindowNotFound() throws JSONException {
    buildSingleWindowMenuFixture(mock(Window.class));
    when(obDal.get(Window.class, WINDOW_ID)).thenReturn(null);
    withFirstMenuEntryAndDal(entry -> assertFalse(entry.has(WINDOW_TYPE)));
  }

}
