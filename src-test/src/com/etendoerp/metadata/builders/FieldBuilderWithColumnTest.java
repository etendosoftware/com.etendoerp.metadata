package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.ApplicationConstants;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.LegacyUtils;
import com.etendoerp.metadata.utils.Utils;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderWithColumnTest {

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
    private Tab referencedTab;
    @Mock
    private Window referencedWindow;
    @Mock
    private Language language;
    @Mock
    private Process process;
    @Mock
    private Property referencedProperty;
    @Mock
    private Property fieldProperty;
    @Mock
    private Entity referencedEntity;
    @Mock
    private Entity tabEntity;
    @Mock
    private OBDal obDal;
    @Mock
    private OBCriteria<Tab> criteria;
    @Mock
    private OBContext obContext;
    private static final String BUTTON_REF_LIST_STRING = "buttonRefList";

    private FieldBuilderWithColumn fieldBuilder;

    @BeforeEach
    void setUp() {
        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);
        when(field.getName()).thenReturn(TEST_FIELD);
        when(field.isReadOnly()).thenReturn(false);

        when(column.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
        when(column.getTable()).thenReturn(table);
        when(column.getReference()).thenReturn(reference);
        when(column.getReferenceSearchKey()).thenReturn(reference);
        when(column.isUpdatable()).thenReturn(true);
        when(column.isMandatory()).thenReturn(false);

        when(reference.getId()).thenReturn("reference-id");

        when(tab.getTable()).thenReturn(table);
        when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);
        when(table.getDataOriginType()).thenReturn(ApplicationConstants.TABLEBASEDTABLE);

        when(fieldProperty.getEntity()).thenReturn(tabEntity);
        when(tabEntity.getTableId()).thenReturn(TABLE_ID);
    }

    /* ---------------------------------------------------------------------- */
    /* Helpers */
    /* ---------------------------------------------------------------------- */

    private JSONObject executeToJSON(Runnable extraMocks) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            when(obContext.getLanguage()).thenReturn(language);

            if (extraMocks != null) {
                extraMocks.run();
            }

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            return fieldBuilder.toJSON();
        }
    }

    private JSONObject executeWithExpressionParser(
            String jsExpression,
            Runnable extraMocks) throws JSONException {

        try (MockedConstruction<DynamicExpressionParser> ignored = mockConstruction(DynamicExpressionParser.class,
                (mock, context) -> when(mock.getJSExpression()).thenReturn(jsExpression))) {
            return executeToJSON(extraMocks);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Tests */
    /* ---------------------------------------------------------------------- */

    @Test
    void testToJSONWithProcessAction() throws JSONException {
        when(column.getProcess()).thenReturn(process);

        try (MockedStatic<ProcessActionBuilder> mocked = mockStatic(ProcessActionBuilder.class)) {

            mocked.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
                    .thenReturn(new JSONObject().put("processId", "process-id"));

            JSONObject result = executeToJSON(null);
            assertTrue(result.has("processAction"));
        }
    }

    @Test
    void testToJSONWithLegacyProcess() throws JSONException {
        try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class);
                MockedStatic<ProcessActionBuilder> builder = mockStatic(ProcessActionBuilder.class)) {

            legacy.when(() -> LegacyUtils.isLegacyProcess(FIELD_ID)).thenReturn(true);
            legacy.when(() -> LegacyUtils.getLegacyProcess(FIELD_ID)).thenReturn(process);

            builder.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
                    .thenReturn(new JSONObject().put("processId", "legacy"));

            JSONObject result = executeToJSON(null);
            assertTrue(result.has("processAction"));
        }
    }

    @Test
    void testToJSONWithReadOnlyLogic() throws JSONException {
        when(column.getReadOnlyLogic()).thenReturn("@test@='Y'");

        JSONObject result = executeWithExpressionParser(
                "test == 'Y'",
                null);

        assertEquals("test == 'Y'", result.getString("readOnlyLogicExpression"));
    }

    @Test
    void testToJSONWithComplexDisplayLogic() throws JSONException {
        when(field.getDisplayLogic())
                .thenReturn("@Product@='Y' & @Customer@!='null'");

        JSONObject result = executeWithExpressionParser(
                "context.Product=='Y' && context.Customer!=null",
                null);

        assertNotNull(result);
    }

    @Test
    void testToJSONWithMandatoryColumn() throws JSONException {
        when(column.isMandatory()).thenReturn(true);

        JSONObject result = executeToJSON(null);
        assertNotNull(result);
    }

    @Test
    void testToJSONWithButtonReferenceList() throws JSONException {
        when(column.getReference().getId()).thenReturn(Constants.BUTTON_REFERENCE_ID);

        org.openbravo.model.ad.domain.List item = mock(org.openbravo.model.ad.domain.List.class);

        when(item.getId()).thenReturn(LIST_ID);
        when(item.getSearchKey()).thenReturn("BTN");
        when(item.get(anyString(), any(Language.class), anyString()))
                .thenReturn("Button");

        when(reference.getADListList()).thenReturn(List.of(item));

        JSONObject result = executeToJSON(null);

        assertTrue(result.has(BUTTON_REF_LIST_STRING));
        assertEquals(1, result.getJSONArray(BUTTON_REF_LIST_STRING).length());
    }

    @Test
    void testToJSONWithoutButtonReference() throws JSONException {
        when(column.getReference().getId()).thenReturn("OTHER");

        JSONObject result = executeToJSON(null);
        assertFalse(result.has(BUTTON_REF_LIST_STRING));
    }

    @Test
    void testToJSONWithEmptyReferenceList() throws JSONException {
        when(reference.getId()).thenReturn(Constants.LIST_REFERENCE_ID);
        when(reference.getADListList()).thenReturn(Collections.emptyList());

        JSONObject result = executeToJSON(null);

        assertTrue(result.has(REF_LIST));
        assertEquals(0, result.getJSONArray(REF_LIST).length());
    }

    @Test
    void testIsParentRecordPropertyTrue() throws JSONException {
        Tab parentTab = mock(Tab.class);
        Table parentTable = mock(Table.class);
        Entity parentEntity = mock(Entity.class);

        when(column.isLinkToParentColumn()).thenReturn(true);
        when(parentTab.getTable()).thenReturn(parentTable);
        when(parentTable.getDBTableName()).thenReturn(TEST_TABLE_NAME);
        when(parentTable.getDataOriginType()).thenReturn(ApplicationConstants.TABLEBASEDTABLE);

        when(referencedProperty.getEntity()).thenReturn(parentEntity);
        when(fieldProperty.getReferencedProperty()).thenReturn(referencedProperty);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedStatic<org.openbravo.base.model.ModelProvider> mockedModelProvider = mockStatic(
                        org.openbravo.base.model.ModelProvider.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            KernelUtils kernelUtilsInstance = mock(KernelUtils.class);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(kernelUtilsInstance);
            when(kernelUtilsInstance.getParentTab(tab)).thenReturn(parentTab);
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);

            org.openbravo.base.model.ModelProvider modelProviderInstance = mock(
                    org.openbravo.base.model.ModelProvider.class);
            mockedModelProvider.when(org.openbravo.base.model.ModelProvider::getInstance)
                    .thenReturn(modelProviderInstance);
            when(modelProviderInstance.getEntityByTableName(TEST_TABLE_NAME)).thenReturn(parentEntity);

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            JSONObject result = fieldBuilder.toJSON();

            assertTrue(result.has("isParentRecordProperty"));
            assertTrue(result.getBoolean("isParentRecordProperty"));
        }
    }

    @Test
    void testReferencedPropertyExceptionIsHandled() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedKernelUtils.when(() -> KernelUtils.getProperty(field))
                    .thenThrow(new RuntimeException("Test exception"));

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            JSONObject result = fieldBuilder.toJSON();

            // Should not throw, and should not have referencedEntity
            assertNotNull(result);
            assertFalse(result.has("referencedEntity"));
        }
    }

    @Test
    void testGetColumnUpdatableReturnsTrueWhenColumnIsNull() throws JSONException {
        when(field.getColumn()).thenReturn(null);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);

            mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(anyBoolean(), anyString(), anyString()))
                    .thenReturn(new String[] { TEST_FIELD });

            // FieldBuilderWithColumn cannot be instantiated with null column
            // This test verifies the getColumnUpdatable method returns true when column is
            // null
            // by checking that FieldBuilderWithColumn handles the null case in
            // getColumnUpdatable
            when(field.getColumn()).thenReturn(column); // reset for constructor
            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

            // After construction, simulate null column
            when(field.getColumn()).thenReturn(null);
            // The getColumnUpdatable should return true as fallback
            assertTrue(fieldBuilder.getColumnUpdatable());
        }
    }
}
