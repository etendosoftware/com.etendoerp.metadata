package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.COLUMN_ID;
import static com.etendoerp.metadata.MetadataTestConstants.FIELD_ID;
import static com.etendoerp.metadata.MetadataTestConstants.LIST_ID;
import static com.etendoerp.metadata.MetadataTestConstants.REF_LIST;
import static com.etendoerp.metadata.MetadataTestConstants.TABLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_COLUMN_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_FIELD;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

/**
 * Unit tests for FieldBuilderWithColumn class.
 * Tests the functionality specific to fields with database columns.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderWithColumnTest {

  @Mock private Field field;
  @Mock private FieldAccess fieldAccess;
  @Mock private Column column;
  @Mock private Reference reference;
  @Mock private Table table;
  @Mock private Tab tab;
  @Mock private Tab referencedTab;
  @Mock private Window referencedWindow;
  @Mock private Language language;
  @Mock private Process process;
  @Mock private Property referencedProperty;
  @Mock private Property fieldProperty;
  @Mock private Entity referencedEntity;
  @Mock private Entity tabEntity;
  @Mock private OBDal obDal;
  @Mock private OBCriteria<Tab> criteria;
  @Mock private OBContext obContext;

  private FieldBuilderWithColumn fieldBuilder;

  /**
   * Sets up common mock behavior before each test.
   */
  @BeforeEach
  void setUp() {
    setupBasicMocks();
    setupColumnMocks();
    setupAccessMocks();
    setupEntityMocks();
  }

  /**
   * Sets up basic mock behavior for field, column, reference, tab, and table objects.
   * This method configures the fundamental properties needed for most tests.
   */
  private void setupBasicMocks() {
    when(field.getId()).thenReturn(FIELD_ID);
    when(field.getColumn()).thenReturn(column);
    when(field.getTab()).thenReturn(tab);
    when(field.getName()).thenReturn(TEST_FIELD);
    when(field.isReadOnly()).thenReturn(false);
    when(field.getDisplayLogic()).thenReturn(null);

    when(column.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
    when(column.isMandatory()).thenReturn(false);
    when(column.isUpdatable()).thenReturn(true);
    when(column.isLinkToParentColumn()).thenReturn(false);
    when(column.getReference()).thenReturn(reference);
    when(column.getReferenceSearchKey()).thenReturn(reference);
    when(column.getProcess()).thenReturn(null);
    when(column.getOBUIAPPProcess()).thenReturn(null);
    when(column.getReadOnlyLogic()).thenReturn(null);

    when(reference.getId()).thenReturn("reference-id");

    when(tab.getTable()).thenReturn(table);
    when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);
    when(table.getDataOriginType()).thenReturn(ApplicationConstants.TABLEBASEDTABLE);
  }

  /**
   * Sets up column-specific mock behavior.
   * Configures the column's table relationship and database table name.
   */
  private void setupColumnMocks() {
    when(column.getTable()).thenReturn(table);
    lenient().when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);
  }

  /**
   * Sets up field access mock behavior.
   * Configures the field access permissions for testing.
   */
  private void setupAccessMocks() {
    when(fieldAccess.isCheckonsave()).thenReturn(true);
    when(fieldAccess.isEditableField()).thenReturn(true);
  }

  /**
   * Sets up entity-related mock behavior.
   * Configures field property, entity relationships, and entity metadata.
   */
  private void setupEntityMocks() {
    when(fieldProperty.getEntity()).thenReturn(tabEntity);
    when(fieldProperty.getReferencedProperty()).thenReturn(null);
    when(tabEntity.getTableId()).thenReturn(TABLE_ID);
    when(tabEntity.getName()).thenReturn("TestEntity");
  }

  /**
   * Tests the toJSON method when the field has a referenced property.
   * Verifies that the JSON output includes referenced entity information,
   * referenced window ID, and referenced tab ID when a field property
   * has a referenced property.
   * 
   * @throws JSONException if there's an error creating or parsing JSON objects
   */
  @Test
  void testToJSONWithReferencedProperty() throws JSONException {
    when(fieldProperty.getReferencedProperty()).thenReturn(referencedProperty);
    when(referencedProperty.getEntity()).thenReturn(referencedEntity);
    when(referencedEntity.getTableId()).thenReturn(TABLE_ID);
    when(referencedEntity.getName()).thenReturn("ReferencedEntity");
    when(referencedTab.getId()).thenReturn("referenced-tab-id");
    when(referencedTab.getWindow()).thenReturn(referencedWindow);
    when(referencedWindow.getId()).thenReturn("referenced-window-id");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
         MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
               when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(new JSONObject().put("id", COLUMN_ID));
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
      mockedUtils.when(() -> Utils.getReferencedTab(referencedProperty)).thenReturn(referencedTab);
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});

      when(obContext.getLanguage()).thenReturn(language);
      when(obDal.get(Table.class, TABLE_ID)).thenReturn(table);
      when(obDal.createCriteria(Tab.class)).thenReturn(criteria);
      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.setMaxResults(1)).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(referencedTab);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
      JSONObject result = fieldBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("referencedEntity"));
      assertTrue(result.has("referencedWindowId"));
      assertTrue(result.has("referencedTabId"));
      assertEquals("ReferencedEntity", result.getString("referencedEntity"));
      assertEquals("referenced-window-id", result.getString("referencedWindowId"));
      assertEquals("referenced-tab-id", result.getString("referencedTabId"));
    }
  }

  /**
   * Tests the toJSON method when the field's column has an associated process.
   * Verifies that the JSON output includes process action information
   * when a column has a process defined.
   * 
   * @throws JSONException if there's an error creating or parsing JSON objects
   */
  @Test
  void testToJSONWithProcessAction() throws JSONException {
    when(column.getProcess()).thenReturn(process);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<ProcessActionBuilder> mockedProcessBuilder = mockStatic(ProcessActionBuilder.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
               when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(new JSONObject().put("id", COLUMN_ID));
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedProcessBuilder.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
          .thenReturn(new JSONObject().put("processId", "process-id"));
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});

      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
      JSONObject result = fieldBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("processAction"));
    }
  }

  /**
   * Tests the toJSON method when the field has a legacy process.
   * Verifies that the JSON output includes process action information
   * when a field is identified as having a legacy process through LegacyUtils.
   * 
   * @throws JSONException if there's an error creating or parsing JSON objects
   */
  @Test
  void testToJSONWithLegacyProcess() throws JSONException {
    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<LegacyUtils> mockedLegacyUtils = mockStatic(LegacyUtils.class);
         MockedStatic<ProcessActionBuilder> mockedProcessBuilder = mockStatic(ProcessActionBuilder.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
               when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(new JSONObject().put("id", COLUMN_ID));
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedLegacyUtils.when(() -> LegacyUtils.isLegacyProcess(FIELD_ID)).thenReturn(true);
      mockedLegacyUtils.when(() -> LegacyUtils.getLegacyProcess(FIELD_ID)).thenReturn(process);
      mockedProcessBuilder.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
          .thenReturn(new JSONObject().put("processId", "legacy-process-id"));
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});

      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
      JSONObject result = fieldBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("processAction"));
    }
  }

  /**
   * Tests the toJSON method when the field's column has read-only logic.
   * Verifies that the JSON output includes the parsed read-only logic expression
   * when a column has read-only logic defined. The logic is parsed from
   * Openbravo format to JavaScript expression format.
   * 
   * @throws JSONException if there's an error creating or parsing JSON objects
   */
  @Test
  void testToJSONWithReadOnlyLogic() throws JSONException {
    when(column.getReadOnlyLogic()).thenReturn("@test_field@='Y'");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
               when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(new JSONObject().put("id", COLUMN_ID));
             });
         MockedConstruction<DynamicExpressionParser> ignored2 = mockConstruction(DynamicExpressionParser.class,
             (mock, context) -> when(mock.getJSExpression()).thenReturn("test_field == 'Y'"))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});
      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
      JSONObject result = fieldBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has("readOnlyLogicExpression"));
      assertEquals("test_field == 'Y'", result.getString("readOnlyLogicExpression"));
    }
  }

  /**
   * Tests the toJSON method when the field's reference is a list reference.
   * Verifies that the JSON output includes reference list information
   * when a field's column reference is of type LIST_REFERENCE_ID.
   * The test ensures that list items are properly converted to JSON format
   * with id, value, and label properties.
   * 
   * @throws JSONException if there's an error creating or parsing JSON objects
   */
  @Test
  void testToJSONWithReferenceList() throws JSONException {
    when(reference.getId()).thenReturn(Constants.LIST_REFERENCE_ID);

    List<org.openbravo.model.ad.domain.List> adListList = new ArrayList<>();
    org.openbravo.model.ad.domain.List listItem = mock(org.openbravo.model.ad.domain.List.class);
    when(listItem.getId()).thenReturn(LIST_ID);
    when(listItem.getSearchKey()).thenReturn("list-key");
    when(listItem.get(anyString(), any(Language.class), anyString())).thenReturn("List Label");
    adListList.add(listItem);
    when(reference.getADListList()).thenReturn(adListList);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored1 = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
               when(mock.toJsonObject(any(Column.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(new JSONObject().put("id", COLUMN_ID));
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});

      JSONArray listInfoArray = new JSONArray();
      JSONObject listInfo = new JSONObject();
      listInfo.put("id", LIST_ID);
      listInfo.put("value", "list-key");
      listInfo.put("label", "List Label");
      listInfoArray.put(listInfo);

      mockedFieldBuilder.when(() -> FieldBuilder.getListInfo(reference, language))
          .thenReturn(listInfoArray);

      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
      JSONObject result = fieldBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(REF_LIST));
      JSONArray refList = result.getJSONArray(REF_LIST);
      assertEquals(1, refList.length());
      assertEquals(LIST_ID, refList.getJSONObject(0).getString("id"));
    }
  }
  /**
   * Tests the getColumnUpdatable method.
   * Verifies that the updatable status of the column is correctly returned.
   */
  @Test
  void testGetColumnUpdatable() {
    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
          .thenReturn(new String[]{TEST_FIELD});
      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

      when(column.isUpdatable()).thenReturn(true);
      assertTrue(fieldBuilder.getColumnUpdatable());

      when(column.isUpdatable()).thenReturn(false);
      assertFalse(fieldBuilder.getColumnUpdatable());
    }
  }

  /**
   * Tests the toJSON method when the field's column is null.
   * Verifies that the method handles null columns gracefully.
   */
  @Test
  void testToJSONWithNullColumn() {
    when(field.getColumn()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
         MockedStatic<DataSourceUtils> ignored1 = mockStatic(DataSourceUtils.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject baseJson = new JSONObject();
               baseJson.put("id", FIELD_ID);
               when(mock.toJsonObject(any(Field.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                   .thenReturn(baseJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
      mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
      when(obContext.getLanguage()).thenReturn(language);

      fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

      assertTrue(fieldBuilder.getColumnUpdatable());
    }
  }
}
