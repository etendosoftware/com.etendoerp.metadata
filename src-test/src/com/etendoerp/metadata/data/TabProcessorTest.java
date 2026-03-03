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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.metadata.builders.FieldBuilderWithoutColumn;
import com.etendoerp.metadata.utils.Utils;

/**
 * This class tests the functionality of the TabProcessor class, which processes
 * tabs and fields
 * in the Openbravo ERP system. It includes tests for retrieving entity column
 * names, JSON field
 * representations, and access checks for processes.
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class TabProcessorTest {

  private static final String ERROR_PROCESSING_TAB_FIELDS_VIA_JDBC = "Error processing tab fields via JDBC";
  private static final String FIELD_ACCESS_ID = "fieldAccessId";
  private static final String FIELD_ID_KEY = "fieldId";
  private static final String TEST_FIELD_NAME = "testFieldName";

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
    when(mockField.getName()).thenReturn(TEST_FIELD_NAME);

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
    if (staticModelProvider != null)
      staticModelProvider.close();
    if (staticOBContext != null)
      staticOBContext.close();
    if (staticUtils != null)
      staticUtils.close();
    if (staticBaseProcessActionHandler != null)
      staticBaseProcessActionHandler.close();

    clearStaticCaches();
  }

  /**
   * Clears the static caches in TabProcessor using reflection.
   * This is necessary to prevent test pollution since the caches are static.
   */
  private void clearStaticCaches() {
    try {
      clearCacheField("fieldCache");
      clearCacheField("fieldAccessCache");
    } catch (Exception e) {
      // Ignore reflection errors in tests
    }
  }

  /**
   * Helper method to clear a specific private static field in TabProcessor.
   * 
   * @param fieldName the name of the static field to clear
   */
  private void clearCacheField(String fieldName) throws NoSuchFieldException, IllegalAccessException {
    java.lang.reflect.Field field = TabProcessor.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    Object cache = field.get(null);
    if (cache instanceof Map) {
      ((Map<?, ?>) cache).clear();
    }
  }

  /**
   * Creates a mock ConcurrentMap cache with no pre-existing entries for the
   * standard cache key.
   *
   * @return a mocked ConcurrentMap that returns null for the standard cache key
   */
  @SuppressWarnings("unchecked")
  private ConcurrentMap<String, JSONObject> createEmptyMockCache() {
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    when(cache.get(cacheKey)).thenReturn(null);
    return cache;
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
   * Tests the getEntityColumnName method of TabProcessor with a column that has a
   * null table.
   * Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNullTableReturnsNull() {
    when(mockColumn.getTable()).thenReturn(null);

    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertNull(result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with a table that has a
   * null name.
   * Expects the method to return null.
   */
  @Test
  void testGetEntityColumnNameWithNoEntityReturnsNull() {
    when(mockModelProvider.getEntity(TEST_TABLE_NAME)).thenReturn(null);

    String result = TabProcessor.getEntityColumnName(mockColumn);

    assertNull(result);
  }

  /**
   * Tests the getEntityColumnName method of TabProcessor with an entity that does
   * not have
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
   * Verifies that the method creates a FieldBuilderWithColumn and returns the
   * expected JSON.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAndColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ID_KEY, "test");

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
   * Tests the getJSONField method of TabProcessor with a field that has no
   * column.
   * Verifies that the method creates a FieldBuilderWithoutColumn and returns the
   * expected JSON.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldWithoutColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ID_KEY, "test");

    try (MockedConstruction<FieldBuilderWithoutColumn> mockedConstruction = mockConstruction(
        FieldBuilderWithoutColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getJSONField(mockField, false);

      assertEquals(expectedJSON, result);
      assertEquals(1, mockedConstruction.constructed().size());

      FieldBuilderWithoutColumn constructedBuilder = mockedConstruction.constructed().get(0);
      verify(constructedBuilder).toJSON();
    }
  }

  /**
   * Tests the getJSONField method of TabProcessor with a FieldAccess object that
   * has a column.
   * Verifies that the method creates a FieldBuilderWithColumn using the field
   * from FieldAccess
   * and returns the expected JSON representation.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAccessAndColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ACCESS_ID, "test");

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
   * Tests the getJSONField method of TabProcessor with a FieldAccess object that
   * has no column.
   * Verifies that the method creates a FieldBuilderWithoutColumn using the field
   * from FieldAccess
   * and returns the expected JSON representation.
   * 
   * @throws JSONException if JSON processing fails during field building
   */
  @Test
  void testGetJSONFieldWithFieldAccessWithoutColumnReturnsJSON() throws JSONException {
    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ACCESS_ID, "test");

    try (MockedConstruction<FieldBuilderWithoutColumn> mockedConstruction = mockConstruction(
        FieldBuilderWithoutColumn.class,
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
   * Tests the hasAccessToProcess method of TabProcessor with a column that has a
   * null process.
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
    staticBaseProcessActionHandler
        .when(() -> org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess(eq(mockProcess),
            any(Map.class)))
        .thenReturn(true);

    boolean result = TabProcessor.hasAccessToProcess(mockField, TEST_WINDOW_ID);

    assertTrue(result);
    staticBaseProcessActionHandler
        .verify(() -> org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess(eq(mockProcess),
            argThat(map -> TEST_WINDOW_ID.equals(map.get("windowId")))));
  }

  /**
   * Tests the getFields method of TabProcessor when a cached value is present.
   * Expects the method to return the cached result without processing fields.
   *
   * @throws JSONException if the JSON processing fails (not expected in this
   *                       test)
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
   * Tests the getFields method of TabProcessor when no cache exists and processes
   * fields.
   * Expects the method to process fields and cache the result.
   *
   * @throws JSONException if the JSON processing fails (not expected in this
   *                       test)
   */
  @Test
  void testGetFieldsWithoutCacheProcessesAndCaches() throws JSONException {
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();

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
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method when access predicate returns false.
   * Expects the field to be skipped.
   */
  @Test
  void testGetFieldsWithInvalidAccessSkipsField() {
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();

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
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method when column is null.
   * Expects the field to be skipped unless it has custom JS.
   */
  @Test
  void testGetFieldsWithNullColumnSkipsField() {
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();

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
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();

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
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    verify(cache).put(eq(cacheKey), any(JSONObject.class));
  }

  /**
   * Tests the getFields method with custom JS field (no column).
   * Expects the field to be processed with custom JS logic.
   */
  @Test
  void testGetFieldsWithCustomJSField() throws JSONException {
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();

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
    String cacheKey = TEST_TAB_ID + "#" + TEST_DATE + TEST_CONTEXT;
    verify(cache).put(eq(cacheKey), any(JSONObject.class));

    verify(mockField).setName("TestField Canva");
  }

  /**
   * Tests the getFields method when the access predicate throws a
   * GenericJDBCException.
   * Expects the exception to be re-thrown with the JDBC error message.
   */
  @Test
  void testGetFieldsWithGenericJDBCExceptionRethrowsException() {
    ConcurrentMap<String, JSONObject> cache = createEmptyMockCache();
    SQLException sqlException = new SQLException("SQL error");
    GenericJDBCException jdbcException = new GenericJDBCException("Original JDBC error", sqlException);

    OBException thrown = assertThrows(OBException.class,
        () -> TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(),
            List.of(mockField),
            field -> {
              throw jdbcException;
            },
            Field::getColumn,
            field -> null,
            field -> null,
            Field::getName,
            Field::setName,
            (field, withColumn) -> new JSONObject(),
            cache));

    assertEquals(ERROR_PROCESSING_TAB_FIELDS_VIA_JDBC, thrown.getMessage());
    assertEquals(sqlException, thrown.getCause());
  }

  /**
   * Tests getTabFields(Tab) processes the tab's field list and returns a result
   * containing the field mapped by its entity column name.
   * Uses mocked construction of FieldBuilderWithColumn to control the JSON
   * output.
   */
  @Test
  void testGetTabFieldsWithTabProcessesFieldList() throws JSONException {
    when(mockTab.getId()).thenReturn(TEST_TAB_ID);
    when(mockTab.getUpdated()).thenReturn(TEST_DATE);
    when(mockTab.getADFieldList()).thenReturn(List.of(mockField));

    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ID_KEY, "tabField");

    try (MockedConstruction<FieldBuilderWithColumn> mockedConstruction = mockConstruction(
        FieldBuilderWithColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getTabFields(mockTab);

      assertNotNull(result);
      assertTrue(result.has(TEST_PROPERTY_NAME));
      assertEquals(expectedJSON, result.get(TEST_PROPERTY_NAME));
    }
  }

  /**
   * Tests getTabFields(Tab) returns an empty result when the field is not active.
   * The isFieldAccessible predicate should filter out inactive fields.
   */
  @Test
  void testGetTabFieldsWithInactiveFieldReturnsEmpty() {
    when(mockTab.getId()).thenReturn(TEST_TAB_ID);
    when(mockTab.getUpdated()).thenReturn(TEST_DATE);
    when(mockTab.getADFieldList()).thenReturn(List.of(mockField));
    when(mockField.isActive()).thenReturn(false);

    JSONObject result = TabProcessor.getTabFields(mockTab);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Tests getTabFields(TabAccess) processes the field access list and returns a
   * result
   * containing the field mapped by its entity column name.
   * Uses mocked construction of FieldBuilderWithColumn to control the JSON
   * output.
   */
  @Test
  void testGetTabFieldsWithTabAccessProcessesFieldAccessList() throws JSONException {
    TabAccess mockTabAccess = mock(TabAccess.class);
    when(mockTabAccess.getId()).thenReturn(TEST_TAB_ID);
    when(mockTabAccess.getUpdated()).thenReturn(TEST_DATE);
    when(mockTabAccess.getADFieldAccessList()).thenReturn(List.of(mockFieldAccess));

    JSONObject expectedJSON = new JSONObject();
    expectedJSON.put(FIELD_ACCESS_ID, "tabAccessField");

    try (MockedConstruction<FieldBuilderWithColumn> mockedConstruction = mockConstruction(
        FieldBuilderWithColumn.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(expectedJSON))) {

      JSONObject result = TabProcessor.getTabFields(mockTabAccess);

      assertNotNull(result);
      assertTrue(result.has(TEST_PROPERTY_NAME));
      assertEquals(expectedJSON, result.get(TEST_PROPERTY_NAME));
    }
  }

  /**
   * Tests getTabFields(TabAccess) returns an empty result when the FieldAccess is
   * not active.
   * The isFieldAccessAccessible predicate checks fieldAccess.isActive() first.
   */
  @Test
  void testGetTabFieldsWithInactiveFieldAccessReturnsEmpty() {
    TabAccess mockTabAccess = mock(TabAccess.class);
    when(mockTabAccess.getId()).thenReturn(TEST_TAB_ID);
    when(mockTabAccess.getUpdated()).thenReturn(TEST_DATE);
    when(mockTabAccess.getADFieldAccessList()).thenReturn(List.of(mockFieldAccess));
    when(mockFieldAccess.isActive()).thenReturn(false);

    JSONObject result = TabProcessor.getTabFields(mockTabAccess);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Tests getTabFields(TabAccess) returns an empty result when the underlying
   * Field is not active.
   * The isFieldAccessAccessible predicate delegates to isFieldAccessible which
   * checks field.isActive().
   */
  @Test
  void testGetTabFieldsWithFieldAccessActiveButFieldInactiveReturnsEmpty() {
    TabAccess mockTabAccess = mock(TabAccess.class);
    when(mockTabAccess.getId()).thenReturn(TEST_TAB_ID);
    when(mockTabAccess.getUpdated()).thenReturn(TEST_DATE);
    when(mockTabAccess.getADFieldAccessList()).thenReturn(List.of(mockFieldAccess));
    when(mockFieldAccess.isActive()).thenReturn(true);
    when(mockField.isActive()).thenReturn(false);

    JSONObject result = TabProcessor.getTabFields(mockTabAccess);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Tests getTabFields(TabAccess) returns an empty result when the field's
   * display logic
   * evaluates to false at server level. This exercises the
   * evaluateDisplayLogicAtServerLevel
   * check inside isFieldAccessible (called by isFieldAccessAccessible).
   */
  @Test
  void testGetTabFieldsWithFieldAccessDisplayLogicFalseReturnsEmpty() {
    TabAccess mockTabAccess = mock(TabAccess.class);
    when(mockTabAccess.getId()).thenReturn(TEST_TAB_ID);
    when(mockTabAccess.getUpdated()).thenReturn(TEST_DATE);
    when(mockTabAccess.getADFieldAccessList()).thenReturn(List.of(mockFieldAccess));
    staticUtils.when(() -> Utils.evaluateDisplayLogicAtServerLevel(mockField)).thenReturn(false);

    JSONObject result = TabProcessor.getTabFields(mockTabAccess);

    assertNotNull(result);
    assertEquals(0, result.length());
  }

  /**
   * Tests getTabFields(TabAccess) returns an empty result when the field's
   * associated process
   * is not accessible. This exercises the hasAccessToProcess check inside
   * isFieldAccessible
   * (called by isFieldAccessAccessible).
   */
  @Test
  void testGetTabFieldsWithFieldAccessNoProcessAccessReturnsEmpty() {
    TabAccess mockTabAccess = mock(TabAccess.class);
    when(mockTabAccess.getId()).thenReturn(TEST_TAB_ID);
    when(mockTabAccess.getUpdated()).thenReturn(TEST_DATE);
    when(mockTabAccess.getADFieldAccessList()).thenReturn(List.of(mockFieldAccess));

    when(mockColumn.getOBUIAPPProcess()).thenReturn(mockProcess);
    staticBaseProcessActionHandler
        .when(() -> org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess(
            eq(mockProcess), any(Map.class)))
        .thenReturn(false);

    JSONObject result = TabProcessor.getTabFields(mockTabAccess);

    assertNotNull(result);
    assertEquals(0, result.length());
  }
}
