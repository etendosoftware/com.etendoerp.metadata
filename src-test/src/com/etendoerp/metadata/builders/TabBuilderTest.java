package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.data.TabProcessor;

/**
 * Unit tests for TabBuilder using pure mocking approach.
 * This approach avoids complex dependencies and focuses on testing the core logic.
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderTest {

    private static final String TAB_ID = "test-tab-id";
    private static final String PARENT_TAB_ID = "parent-tab-id";
    private static final String TABLE_NAME = "TestTable";
    private static final String ENTITY_COLUMN_NAME = "testColumn";

    /**
     * Tests the constructor with Tab and TabAccess parameters.
     * It verifies that an instance of TabBuilder is created successfully.
     */
    @Test
    void constructorWithTabOnlyCreatesInstanceSuccessfully() {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);

            assertNotNull(tabBuilder);
        }
    }

    /**
     * Tests the constructor with Tab and TabAccess parameters.
     * It verifies that an instance of TabBuilder is created successfully.
     */
    @Test
    void constructorWithTabAndTabAccessCreatesInstanceSuccessfully() {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        TabAccess mockTabAccess = mock(TabAccess.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

            TabBuilder tabBuilder = new TabBuilder(mockTab, mockTabAccess);

            assertNotNull(tabBuilder);
        }
    }

    /**
     * Tests that a RuntimeException thrown during the execution of {@code toJSON}
     * is propagated as expected. This simulates a scenario where an unexpected
     * runtime error (such as a database error) occurs when accessing the table name.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithBasicTabReturnsValidJSON() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("1=1");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     tabJson.put("id", TAB_ID);
                     tabJson.put("name", "Test Tab");
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                .thenReturn(new JSONObject().put("field1", new JSONObject()));

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("filter"));
            assertTrue(result.has("displayLogic"));
            assertTrue(result.has("entityName"));
            assertTrue(result.has("parentColumns"));
            assertTrue(result.has("fields"));
            assertFalse(result.has("parentTabId"));

            assertEquals("1=1", result.getString("filter"));
            assertEquals("", result.getString("displayLogic"));
            assertEquals(TABLE_NAME, result.getString("entityName"));

            JSONArray parentColumns = result.getJSONArray("parentColumns");
            assertEquals(0, parentColumns.length());
        }
    }

    /**
     * Tests the toJSON method of TabBuilder with a parent tab.
     * It verifies that the parentTabId is included in the JSON output.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithParentTabIncludesParentTabId() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Tab mockParentTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn(null);
        when(mockTab.getDisplayLogic()).thenReturn(null);
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(mockParentTab);
        when(mockParentTab.getId()).thenReturn(PARENT_TAB_ID);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                .thenReturn(new JSONObject());

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("parentTabId"));
            assertEquals(PARENT_TAB_ID, result.getString("parentTabId"));
        }
    }

    /**
     * Tests the toJSON method of TabBuilder with a child tab that has parent columns.
     * It verifies that the parent columns are included in the JSON output.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithChildTabAndParentColumnsIncludesParentColumns() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        Column mockColumn1 = mock(Column.class);
        Column mockColumn2 = mock(Column.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(1L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);

        List<Column> columns = new ArrayList<>();
        columns.add(mockColumn1);
        columns.add(mockColumn2);
        when(mockTable.getADColumnList()).thenReturn(columns);

        when(mockColumn1.isLinkToParentColumn()).thenReturn(true);
        when(mockColumn2.isLinkToParentColumn()).thenReturn(false);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                .thenReturn(new JSONObject());
            mockedTabProcessor.when(() -> TabProcessor.getEntityColumnName(mockColumn1))
                .thenReturn(ENTITY_COLUMN_NAME);

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("parentColumns"));
            JSONArray parentColumns = result.getJSONArray("parentColumns");
            assertEquals(1, parentColumns.length());
            assertEquals(ENTITY_COLUMN_NAME, parentColumns.getString(0));
        }
    }

    /**
     * Tests the toJSON method of TabBuilder with a child tab that has no parent columns.
     * It verifies that the parentColumns array is empty in the JSON output.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithTabAccessUsesTabAccessFields() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        TabAccess mockTabAccess = mock(TabAccess.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        List<FieldAccess> fieldAccessList = new ArrayList<>();
        FieldAccess mockFieldAccess = mock(FieldAccess.class);
        fieldAccessList.add(mockFieldAccess);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
        when(mockTabAccess.getADFieldAccessList()).thenReturn(fieldAccessList);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTabAccess))
                .thenReturn(new JSONObject().put("fieldAccess1", new JSONObject()));

            TabBuilder tabBuilder = new TabBuilder(mockTab, mockTabAccess);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("fields"));

            mockedTabProcessor.verify(() -> TabProcessor.getTabFields(mockTabAccess));
        }
    }

    /**
     * Tests the toJSON method of TabBuilder when the TabAccess field list is empty.
     * It verifies that the TabProcessor.getTabFields method is called to retrieve fields.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithEmptyTabAccessFieldListUsesTabFields() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        TabAccess mockTabAccess = mock(TabAccess.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
        when(mockTabAccess.getADFieldAccessList()).thenReturn(new ArrayList<>());

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                .thenReturn(new JSONObject().put("field1", new JSONObject()));

            TabBuilder tabBuilder = new TabBuilder(mockTab, mockTabAccess);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("fields"));
            
            mockedTabProcessor.verify(() -> TabProcessor.getTabFields(mockTab));
        }
    }

    /**
     * Tests the toJSON method of TabBuilder when the tabAccess is null.
     * It verifies that the TabProcessor.getTabFields method is called to retrieve fields.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithNullTabAccessUsesTabFields() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                .thenReturn(new JSONObject().put("field1", new JSONObject()));

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("fields"));
        }
    }

    /**
     * Tests the toJSON method of TabBuilder when a RuntimeException occurs.
     * It verifies that the exception is propagated correctly.
     */
    @Test
    void toJSONWhenRuntimeExceptionOccursThrowsRuntimeException() {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenThrow(new RuntimeException("Database error"));

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);

            RuntimeException exception = assertThrows(RuntimeException.class, () -> tabBuilder.toJSON());
            assertEquals("Database error", exception.getMessage());
        }
    }

    /**
     * Tests the toJSON method of TabBuilder with a complex scenario involving parent tabs,
     * multiple columns, and field access.
     * It verifies that the generated JSON contains all expected properties and values.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void toJSONWithComplexScenarioGeneratesCompleteJSON() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Tab mockParentTab = mock(Tab.class);
        TabAccess mockTabAccess = mock(TabAccess.class);
        Table mockTable = mock(Table.class);
        Column mockColumn = mock(Column.class);
        FieldAccess mockFieldAccess = mock(FieldAccess.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("active='Y'");
        when(mockTab.getDisplayLogic()).thenReturn("@docstatus@='DR'");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TABLE_NAME);
        when(mockTab.getTabLevel()).thenReturn(1L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(mockParentTab);
        when(mockParentTab.getId()).thenReturn(PARENT_TAB_ID);

        List<Column> columns = new ArrayList<>();
        columns.add(mockColumn);
        when(mockTable.getADColumnList()).thenReturn(columns);
        when(mockColumn.isLinkToParentColumn()).thenReturn(true);

        List<FieldAccess> fieldAccessList = new ArrayList<>();
        fieldAccessList.add(mockFieldAccess);
        when(mockTabAccess.getADFieldAccessList()).thenReturn(fieldAccessList);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
             MockedStatic<TabProcessor> mockedTabProcessor = mockStatic(TabProcessor.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     JSONObject tabJson = new JSONObject();
                     tabJson.put("id", TAB_ID);
                     tabJson.put("name", "Test Tab");
                     when(mock.toJsonObject(any(), any())).thenReturn(tabJson);
                 })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(mockKernelUtils);
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTabAccess))
                .thenReturn(new JSONObject().put("fieldAccess1", new JSONObject()));
            mockedTabProcessor.when(() -> TabProcessor.getEntityColumnName(mockColumn))
                .thenReturn(ENTITY_COLUMN_NAME);

            TabBuilder tabBuilder = new TabBuilder(mockTab, mockTabAccess);
            JSONObject result = tabBuilder.toJSON();

            assertNotNull(result);
            
            assertTrue(result.has("filter"));
            assertTrue(result.has("displayLogic"));
            assertTrue(result.has("entityName"));
            assertTrue(result.has("parentColumns"));
            assertTrue(result.has("fields"));
            assertTrue(result.has("parentTabId"));

            assertEquals("active='Y'", result.getString("filter"));
            assertEquals("@docstatus@='DR'", result.getString("displayLogic"));
            assertEquals(TABLE_NAME, result.getString("entityName"));
            assertEquals(PARENT_TAB_ID, result.getString("parentTabId"));

            JSONArray parentColumns = result.getJSONArray("parentColumns");
            assertEquals(1, parentColumns.length());
            assertEquals(ENTITY_COLUMN_NAME, parentColumns.getString(0));

            JSONObject fields = result.getJSONObject("fields");
            assertNotNull(fields);
        }
    }
}
