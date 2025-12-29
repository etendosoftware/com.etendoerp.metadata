package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.DISPLAY_PROPERTY;
import static com.etendoerp.metadata.MetadataTestConstants.FIELD_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
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
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.model.ad.domain.ReferencedTreeField;
import org.openbravo.model.ad.domain.Validation;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.data.ReferenceSelectors;
import com.etendoerp.metadata.utils.Constants;

/**
 * Test class for FieldBuilder, which contains methods to build and manipulate field-related data.
 * This class uses Mockito for mocking dependencies and JUnit 5 for testing.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderTest {

  @Mock
  private Field field;
  @Mock
  private Column column;
  @Mock
  private Reference reference;
  @Mock
  private Tab tab;
  @Mock
  private Table table;
  @Mock
  private Language language;
  @Mock
  private Process process;
  @Mock
  private org.openbravo.client.application.Process processDefinition;
  @Mock
  private Selector selector;
  @Mock
  private ReferencedTree referencedTree;
  @Mock
  private SelectorField selectorField;
  @Mock
  private ReferencedTreeField referencedTreeField;
  @Mock
  private DatasourceField datasourceField;
  @Mock
  private DataSource dataSource;
  @Mock
  private ModelProvider modelProvider;
  private static final String LIST_ID_STRING = "list-id";
  private static final String REF_ID = "ref-id";
  private static final String COLUMN_NAME = "column_name";
  private static final String VAL_ID = "val-id";
  private static final String PROC_ID = "proc-id";
  private static final String CONTEXT = "context";
  private static final String TEST_FIELD_ID = "test-field-id";
  private static final String RECORD_ID = "record-id";
  private static final String RECORD_NAME = "record-name";
  private static final String DATASOURCE_FIELD = "datasource.field";
  private static final String DATASOURCE_FIELD_DOLLAR = "datasource$field";


    /**
   * Sets up the necessary mocks and their behaviors before each test.
   */
  @BeforeEach
  void setUp() {
    when(field.getColumn()).thenReturn(column);
    when(field.getTab()).thenReturn(tab);
    when(field.getId()).thenReturn(FIELD_ID);
    when(column.getReference()).thenReturn(reference);
    when(reference.getId()).thenReturn("reference-id");
  }

  /**
   * Tests if the field is a reference field based on the reference ID.
   */
  @Test
  void testIsProcessFieldWithProcessAction() {
    when(column.getProcess()).thenReturn(process);
    when(column.getOBUIAPPProcess()).thenReturn(null);

    boolean result = FieldBuilder.isProcessField(field);

    assertTrue(result);
  }

  /**
   * Tests if the field is not a process field when no process is defined.
   */
  @Test
  void testIsProcessFieldWithProcessDefinition() {
    when(column.getProcess()).thenReturn(null);
    when(column.getOBUIAPPProcess()).thenReturn(processDefinition);

    boolean result = FieldBuilder.isProcessField(field);

    assertTrue(result);
  }

  /**
   * Tests if the field is not a process field when both process and process definition are null.
   */
  @Test
  void testIsProcessFieldWithoutProcess() {
    when(column.getProcess()).thenReturn(null);
    when(column.getOBUIAPPProcess()).thenReturn(null);

    boolean result = FieldBuilder.isProcessField(field);

    assertFalse(result);
  }

  /**
   * Tests if the field is a reference field based on the reference ID.
   */
  @Test
  void testGetReferenceSelectorsWithSelector() {
    List<Selector> selectorList = List.of(selector);
    when(reference.getOBUISELSelectorList()).thenReturn(selectorList);
    when(reference.getADReferencedTreeList()).thenReturn(Collections.emptyList());

    ReferenceSelectors result = FieldBuilder.getReferenceSelectors(reference);

    assertEquals(selector, result.selector);
    assertNull(result.treeSelector);
  }

  /**
   * Tests if the method returns null when there are no selectors or trees.
   */
  @Test
  void testGetReferenceSelectorsWithTreeSelector() {
    when(reference.getOBUISELSelectorList()).thenReturn(Collections.emptyList());
    List<ReferencedTree> treeList = List.of(referencedTree);
    when(reference.getADReferencedTreeList()).thenReturn(treeList);

    ReferenceSelectors result = FieldBuilder.getReferenceSelectors(reference);

    assertNull(result.selector);
    assertEquals(referencedTree, result.treeSelector);
  }

  /**
   * Tests if the method returns null when both selectors and trees are empty.
   */
  @Test
  void testGetReferenceSelectorsWithNullReference() {
    ReferenceSelectors result = FieldBuilder.getReferenceSelectors(null);

    assertNull(result.selector);
    assertNull(result.treeSelector);
  }

  /**
   * Tests if the method returns a valid JSON object with selector information.
   * It mocks the necessary methods to return a valid selector.
   *
   * @throws JSONException if there is an error creating the JSON object
   */
  @Test
  void testGetSelectorInfoWithComboTableSelector() throws JSONException {
    ReferenceSelectors mockSelectors = new ReferenceSelectors(null, null);
    ProcessParameter processParameter = mock(ProcessParameter.class);
    Reference ref = mock(Reference.class);
    Validation validation = mock(Validation.class);
    Process processMock = mock(Process.class);

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS);
         MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<DalConnectionProvider> dalConnStatic = mockStatic(DalConnectionProvider.class);
         MockedStatic<RequestContext> requestContextStatic = mockStatic(RequestContext.class);
         MockedStatic<Utility> utilityStatic = mockStatic(Utility.class);
         MockedConstruction<ComboTableData> comboTableDataMockedConstruction = mockConstruction(ComboTableData.class,
             (mock, context) -> {
               when(mock.select(false)).thenReturn(new FieldProvider[0]);
             })) {

      OBDal obDal = mock(OBDal.class);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(ProcessParameter.class, FIELD_ID)).thenReturn(processParameter);

      when(processParameter.getReference()).thenReturn(ref);
      when(ref.getId()).thenReturn(REF_ID);
      when(processParameter.getDBColumnName()).thenReturn(COLUMN_NAME);
      when(processParameter.getValidation()).thenReturn(validation);
      when(validation.getId()).thenReturn(VAL_ID);
      when(processParameter.getProcess()).thenReturn(processMock);
      when(processMock.getId()).thenReturn(PROC_ID);

      dalConnStatic.when(DalConnectionProvider::getReadOnlyConnectionProvider).thenReturn(mock(DalConnectionProvider.class));
      RequestContext requestContext = mock(RequestContext.class);
      requestContextStatic.when(RequestContext::get).thenReturn(requestContext);
      when(requestContext.getVariablesSecureApp()).thenReturn(mock(VariablesSecureApp.class));

      utilityStatic.when(() -> Utility.getContext(any(), any(), anyString(), anyString())).thenReturn(CONTEXT);

      mockedStatic.when(() -> FieldBuilder.getReferenceSelectors(reference))
          .thenReturn(mockSelectors);

      JSONObject result = FieldBuilder.getSelectorInfo(FIELD_ID, reference);

      assertNotNull(result);
      assertTrue(result.has(Constants.DATASOURCE_PROPERTY));
      assertTrue(result.has(Constants.FIELD_ID_PROPERTY));
    }
  }

  /**
   * Tests if the method returns the correct JSON object with tree selector information.
   * It mocks the necessary methods to return a valid tree selector.
   *
   * @throws JSONException if there is an error creating the JSON object
   */
  @Test
  void testGetSelectorInfoWithTreeSelector() throws JSONException {
    ReferenceSelectors mockSelectors = new ReferenceSelectors(null, referencedTree);
    when(referencedTree.getId()).thenReturn("tree-id");
    when(referencedTree.getDisplayfield()).thenReturn(referencedTreeField);
    when(referencedTreeField.getProperty()).thenReturn(DISPLAY_PROPERTY);
    when(referencedTree.getValuefield()).thenReturn(referencedTreeField);

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      mockedStatic.when(() -> FieldBuilder.getReferenceSelectors(reference))
          .thenReturn(mockSelectors);

      JSONObject result = FieldBuilder.getSelectorInfo(FIELD_ID, reference);

      assertNotNull(result);
      assertTrue(result.has(Constants.DATASOURCE_PROPERTY));
      assertTrue(result.has(Constants.SELECTOR_DEFINITION_PROPERTY));
      assertEquals("tree-id", result.get("treeReferenceId"));
    }
  }

  /**
   * Tests the getListInfo method to ensure it returns a JSONArray with the correct list information.
   * Mocks a list item and verifies the resulting JSON structure and values.
   *
   * @throws JSONException if there is an error creating or accessing the JSON object
   */
  @Test
  void testGetListInfo() throws JSONException {
    org.openbravo.model.ad.domain.List listItem = mock(org.openbravo.model.ad.domain.List.class);
    when(listItem.getId()).thenReturn(LIST_ID_STRING);
    when(listItem.getSearchKey()).thenReturn("searchKey");
    when(listItem.get(anyString(), any(Language.class), anyString())).thenReturn("List Name");

    List<org.openbravo.model.ad.domain.List> adListList = List.of(listItem);
    when(reference.getADListList()).thenReturn(adListList);

    JSONArray result = FieldBuilder.getListInfo(reference, language);

    assertNotNull(result);
    assertEquals(1, result.length());
    JSONObject listJson = result.getJSONObject(0);
    assertEquals(LIST_ID_STRING, listJson.getString("id"));
    assertEquals("searchKey", listJson.getString("value"));
    assertEquals("List Name", listJson.getString("label"));
  }

  /**
   * Tests if the method returns an empty JSONArray when there are no lists.
   */
  @Test
  void testGetDisplayFieldWithDisplayField() {
    when(selector.getDisplayfield()).thenReturn(selectorField);

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(selectorField))
          .thenReturn(DISPLAY_PROPERTY);

      String result = FieldBuilder.getDisplayField(selector);

      assertEquals(DISPLAY_PROPERTY, result);
    }
  }

  /**
   * Tests if the method returns "_identifier" when no display field is set.
   */
  @Test
  void testGetDisplayFieldWithDataSource() {
    when(selector.getDisplayfield()).thenReturn(null);
    when(selector.getObserdsDatasource()).thenReturn(dataSource);
    when(dataSource.getTable()).thenReturn(null);
    when(dataSource.getOBSERDSDatasourceFieldList()).thenReturn(List.of(datasourceField));
    when(datasourceField.getName()).thenReturn(DATASOURCE_FIELD);

    String result = FieldBuilder.getDisplayField(selector);

    assertEquals(DATASOURCE_FIELD_DOLLAR, result);
  }

  /**
   * Tests if the method returns "_identifier" when no display field is set and no data source is available.
   */
  @Test
  void testGetPropertyOrDataSourceFieldWithProperty() {
    when(selectorField.getProperty()).thenReturn("test.property");

    String result = FieldBuilder.getPropertyOrDataSourceField(selectorField);

    assertEquals("test$property", result);
  }

  /**
   * Tests if the method returns the display column alias when no property is set.
   */
  @Test
  void testGetPropertyOrDataSourceFieldWithDisplayColumnAlias() {
    when(selectorField.getProperty()).thenReturn(null);
    when(selectorField.getDisplayColumnAlias()).thenReturn("test.alias");

    String result = FieldBuilder.getPropertyOrDataSourceField(selectorField);

    assertEquals("test$alias", result);
  }

  /**
   * Tests if the method returns the data source field name when no property or display column alias is set.
   */
  @Test
  void testGetPropertyOrDataSourceFieldWithDatasourceField() {
    when(selectorField.getProperty()).thenReturn(null);
    when(selectorField.getDisplayColumnAlias()).thenReturn(null);
    when(selectorField.getObserdsDatasourceField()).thenReturn(datasourceField);
    when(datasourceField.getName()).thenReturn(DATASOURCE_FIELD);

    String result = FieldBuilder.getPropertyOrDataSourceField(selectorField);

    assertEquals(DATASOURCE_FIELD_DOLLAR, result);
  }

  /**
   * Tests if the method throws an exception when all fields are null.
   */
  @Test
  void testGetPropertyOrDataSourceFieldWithNullFields() {
    when(selectorField.getProperty()).thenReturn(null);
    when(selectorField.getDisplayColumnAlias()).thenReturn(null);
    when(selectorField.getObserdsDatasourceField()).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> FieldBuilder.getPropertyOrDataSourceField(selectorField));
  }

  /**
   * Tests if the method returns null when the reference is null.
   */
  @Test
  void testGetDomainTypeWithValidReferenceId() {
    try (MockedStatic<ModelProvider> mockedModelProvider = mockStatic(ModelProvider.class)) {
      org.openbravo.base.model.Reference mockReference = mock(org.openbravo.base.model.Reference.class);
      PrimitiveDomainType mockDomainType = mock(PrimitiveDomainType.class);

      mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
      when(modelProvider.getReference("test-reference-id")).thenReturn(mockReference);
      when(mockReference.getDomainType()).thenReturn(mockDomainType);

      DomainType result = FieldBuilder.getDomainType("test-reference-id");

      assertEquals(mockDomainType, result);
    }
  }

  /**
   * Tests if the method returns null when the reference ID is null.
   */
  @Test
  void testGetDisplayFieldWithIdentifierFallback() {
    when(selector.getDisplayfield()).thenReturn(null);
    when(selector.getObserdsDatasource()).thenReturn(null);

    String result = FieldBuilder.getDisplayField(selector);

    assertEquals("_identifier", result);
  }

  /**
   * Tests if the method returns "id" when no value field is set.
   */
  @Test
  void testGetValueFieldWithIdFallback() {
    when(selector.getValuefield()).thenReturn(null);
    when(selector.getObserdsDatasource()).thenReturn(null);

    String result = FieldBuilder.getValueField(selector);

    assertEquals("id", result);
  }

  /**
   * Tests if the method returns the value field when it is set.
   */
  @Test
  void testGetValueFieldWithCustomQuery() {
    when(selector.getValuefield()).thenReturn(selectorField);
    when(selector.isCustomQuery()).thenReturn(true);

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      mockedStatic.when(() -> FieldBuilder.getPropertyOrDataSourceField(selectorField))
          .thenReturn("valueProperty");

      String result = FieldBuilder.getValueField(selector);

      assertEquals("valueProperty", result);
    }
  }

  /**
   * Tests if the method returns "_identifier" when no value field is set and a data source is available.
   */
  @Test
  void testGetDisplayFieldWithDataSourceWithTable() {
    when(selector.getDisplayfield()).thenReturn(null);
    when(selector.getObserdsDatasource()).thenReturn(dataSource);
    when(dataSource.getTable()).thenReturn(table);

    String result = FieldBuilder.getDisplayField(selector);

    assertEquals("_identifier", result);
  }

  /**
   * Tests if the method returns "id" when no value field is set and a data source with a table is available.
   */
  @Test
  void testGetValueFieldWithDataSourceWithTable() {
    when(selector.getValuefield()).thenReturn(null);
    when(selector.getObserdsDatasource()).thenReturn(dataSource);
    when(dataSource.getTable()).thenReturn(table);

    String result = FieldBuilder.getValueField(selector);

    assertEquals("id", result);
  }

  /**
   * Tests the addSelectorInfo method with a custom query selector.
   * Verifies that the correct data source and text match style are set in the resulting JSON object.
   *
   * @throws Exception if any error occurs during the test
   */
  @Test
  void testAddSelectorInfoWithCustomQuerySelector() throws Exception {
    when(selector.getObserdsDatasource()).thenReturn(null);
    when(selector.isCustomQuery()).thenReturn(true);
    when(selector.getId()).thenReturn("selector-id");
    when(selector.getDisplayfield()).thenReturn(null);
    when(selector.getSuggestiontextmatchstyle()).thenReturn("exact");
    when(selector.getOBUISELSelectorFieldList()).thenReturn(Collections.emptyList());
    when(selector.getValuefield()).thenReturn(null);

    try (MockedStatic<FieldBuilder> mockedStatic = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      mockedStatic.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(JsonConstants.IDENTIFIER);
      mockedStatic.when(() -> FieldBuilder.getValueField(selector)).thenReturn(JsonConstants.ID);
      mockedStatic.when(() -> FieldBuilder.getExtraSearchFields(selector)).thenReturn("");

      JSONObject result = FieldBuilder.addSelectorInfo(FIELD_ID, selector);

      assertEquals(Constants.CUSTOM_QUERY_DS, result.getString(Constants.DATASOURCE_PROPERTY));
      assertEquals("exact", result.getString(JsonConstants.TEXTMATCH_PARAMETER));
    }
  }

  /**
   * Tests the private getHqlName method when an exception is thrown by field.getColumn().
   * Verifies that the method returns the field name as a fallback.
   *
   * @throws Exception if reflection fails or method invocation fails
   */
  @Test
  void testGetHqlNameSuccess() throws Exception {
    when(column.getTable()).thenReturn(table);
    when(table.getDBTableName()).thenReturn("test_table");
    when(column.getDBColumnName()).thenReturn("test_column");
    when(field.getName()).thenReturn(TEST_FIELD);

    try (MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class)) {
      mockedDataSourceUtils.when(() -> DataSourceUtils.getHQLColumnName(true, "test_table", "test_column"))
          .thenReturn(new String[]{ "hqlColumnName" });

      Method method = FieldBuilder.class.getDeclaredMethod("getHqlName", Field.class);
      method.setAccessible(true);
      String result = (String) method.invoke(null, field);

      assertEquals("hqlColumnName", result);
    }
  }

  /**
   * Tests the private getHqlName method when an exception is thrown by field.getColumn().
   * Verifies that the method returns the field name as a fallback.
   *
   * @throws Exception if reflection fails or method invocation fails
   */
  @Test
  void testGetHqlNameException() throws Exception {
    when(field.getName()).thenReturn(TEST_FIELD);
    when(field.getColumn()).thenThrow(new RuntimeException("Test exception"));

    Method method = FieldBuilder.class.getDeclaredMethod("getHqlName", Field.class);
    method.setAccessible(true);
    String result = (String) method.invoke(null, field);

    assertEquals(TEST_FIELD, result);
  }

  /**
   * Tests addADListList method to ensure it builds the correct JSONArray
   * from AD List entries associated to a Reference.
   *
   * @throws JSONException if JSON creation fails
   */
  @Test
  void testAddADListList() throws JSONException {
    // Mock OBContext and Language
    Language mockLanguage = mock(Language.class);
    OBContext mockContext = mock(OBContext.class);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(mockLanguage);

      // Mock AD List entry
      org.openbravo.model.ad.domain.List listItem =
          mock(org.openbravo.model.ad.domain.List.class);

      when(listItem.getId()).thenReturn(LIST_ID_STRING);
      when(listItem.getSearchKey()).thenReturn("SEARCH_KEY");
      when(listItem.get(
          org.openbravo.model.ad.domain.List.PROPERTY_NAME,
          mockLanguage,
          LIST_ID_STRING
      )).thenReturn("List Label");

      when(reference.getADListList()).thenReturn(List.of(listItem));

      // Execute
      JSONArray result = FieldBuilder.addADListList(reference);

      // Verify
      assertNotNull(result);
      assertEquals(1, result.length());

      JSONObject json = result.getJSONObject(0);
      assertEquals(LIST_ID_STRING, json.getString("id"));
      assertEquals("SEARCH_KEY", json.getString("value"));
      assertEquals("List Label", json.getString("label"));
    }
  }

  /**
   * Tests the addComboTableSelectorInfo method.
   * This test ensures that the method can correctly generate combo table selector information
   * by mocking all necessary Openbravo infrastructure and dependencies.
   *
   * @throws JSONException if there is an error during JSON construction
   */
  @Test
  void testAddComboTableSelectorInfo() throws JSONException {
    ProcessParameter processParameter = mock(ProcessParameter.class);
    Reference ref = mock(Reference.class);
    Validation validation = mock(Validation.class);
    Process processMock = mock(Process.class);
    VariablesSecureApp vars = mock(VariablesSecureApp.class);
    RequestContext requestContext = mock(RequestContext.class);
    DalConnectionProvider connProvider = mock(DalConnectionProvider.class);
    FieldProvider fieldProvider = mock(FieldProvider.class);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<DalConnectionProvider> dalConnStatic = mockStatic(DalConnectionProvider.class);
         MockedStatic<RequestContext> requestContextStatic = mockStatic(RequestContext.class);
         MockedStatic<Utility> utilityStatic = mockStatic(Utility.class);
         MockedConstruction<ComboTableData> comboTableDataMockedConstruction = mockConstruction(ComboTableData.class,
             (mock, context) -> {
               when(mock.select(false)).thenReturn(new FieldProvider[]{ fieldProvider });
             })) {

      OBDal obDal = mock(OBDal.class);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(ProcessParameter.class, TEST_FIELD_ID)).thenReturn(processParameter);

      when(processParameter.getReference()).thenReturn(ref);
      when(ref.getId()).thenReturn(REF_ID);
      when(processParameter.getDBColumnName()).thenReturn(COLUMN_NAME);
      when(processParameter.getValidation()).thenReturn(validation);
      when(validation.getId()).thenReturn(VAL_ID);
      when(processParameter.getProcess()).thenReturn(processMock);
      when(processMock.getId()).thenReturn(PROC_ID);

      dalConnStatic.when(DalConnectionProvider::getReadOnlyConnectionProvider).thenReturn(connProvider);
      requestContextStatic.when(RequestContext::get).thenReturn(requestContext);
      when(requestContext.getVariablesSecureApp()).thenReturn(vars);

      utilityStatic.when(() -> Utility.getContext(any(), any(), anyString(), anyString())).thenReturn(CONTEXT);

      when(fieldProvider.getField("ID")).thenReturn(RECORD_ID);
      when(fieldProvider.getField("NAME")).thenReturn(RECORD_NAME);

      JSONObject result = FieldBuilder.getSelectorInfo(TEST_FIELD_ID, null);

      assertNotNull(result);
      assertEquals(Constants.TABLE_DATASOURCE, result.getString(Constants.DATASOURCE_PROPERTY));
      JSONArray responseValues = result.getJSONArray(JsonConstants.RESPONSE_VALUES);
      assertEquals(1, responseValues.length());
      JSONObject entry = responseValues.getJSONObject(0);
      assertEquals(RECORD_ID, entry.getString("id"));
      assertEquals(RECORD_NAME, entry.getString("name"));
    }
  }

  /**
   * Tests the addComboTableSelectorInfo method when the field is not found.
   */
  @Test
  void testAddComboTableSelectorInfoFieldNotFound() throws JSONException {
    String fieldId = "non-existent-id";

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      OBDal obDal = mock(OBDal.class);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(ProcessParameter.class, fieldId)).thenReturn(null);

      JSONObject result = FieldBuilder.getSelectorInfo(fieldId, null);

      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  /**
   * Tests the addComboTableSelectorInfo method when an exception occurs.
   */
  @Test
  void testAddComboTableSelectorInfoWithException() {
    ProcessParameter processParameter = mock(ProcessParameter.class);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<DalConnectionProvider> dalConnStatic = mockStatic(DalConnectionProvider.class)) {

      OBDal obDal = mock(OBDal.class);
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(ProcessParameter.class, TEST_FIELD_ID)).thenReturn(processParameter);

      dalConnStatic.when(DalConnectionProvider::getReadOnlyConnectionProvider).thenThrow(new RuntimeException("DB Error"));

      assertThrows(org.openbravo.base.exception.OBException.class, () -> {
        FieldBuilder.getSelectorInfo(TEST_FIELD_ID, null);
      });
    }
  }

}
