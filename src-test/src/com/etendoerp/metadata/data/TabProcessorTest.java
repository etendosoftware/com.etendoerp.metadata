package com.etendoerp.metadata.data;

import static com.etendoerp.metadata.MetadataTestConstants.TEST_COLUMN_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_CONTEXT;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_DATE;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_EXCEPTION;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROPERTY_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TABLE_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TAB_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_WINDOW_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.metadata.builders.FieldBuilderWithoutColumn;
import com.etendoerp.metadata.utils.Utils;

/**
 * This class tests the functionality of the TabProcessor class, which processes tabs and fields
 * in the Openbravo ERP system. It includes tests for retrieving entity column names, JSON field
 * representations, and access checks for processes.
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class TabProcessorTest {

  @Mock
  private Tab mockTab;
  @Mock
  private Field mockField;
  @Mock
  private FieldAccess mockFieldAccess;
  @Mock
  private Column mockColumn;
  @Mock
  private Table mockTable;
  @Mock
  private Window mockWindow;
  @Mock
  private Process mockProcess;
  @Mock
  private Entity mockEntity;
  @Mock
  private Property mockProperty;
  @Mock
  private ModelProvider mockModelProvider;
  @Mock
  private OBContext mockOBContext;

  private MockedStatic<ModelProvider> staticModelProvider;
  private MockedStatic<OBContext> staticOBContext;
  private MockedStatic<Utils> staticUtils;
  private MockedStatic<org.openbravo.client.application.process.BaseProcessActionHandler> staticBaseProcessActionHandler;

  /**
   * Sets up the test environment before each test case.
   * Mocks static methods and configures common mock behaviors.
   */
  @BeforeEach
  void setUp() {
    staticModelProvider = mockStatic(ModelProvider.class);
    staticOBContext = mockStatic(OBContext.class);
    staticUtils = mockStatic(Utils.class);
    staticBaseProcessActionHandler = mockStatic(
        org.openbravo.client.application.process.BaseProcessActionHandler.class);

    staticModelProvider.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
    staticOBContext.when(OBContext::getOBContext).thenReturn(mockOBContext);
    staticUtils.when(() -> Utils.evaluateDisplayLogicAtServerLevel(any(Field.class))).thenReturn(true);

    when(mockField.getColumn()).thenReturn(mockColumn);
    when(mockField.getTab()).thenReturn(mockTab);
    when(mockField.isActive()).thenReturn(true);
    when(mockField.getEtmetaCustomjs()).thenReturn(null);
    when(mockField.getClientclass()).thenReturn(null);
    when(mockField.getName()).thenReturn("testFieldName");

    when(mockFieldAccess.getField()).thenReturn(mockField);
    when(mockFieldAccess.isActive()).thenReturn(true);

    when(mockColumn.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
    when(mockColumn.getTable()).thenReturn(mockTable);
    when(mockColumn.getOBUIAPPProcess()).thenReturn(null);

    when(mockTable.getName()).thenReturn(TEST_TABLE_NAME);

    when(mockTab.getWindow()).thenReturn(mockWindow);
    when(mockWindow.getId()).thenReturn(TEST_WINDOW_ID);

    when(mockModelProvider.getEntity(TEST_TABLE_NAME)).thenReturn(mockEntity);
    when(mockEntity.getPropertyByColumnName(TEST_COLUMN_NAME)).thenReturn(mockProperty);
    when(mockProperty.getName()).thenReturn(TEST_PROPERTY_NAME);

    when(mockOBContext.toString()).thenReturn("testContext");
  }

  /**
   * Cleans up the test environment after each test case.
   * Closes mocked static methods to prevent interference between tests.
   */
  @AfterEach
  void tearDown() {
    if (staticModelProvider != null) staticModelProvider.close();
    if (staticOBContext != null) staticOBContext.close();
    if (staticUtils != null) staticUtils.close();
    if (staticBaseProcessActionHandler != null) staticBaseProcessActionHandler.close();
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with a valid column.
   * Expects the method to return the corresponding property name.
   */
  @Test
  void testGetEntityColumnNameWithValidColumnReturnsPropertyName() {
    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertEquals(TEST_PROPERTY_NAME, result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with a null column.
   * Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNullColumnReturnsNull() {
    String result = TabProcessor.getEntityColumnName(null);

    assertNull(result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with a column that has a null table.
   * Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNullTableReturnsNull() {
    when(mockColumn.getTable()).thenReturn(null);

    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertNull(result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with a table that has a null name.
   * Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNoEntityReturnsNull() {
    when(mockModelProvider.getEntity(TEST_TABLE_NAME)).thenReturn(null);

    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertNull(result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with an entity that does not have
   * a property for the given column name. Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNoPropertyReturnsNull() {
    when(mockEntity.getPropertyByColumnName(TEST_COLUMN_NAME)).thenReturn(null);

    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertNull(result);
  }

  /**
   * Tests the getJSONField method of TabProcessor with a field that has a column.
   * Verifies that the method creates a FieldBuilderWithColumn and returns the expected JSON.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAndColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put("fieldId", "test");

    try (MockedConstruction<FieldBuilderWithColumn> mockedConstruction = mockConstruction(FieldBuilderWithColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getJSONField(mockField, true);

      assertEquals(expectedJSON, result);
      assertEquals(1, mockedConstruction.constructed().size());

      FieldBuilderWithColumn constructedBuilder = mockedConstruction.constructed().get(0);
      verify(constructedBuilder).toJSON();
    }
  }

  /**
   * Tests the getJSONField method of TabProcessor with a field that has no column.
   * Verifies that the method creates a FieldBuilderWithoutColumn and returns the expected JSON.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldWithoutColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put("fieldId", "test");

    try (MockedConstruction<FieldBuilderWithoutColumn> mockedConstruction = mockConstruction(FieldBuilderWithoutColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getJSONField(mockField, false);

      assertEquals(expectedJSON, result);
      assertEquals(1, mockedConstruction.constructed().size());

      FieldBuilderWithoutColumn constructedBuilder = mockedConstruction.constructed().get(0);
      verify(constructedBuilder).toJSON();
    }
  }

  /**
   * Tests the getJSONField method of TabProcessor with a FieldAccess object that has a column.
   * Verifies that the method creates a FieldBuilderWithColumn using the field from FieldAccess
   * and returns the expected JSON representation.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAccessAndColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put("fieldAccessId", "test");

    try (MockedConstruction<FieldBuilderWithColumn> mockedConstruction = mockConstruction(FieldBuilderWithColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getJSONField(mockFieldAccess, true);

      assertEquals(expectedJSON, result);
      assertEquals(1, mockedConstruction.constructed().size());

      FieldBuilderWithColumn constructedBuilder = mockedConstruction.constructed().get(0);
      verify(constructedBuilder).toJSON();
    }
  }

  /**
   * Tests the getJSONField method of TabProcessor with a FieldAccess object that has no column.
   * Verifies that the method creates a FieldBuilderWithoutColumn using the field from FieldAccess
   * and returns the expected JSON representation.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAccessWithoutColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put("fieldAccessId", "test");

    try (MockedConstruction<FieldBuilderWithoutColumn> mockedConstruction = mockConstruction(FieldBuilderWithoutColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getJSONField(mockFieldAccess, false);

      assertEquals(expectedJSON, result);
      assertEquals(1, mockedConstruction.constructed().size());

      FieldBuilderWithoutColumn constructedBuilder = mockedConstruction.constructed().get(0);
      verify(constructedBuilder).toJSON();
    }
  }

  /**
   * Tests the getJSONField method of TabProcessor when JSONException occurs.
   * Expects the method to return an empty JSON object.
   */
  @Test
  void testGetJSONFieldWithJSONExceptionReturnsEmptyJSON() {
    try (MockedConstruction<FieldBuilderWithColumn> mockedConstruction = mockConstruction(FieldBuilderWithColumn.class,
        (mock, context) -> when(mock.toJSON()).thenThrow(new JSONException(TEST_EXCEPTION)))) {

      JSONObject result = TabProcessor.getJSONField(mockField, true);

      assertNotNull(result);
      assertEquals(0, result.length());
      assertEquals(1, mockedConstruction.constructed().size());
    }
  }

  /**
   * Tests the hasAccessToProcess method of TabProcessor with a null column.
   * Expects the method to return true.
   */
  @Test
  void testHasAccessToProcessWithNullColumnReturnsTrue() {
    when(mockField.getColumn()).thenReturn(null);

    boolean result = TabProcessor.hasAccessToProcess(mockField, TEST_WINDOW_ID);

    assertTrue(result);
  }

  /**
   * Tests the hasAccessToProcess method of TabProcessor with a column that has a null process.
   * Expects the method to return true.
   */
  @Test
  void testHasAccessToProcessWithNullProcessReturnsTrue() {
    when(mockColumn.getOBUIAPPProcess()).thenReturn(null);

    boolean result = TabProcessor.hasAccessToProcess(mockField, TEST_WINDOW_ID);

    assertTrue(result);
  }

  /**
   * Tests the hasAccessToProcess method of TabProcessor with a valid process.
   * Expects the method to call the hasAccess method and return its result.
   */
  @Test
  void testHasAccessToProcessWithValidProcessCallsHasAccessMethod() {
    when(mockColumn.getOBUIAPPProcess()).thenReturn(mockProcess);
    staticBaseProcessActionHandler.when(() ->
            org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess(eq(mockProcess), any(Map.class)))
        .thenReturn(true);

    boolean result = TabProcessor.hasAccessToProcess(mockField, TEST_WINDOW_ID);

    assertTrue(result);
    staticBaseProcessActionHandler.verify(() ->
        org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess(eq(mockProcess),
            argThat(map -> TEST_WINDOW_ID.equals(map.get("windowId")))));
  }

  /**
   * Tests the getFields method of TabProcessor when a cached value is present.
   * Expects the method to return the cached result without processing fields.
   *
   * @throws JSONException if the JSON processing fails (not expected in this test)
   */
  @Test
  void testGetFieldsWithCachedValueReturnsCachedResult() throws JSONException {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    JSONObject cachedResult = new JSONObject();
    cachedResult.put("cached", true);
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(cachedResult);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> true, Field::getColumn,
        field -> null,
        field -> null,
        Field::getName,
        Field::setName,
        (field, withColumn) -> new JSONObject(),
        cache);

    assertEquals(cachedResult, result);
    verify(cache, never()).put(any(), any());
  }

  /**
   * Tests the getFields method of TabProcessor when no cache exists and processes fields.
   * Expects the method to process fields and cache the result.
   *
   * @throws JSONException if the JSON processing fails (not expected in this test)
   */
  @Test
  void testGetFieldsWithoutCacheProcessesAndCaches() throws JSONException {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);

    JSONObject fieldJSON = new JSONObject();
    fieldJSON.put("processed", true);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> true, Field::getColumn,
        field -> null,
        field -> null,
        Field::getName,
        Field::setName,
        (field, withColumn) -> fieldJSON,
        cache);

    assertNotNull(result);
    assertEquals(fieldJSON, result.get(TEST_PROPERTY_NAME));
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method when access predicate returns false.
   * Expects the field to be skipped.
   */
  @Test
  void testGetFieldsWithInvalidAccessSkipsField() {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> false, Field::getColumn,
        field -> null,
        field -> null,
        Field::getName,
        Field::setName,
        (field, withColumn) -> new JSONObject(),
        cache);

    assertNotNull(result);
    assertEquals(0, result.length());
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method when column is null.
   * Expects the field to be skipped unless it has custom JS.
   */
  @Test
  void testGetFieldsWithNullColumnSkipsField() {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> true, field -> null,
        field -> null,
        field -> null,
        Field::getName,
        Field::setName,
        (field, withColumn) -> new JSONObject(),
        cache);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Tests the getFields method when entity column name cannot be determined.
   * Expects the field to be skipped.
   */
  @Test
  void testGetFieldsWithNullEntityColumnNameSkipsField() {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);

    when(mockProperty.getName()).thenReturn(null);
    when(mockEntity.getPropertyByColumnName(TEST_COLUMN_NAME)).thenReturn(null);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> true, Field::getColumn,
        field -> null,
        field -> null,
        Field::getName,
        Field::setName,
        (field, withColumn) -> new JSONObject(),
        cache);

    assertNotNull(result);
    assertEquals(0, result.length());
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method with custom JS field (no column).
   * Expects the field to be processed with custom JS logic.
   */
  @Test
  void testGetFieldsWithCustomJSField() throws JSONException {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);

    when(mockField.getColumn()).thenReturn(null);
    when(mockField.getEtmetaCustomjs()).thenReturn("someCustomJS");
    when(mockField.getClientclass()).thenReturn("TestClientClass");
    when(mockField.getName()).thenReturn("TestField");

    JSONObject fieldJSON = new JSONObject();
    fieldJSON.put("customField", true);

    JSONObject result = TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
        List.of(mockField), field -> true, Field::getColumn,
        Field::getEtmetaCustomjs,
        Field::getClientclass,
        Field::getName,
        Field::setName,
        (field, withColumn) -> fieldJSON,
        cache);

    assertNotNull(result);
    assertTrue(result.has("TestField_TestClientClass"));
    assertEquals(fieldJSON, result.get("TestField_TestClientClass"));
    verify(cache).put(eq(cacheKey), any(JSONObject.class));

    verify(mockField).setName("TestField Canva");
  }
}
