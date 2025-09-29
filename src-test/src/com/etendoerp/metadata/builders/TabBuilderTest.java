package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.data.TabProcessor;

/**
 * Unit tests for audit fields functionality in TabBuilder
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderAuditFieldsTest {

    /**
     * Tests that audit fields are automatically added to the fields JSON
     * when they are not already present.
     */
    @Test
    void toJSONAddsAuditFieldsWhenNotPresent() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        Column createdColumn = createMockColumn("Created", "Created", "Creation Date");
        Column createdByColumn = createMockColumn("CreatedBy", "CreatedBy", "Created By");
        Column updatedColumn = createMockColumn("Updated", "Updated", "Updated");
        Column updatedByColumn = createMockColumn("UpdatedBy", "UpdatedBy", "Updated By");

        List<Column> columns = new ArrayList<>();
        columns.add(createdColumn);
        columns.add(createdByColumn);
        columns.add(updatedColumn);
        columns.add(updatedByColumn);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn("TestTable");
        when(mockTable.getADColumnList()).thenReturn(columns);
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
                    .thenReturn(new JSONObject());

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            JSONObject fields = result.getJSONObject("fields");
            assertTrue(fields.has("creationDate"), "Should have creationDate field");
            assertTrue(fields.has("createdBy"), "Should have createdBy field");
            assertTrue(fields.has("updated"), "Should have updated field");
            assertTrue(fields.has("updatedBy"), "Should have updatedBy field");
        }
    }

    /**
     * Tests that only creationDate and updated are visible in grid by default
     */
    @Test
    void auditFieldsHaveCorrectDefaultVisibility() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        Column createdColumn = createMockColumn("Created", "Created", "Creation Date");
        Column createdByColumn = createMockColumn("CreatedBy", "CreatedBy", "Created By");
        Column updatedColumn = createMockColumn("Updated", "Updated", "Updated");
        Column updatedByColumn = createMockColumn("UpdatedBy", "UpdatedBy", "Updated By");

        List<Column> columns = List.of(createdColumn, createdByColumn, updatedColumn, updatedByColumn);

        setupBasicMocks(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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

            JSONObject fields = result.getJSONObject("fields");

            assertTrue(fields.getJSONObject("creationDate").getBoolean("showInGridView"),
                    "creationDate should be visible in grid");
            assertTrue(fields.getJSONObject("updated").getBoolean("showInGridView"),
                    "updated should be visible in grid");
            assertFalse(fields.getJSONObject("createdBy").getBoolean("showInGridView"),
                    "createdBy should NOT be visible in grid");
            assertFalse(fields.getJSONObject("updatedBy").getBoolean("showInGridView"),
                    "updatedBy should NOT be visible in grid");
        }
    }

    /**
     * Tests that audit fields are always read-only
     */
    @Test
    void auditFieldsAreAlwaysReadOnly() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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
            JSONObject fields = result.getJSONObject("fields");

            for (String fieldName : new String[]{"creationDate", "createdBy", "updated", "updatedBy"}) {
                JSONObject field = fields.getJSONObject(fieldName);
                assertTrue(field.getBoolean("isReadOnly"), fieldName + " should be read-only");
                assertFalse(field.getBoolean("isEditable"), fieldName + " should not be editable");
                assertFalse(field.getBoolean("isUpdatable"), fieldName + " should not be updatable");
            }
        }
    }

    /**
     * Tests that user reference fields (createdBy, updatedBy) have correct metadata
     */
    @Test
    void userReferenceFieldsHaveCorrectMetadata() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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
            JSONObject fields = result.getJSONObject("fields");

            // Check createdBy
            JSONObject createdBy = fields.getJSONObject("createdBy");
            assertTrue(createdBy.has("referencedEntity"), "createdBy should have referencedEntity");
            assertEquals("ADUser", createdBy.getString("referencedEntity"));
            assertTrue(createdBy.has("selector"), "createdBy should have selector");

            // Check updatedBy
            JSONObject updatedBy = fields.getJSONObject("updatedBy");
            assertTrue(updatedBy.has("referencedEntity"), "updatedBy should have referencedEntity");
            assertEquals("ADUser", updatedBy.getString("referencedEntity"));
            assertTrue(updatedBy.has("selector"), "updatedBy should have selector");

            // Date fields should not have referencedEntity
            assertFalse(fields.getJSONObject("creationDate").has("referencedEntity"),
                    "creationDate should not have referencedEntity");
        }
    }

    /**
     * Tests that existing audit fields in the tab are not overwritten
     */
    @Test
    void existingAuditFieldsAreNotOverwritten() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        // Use lenient stubs since these columns won't be accessed in this test path
        List<Column> columns = createAllAuditColumnsLenient();
        setupBasicMocksLenient(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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

            // Return existing creationDate field
            JSONObject existingFields = new JSONObject();
            JSONObject customCreationDate = new JSONObject();
            customCreationDate.put("name", "Custom Creation Date");
            customCreationDate.put("showInGridView", false);
            existingFields.put("creationDate", customCreationDate);

            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab))
                    .thenReturn(existingFields);

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();
            JSONObject fields = result.getJSONObject("fields");

            // Existing field should not be overwritten
            assertEquals("Custom Creation Date", fields.getJSONObject("creationDate").getString("name"));
            assertFalse(fields.getJSONObject("creationDate").getBoolean("showInGridView"));

            // Other audit fields should still be added
            assertTrue(fields.has("createdBy"));
            assertTrue(fields.has("updated"));
            assertTrue(fields.has("updatedBy"));
        }
    }

    /**
     * Tests that missing audit columns are handled gracefully
     */
    @Test
    void missingAuditColumnsAreSkippedGracefully() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        // Only include creationDate column
        List<Column> columns = List.of(createMockColumn("Created", "Created", "Creation Date"));
        setupBasicMocks(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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
            JSONObject fields = result.getJSONObject("fields");

            // Only creationDate should be present
            assertTrue(fields.has("creationDate"));
            assertFalse(fields.has("createdBy"));
            assertFalse(fields.has("updated"));
            assertFalse(fields.has("updatedBy"));
        }
    }

    /**
     * Tests that audit fields have correct grid positions
     */
    @Test
    void auditFieldsHaveCorrectGridPositions() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        Language mockLanguage = mock(Language.class);
        Tab mockTab = mock(Tab.class);
        Table mockTable = mock(Table.class);
        KernelUtils mockKernelUtils = mock(KernelUtils.class);

        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(mockContext, mockLanguage, mockTab, mockTable, mockKernelUtils, columns);

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
            JSONObject fields = result.getJSONObject("fields");

            assertEquals(9000, fields.getJSONObject("creationDate").getInt("gridPosition"));
            assertEquals(9001, fields.getJSONObject("createdBy").getInt("gridPosition"));
            assertEquals(9002, fields.getJSONObject("updated").getInt("gridPosition"));
            assertEquals(9003, fields.getJSONObject("updatedBy").getInt("gridPosition"));
        }
    }

    // Helper methods
    private Column createMockColumn(String id, String dbName, String name) {
        Column column = mock(Column.class);
        when(column.getId()).thenReturn(id);
        when(column.getDBColumnName()).thenReturn(dbName);
        when(column.getName()).thenReturn(name);
        when(column.getDescription()).thenReturn("Test description");
        when(column.getHelpComment()).thenReturn("Test help");
        when(column.isMandatory()).thenReturn(true);
        when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    private Column createMockColumnLenient(String id, String dbName, String name) {
        Column column = mock(Column.class);
        lenient().when(column.getId()).thenReturn(id);
        lenient().when(column.getDBColumnName()).thenReturn(dbName);
        lenient().when(column.getName()).thenReturn(name);
        lenient().when(column.getDescription()).thenReturn("Test description");
        lenient().when(column.getHelpComment()).thenReturn("Test help");
        lenient().when(column.isMandatory()).thenReturn(true);
        lenient().when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    private List<Column> createAllAuditColumns() {
        return List.of(
                createMockColumn("Created", "Created", "Creation Date"),
                createMockColumn("CreatedBy", "CreatedBy", "Created By"),
                createMockColumn("Updated", "Updated", "Updated"),
                createMockColumn("UpdatedBy", "UpdatedBy", "Updated By")
        );
    }

    private List<Column> createAllAuditColumnsLenient() {
        return List.of(
                createMockColumnLenient("Created", "Created", "Creation Date"),
                createMockColumnLenient("CreatedBy", "CreatedBy", "Created By"),
                createMockColumnLenient("Updated", "Updated", "Updated"),
                createMockColumnLenient("UpdatedBy", "UpdatedBy", "Updated By")
        );
    }

    private void setupBasicMocks(OBContext mockContext, Language mockLanguage, Tab mockTab,
                                 Table mockTable, KernelUtils mockKernelUtils, List<Column> columns) {
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn("TestTable");
        when(mockTable.getADColumnList()).thenReturn(columns);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
    }

    private void setupBasicMocksLenient(OBContext mockContext, Language mockLanguage, Tab mockTab,
                                        Table mockTable, KernelUtils mockKernelUtils, List<Column> columns) {
        lenient().when(mockContext.getLanguage()).thenReturn(mockLanguage);
        lenient().when(mockTab.getFilterClause()).thenReturn("");
        lenient().when(mockTab.getDisplayLogic()).thenReturn("");
        lenient().when(mockTab.getTable()).thenReturn(mockTable);
        lenient().when(mockTable.getName()).thenReturn("TestTable");
        lenient().when(mockTable.getADColumnList()).thenReturn(columns);
        lenient().when(mockTab.getTabLevel()).thenReturn(0L);
        lenient().when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
    }
}