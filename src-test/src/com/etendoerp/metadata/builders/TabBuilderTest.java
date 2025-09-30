package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.codehaus.jettison.json.JSONException;
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

    // Column IDs and DB names
    private static final String CREATED_ID = "Created";
    private static final String CREATED_BY_ID = "CreatedBy";
    private static final String UPDATED_ID = "Updated";
    private static final String UPDATED_BY_ID = "UpdatedBy";

    // Column display names
    private static final String CREATION_DATE_NAME = "Creation Date";
    private static final String CREATED_BY_NAME = "Created By";
    private static final String UPDATED_NAME = "Updated";
    private static final String UPDATED_BY_NAME = "Updated By";

    // Field names (HQL property names)
    private static final String CREATION_DATE_FIELD = "creationDate";
    private static final String CREATED_BY_FIELD = "createdBy";
    private static final String UPDATED_FIELD = "updated";
    private static final String UPDATED_BY_FIELD = "updatedBy";

    // JSON keys
    private static final String FIELDS_KEY = "fields";
    private static final String SHOW_IN_GRID_VIEW_KEY = "showInGridView";
    private static final String REFERENCED_ENTITY_KEY = "referencedEntity";
    private static final String GRID_POSITION_KEY = "gridPosition";
    private static final String IS_READ_ONLY_KEY = "isReadOnly";
    private static final String IS_EDITABLE_KEY = "isEditable";
    private static final String IS_UPDATABLE_KEY = "isUpdatable";
    private static final String SELECTOR_KEY = "selector";
    private static final String NAME_KEY = "name";
    private static final String JSON_EXCEPTION = "JSON exception";

    // Test data
    private static final String TEST_TABLE_NAME = "TestTable";
    private static final String TEST_DESCRIPTION = "Test description";
    private static final String TEST_HELP = "Test help";
    private static final String AD_USER_ENTITY = "ADUser";
    private static final String CUSTOM_CREATION_DATE_NAME = "Custom Creation Date";

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

        Column createdColumn = createMockColumn(CREATED_ID, CREATED_ID, CREATION_DATE_NAME);
        Column createdByColumn = createMockColumn(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME);
        Column updatedColumn = createMockColumn(UPDATED_ID, UPDATED_ID, UPDATED_NAME);
        Column updatedByColumn = createMockColumn(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME);

        List<Column> columns = new ArrayList<>();
        columns.add(createdColumn);
        columns.add(createdByColumn);
        columns.add(updatedColumn);
        columns.add(updatedByColumn);

        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);
        when(mockTable.getADColumnList()).thenReturn(columns);
        when(mockTab.getTabLevel()).thenReturn(0L);
        when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);

        executeTabBuilderTest(mockContext, mockKernelUtils, mockTab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);
                assertTrue(fields.has(CREATION_DATE_FIELD), "Should have creationDate field");
                assertTrue(fields.has(CREATED_BY_FIELD), "Should have createdBy field");
                assertTrue(fields.has(UPDATED_FIELD), "Should have updated field");
                assertTrue(fields.has(UPDATED_BY_FIELD), "Should have updatedBy field");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that only creationDate and updated are visible in grid by default
     */
    @Test
    void auditFieldsHaveCorrectDefaultVisibility() throws Exception {
        TestContext ctx = setupTestContext();

        Column createdColumn = createMockColumn(CREATED_ID, CREATED_ID, CREATION_DATE_NAME);
        Column createdByColumn = createMockColumn(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME);
        Column updatedColumn = createMockColumn(UPDATED_ID, UPDATED_ID, UPDATED_NAME);
        Column updatedByColumn = createMockColumn(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME);

        List<Column> columns = List.of(createdColumn, createdByColumn, updatedColumn, updatedByColumn);
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                assertTrue(fields.getJSONObject(CREATION_DATE_FIELD).getBoolean(SHOW_IN_GRID_VIEW_KEY),
                        "creationDate should be visible in grid");
                assertTrue(fields.getJSONObject(UPDATED_FIELD).getBoolean(SHOW_IN_GRID_VIEW_KEY),
                        "updated should be visible in grid");
                assertFalse(fields.getJSONObject(CREATED_BY_FIELD).getBoolean(SHOW_IN_GRID_VIEW_KEY),
                        "createdBy should NOT be visible in grid");
                assertFalse(fields.getJSONObject(UPDATED_BY_FIELD).getBoolean(SHOW_IN_GRID_VIEW_KEY),
                        "updatedBy should NOT be visible in grid");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that audit fields are always read-only
     */
    @Test
    void auditFieldsAreAlwaysReadOnly() throws Exception {
        TestContext ctx = setupTestContext();
        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                for (String fieldName : new String[]{CREATION_DATE_FIELD, CREATED_BY_FIELD, UPDATED_FIELD, UPDATED_BY_FIELD}) {
                    JSONObject field = fields.getJSONObject(fieldName);
                    assertTrue(field.getBoolean(IS_READ_ONLY_KEY), fieldName + " should be read-only");
                    assertFalse(field.getBoolean(IS_EDITABLE_KEY), fieldName + " should not be editable");
                    assertFalse(field.getBoolean(IS_UPDATABLE_KEY), fieldName + " should not be updatable");
                }
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that user reference fields (createdBy, updatedBy) have correct metadata
     */
    @Test
    void userReferenceFieldsHaveCorrectMetadata() throws Exception {
        TestContext ctx = setupTestContext();
        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                JSONObject createdBy = fields.getJSONObject(CREATED_BY_FIELD);
                assertTrue(createdBy.has(REFERENCED_ENTITY_KEY), "createdBy should have referencedEntity");
                assertEquals(AD_USER_ENTITY, createdBy.getString(REFERENCED_ENTITY_KEY));
                assertTrue(createdBy.has(SELECTOR_KEY), "createdBy should have selector");

                JSONObject updatedBy = fields.getJSONObject(UPDATED_BY_FIELD);
                assertTrue(updatedBy.has(REFERENCED_ENTITY_KEY), "updatedBy should have referencedEntity");
                assertEquals(AD_USER_ENTITY, updatedBy.getString(REFERENCED_ENTITY_KEY));
                assertTrue(updatedBy.has(SELECTOR_KEY), "updatedBy should have selector");

                assertFalse(fields.getJSONObject(CREATION_DATE_FIELD).has(REFERENCED_ENTITY_KEY),
                        "creationDate should not have referencedEntity");
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that existing audit fields in the tab are not overwritten
     */
    @Test
    void existingAuditFieldsAreNotOverwritten() throws Exception {
        TestContext ctx = setupTestContext();
        List<Column> columns = createAllAuditColumnsLenient();
        setupBasicMocksLenient(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        JSONObject existingFields = new JSONObject();
        JSONObject customCreationDate = new JSONObject();
        customCreationDate.put(NAME_KEY, CUSTOM_CREATION_DATE_NAME);
        customCreationDate.put(SHOW_IN_GRID_VIEW_KEY, false);
        existingFields.put(CREATION_DATE_FIELD, customCreationDate);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, existingFields, result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                assertEquals(CUSTOM_CREATION_DATE_NAME, fields.getJSONObject(CREATION_DATE_FIELD).getString(NAME_KEY));
                assertFalse(fields.getJSONObject(CREATION_DATE_FIELD).getBoolean(SHOW_IN_GRID_VIEW_KEY));

                assertTrue(fields.has(CREATED_BY_FIELD));
                assertTrue(fields.has(UPDATED_FIELD));
                assertTrue(fields.has(UPDATED_BY_FIELD));
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that missing audit columns are handled gracefully
     */
    @Test
    void missingAuditColumnsAreSkippedGracefully() throws Exception {
        TestContext ctx = setupTestContext();
        List<Column> columns = List.of(createMockColumn(CREATED_ID, CREATED_ID, CREATION_DATE_NAME));
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                assertTrue(fields.has(CREATION_DATE_FIELD));
                assertFalse(fields.has(CREATED_BY_FIELD));
                assertFalse(fields.has(UPDATED_FIELD));
                assertFalse(fields.has(UPDATED_BY_FIELD));
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    /**
     * Tests that audit fields have correct grid positions
     */
    @Test
    void auditFieldsHaveCorrectGridPositions() throws Exception {
        TestContext ctx = setupTestContext();
        List<Column> columns = createAllAuditColumns();
        setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, columns);

        executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
            try {
                JSONObject fields = result.getJSONObject(FIELDS_KEY);

                assertEquals(9000, fields.getJSONObject(CREATION_DATE_FIELD).getInt(GRID_POSITION_KEY));
                assertEquals(9001, fields.getJSONObject(CREATED_BY_FIELD).getInt(GRID_POSITION_KEY));
                assertEquals(9002, fields.getJSONObject(UPDATED_FIELD).getInt(GRID_POSITION_KEY));
                assertEquals(9003, fields.getJSONObject(UPDATED_BY_FIELD).getInt(GRID_POSITION_KEY));
            } catch (JSONException e) {
                fail(JSON_EXCEPTION + ": " + e.getMessage());
            }
        });
    }

    // Helper methods

    /**
     * Executes a TabBuilder test with common mock setup
     */
    private void executeTabBuilderTest(OBContext mockContext, KernelUtils mockKernelUtils,
                                       Tab mockTab, JSONObject tabFields,
                                       Consumer<JSONObject> assertions) {
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
            mockedTabProcessor.when(() -> TabProcessor.getTabFields(mockTab)).thenReturn(tabFields);

            TabBuilder tabBuilder = new TabBuilder(mockTab, null);
            JSONObject result = tabBuilder.toJSON();

            assertions.accept(result);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    private TestContext setupTestContext() {
        return new TestContext(
                mock(OBContext.class),
                mock(Language.class),
                mock(Tab.class),
                mock(Table.class),
                mock(KernelUtils.class)
        );
    }

    private Column createMockColumn(String id, String dbName, String name) {
        Column column = mock(Column.class);
        when(column.getId()).thenReturn(id);
        when(column.getDBColumnName()).thenReturn(dbName);
        when(column.getName()).thenReturn(name);
        when(column.getDescription()).thenReturn(TEST_DESCRIPTION);
        when(column.getHelpComment()).thenReturn(TEST_HELP);
        when(column.isMandatory()).thenReturn(true);
        when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    private Column createMockColumnLenient(String id, String dbName, String name) {
        Column column = mock(Column.class);
        lenient().when(column.getId()).thenReturn(id);
        lenient().when(column.getDBColumnName()).thenReturn(dbName);
        lenient().when(column.getName()).thenReturn(name);
        lenient().when(column.getDescription()).thenReturn(TEST_DESCRIPTION);
        lenient().when(column.getHelpComment()).thenReturn(TEST_HELP);
        lenient().when(column.isMandatory()).thenReturn(true);
        lenient().when(column.getIdentifier()).thenReturn(name);
        return column;
    }

    private List<Column> createAllAuditColumns() {
        return List.of(
                createMockColumn(CREATED_ID, CREATED_ID, CREATION_DATE_NAME),
                createMockColumn(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME),
                createMockColumn(UPDATED_ID, UPDATED_ID, UPDATED_NAME),
                createMockColumn(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME)
        );
    }

    private List<Column> createAllAuditColumnsLenient() {
        return List.of(
                createMockColumnLenient(CREATED_ID, CREATED_ID, CREATION_DATE_NAME),
                createMockColumnLenient(CREATED_BY_ID, CREATED_BY_ID, CREATED_BY_NAME),
                createMockColumnLenient(UPDATED_ID, UPDATED_ID, UPDATED_NAME),
                createMockColumnLenient(UPDATED_BY_ID, UPDATED_BY_ID, UPDATED_BY_NAME)
        );
    }

    private void setupBasicMocks(OBContext mockContext, Language mockLanguage, Tab mockTab,
                                 Table mockTable, KernelUtils mockKernelUtils, List<Column> columns) {
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockTab.getFilterClause()).thenReturn("");
        when(mockTab.getDisplayLogic()).thenReturn("");
        when(mockTab.getTable()).thenReturn(mockTable);
        when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);
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
        lenient().when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);
        lenient().when(mockTable.getADColumnList()).thenReturn(columns);
        lenient().when(mockTab.getTabLevel()).thenReturn(0L);
        lenient().when(mockKernelUtils.getParentTab(mockTab)).thenReturn(null);
    }

    /**
     * Context holder for test mocks
     */
    private static class TestContext {
        final OBContext context;
        final Language language;
        final Tab tab;
        final Table table;
        final KernelUtils kernelUtils;

        TestContext(OBContext context, Language language, Tab tab, Table table, KernelUtils kernelUtils) {
            this.context = context;
            this.language = language;
            this.tab = tab;
            this.table = table;
            this.kernelUtils = kernelUtils;
        }
    }
}