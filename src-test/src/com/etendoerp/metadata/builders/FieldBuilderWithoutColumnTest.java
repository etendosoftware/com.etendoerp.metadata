package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.codehaus.jettison.json.JSONException;
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
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.etendorx.utils.DataSourceUtils;

/**
 * Test class for FieldBuilderWithoutColumn.
 * Tests the behavior of fields that don't have associated database columns,
 * such as canvas fields or other UI-only field types.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderWithoutColumnTest {

    @Mock
    private Field field;

    @Mock
    private FieldAccess fieldAccess;

    @Mock
    private Column column;

    @Mock
    private Reference reference;

    @Mock
    private Table table;

    @Mock
    private Tab tab;

    @Mock
    private Language language;

    @Mock
    private OBContext obContext;

    private static final String DISPLAY_LOGIN_EXPRESSION_STRING = "displayLogicExpression";

    @BeforeEach
    void setUp() {
        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);
        when(field.getName()).thenReturn(TEST_FIELD);
        when(field.isReadOnly()).thenReturn(false);

        when(column.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
        when(column.getTable()).thenReturn(table);

        when(tab.getTable()).thenReturn(table);
        when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);
    }

    /**
     * Helper method to execute toJSON with required static mocks.
     */
    private JSONObject executeToJSON(Runnable extraMocks) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            if (extraMocks != null) {
                extraMocks.run();
            }

            FieldBuilderWithoutColumn fieldBuilder = new FieldBuilderWithoutColumn(field, fieldAccess);
            return fieldBuilder.toJSON();
        }
    }

    /**
     * Tests the constructor with a valid field and field access.
     * Verifies that the builder is properly initialized.
     */
    @Test
    void testConstructorWithFieldAndAccess() throws JSONException {
        JSONObject result = executeToJSON(null);

        assertNotNull(result, "toJSON should return a non-null JSONObject");
        assertTrue(result.has("id"), "Result should contain field id");
    }

    /**
     * Tests the constructor with null field access.
     * Verifies that the builder handles null field access gracefully.
     */
    @Test
    void testConstructorWithNullFieldAccess() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            FieldBuilderWithoutColumn builder = new FieldBuilderWithoutColumn(field, null);
            JSONObject result = builder.toJSON();

            assertNotNull(result, "toJSON should work even with null fieldAccess");
        }
    }

    /**
     * Tests that toJSON returns the correct structure for fields without columns.
     * Verifies the presence of expected properties in the result.
     */
    @Test
    void testToJSONContainsExpectedProperties() throws JSONException {
        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("id"), "Result should contain id");
    }

    /**
     * Tests that the HQL name is properly added to the JSON output.
     */
    @Test
    void testToJSONAddsHqlName() throws JSONException {
        JSONObject result = executeToJSON(null);

        assertTrue(result.has("hqlName"), "Result should contain hqlName property");
    }

    /**
     * Tests access properties when field access allows editing.
     * Verifies that editability settings are correctly applied.
     */
    @Test
    void testToJSONWithEditableFieldAccess() throws JSONException {
        when(fieldAccess.isEditableField()).thenReturn(true);
        when(fieldAccess.isCheckonsave()).thenReturn(true);

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("isEditable"), "Result should contain isEditable property");
        assertTrue(result.getBoolean("isEditable"), "Field should be editable");
    }

    /**
     * Tests access properties when field access is read-only.
     * Verifies that read-only settings are correctly applied.
     */
    @Test
    void testToJSONWithReadOnlyFieldAccess() throws JSONException {
        when(fieldAccess.isEditableField()).thenReturn(false);
        when(field.isReadOnly()).thenReturn(true);

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("isReadOnly"), "Result should contain isReadOnly property");
        assertTrue(result.getBoolean("isReadOnly"), "Field should be read-only");
    }

    /**
     * Tests that display logic is added when configured on the field.
     */
    @Test
    void testToJSONWithDisplayLogic() throws JSONException {
        when(field.getDisplayLogic()).thenReturn("@Test@='Y'");

        try (MockedConstruction<DynamicExpressionParser> ignored = mockConstruction(DynamicExpressionParser.class,
                (mock, context) -> when(mock.getJSExpression()).thenReturn("context.Test=='Y'"))) {

            JSONObject result = executeToJSON(null);

            assertNotNull(result);
            assertTrue(result.has(DISPLAY_LOGIN_EXPRESSION_STRING),
                    "Result should contain displayLogicExpression when display logic is set");
        }
    }

    /**
     * Tests that display logic is not added when not configured.
     */
    @Test
    void testToJSONWithoutDisplayLogic() throws JSONException {
        when(field.getDisplayLogic()).thenReturn(null);

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertFalse(result.has(DISPLAY_LOGIN_EXPRESSION_STRING),
                "Result should not contain displayLogicExpression when not configured");
    }

    /**
     * Tests that display logic with blank string is not processed.
     */
    @Test
    void testToJSONWithBlankDisplayLogic() throws JSONException {
        when(field.getDisplayLogic()).thenReturn("   ");

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertFalse(result.has(DISPLAY_LOGIN_EXPRESSION_STRING),
                "Result should not contain displayLogicExpression for blank display logic");
    }

    /**
     * Tests that getColumnUpdatable returns true for fields without columns.
     * Fields without columns should always be considered updatable by default.
     */
    @Test
    void testGetColumnUpdatableReturnsTrue() throws JSONException {
        JSONObject result = executeToJSON(null);

        assertTrue(result.has("isUpdatable"), "Result should contain isUpdatable");
        assertTrue(result.getBoolean("isUpdatable"),
                "Fields without columns should be updatable by default");
    }

    /**
     * Tests checkOnSave property with field access.
     */
    @Test
    void testToJSONWithCheckOnSave() throws JSONException {
        when(fieldAccess.isCheckonsave()).thenReturn(true);

        JSONObject result = executeToJSON(null);

        assertTrue(result.has("checkOnSave"), "Result should contain checkOnSave");
        assertTrue(result.getBoolean("checkOnSave"), "checkOnSave should be true");
    }

    /**
     * Tests that the builder properly inherits from FieldBuilder.
     * Verifies that FieldBuilderWithoutColumn is an instance of FieldBuilder.
     */
    @Test
    void testInheritanceFromFieldBuilder() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) ->
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", FIELD_ID))
                        )) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            FieldBuilderWithoutColumn builder = new FieldBuilderWithoutColumn(field, fieldAccess);

            assertTrue(builder instanceof FieldBuilder,
                    "FieldBuilderWithoutColumn should extend FieldBuilder");
            assertTrue(builder instanceof Builder,
                    "FieldBuilderWithoutColumn should ultimately extend Builder");
        }
    }
}
