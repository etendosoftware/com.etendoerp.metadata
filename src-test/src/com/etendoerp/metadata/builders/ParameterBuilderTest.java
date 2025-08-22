package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.DATASOURCE_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.JS_EXPRESSION;
import static com.etendoerp.metadata.MetadataTestConstants.PARAMETER_ID;
import static com.etendoerp.metadata.MetadataTestConstants.READONLY_LOGIC;
import static com.etendoerp.metadata.MetadataTestConstants.READ_ONLY_LOGIC_EXPRESSION;
import static com.etendoerp.metadata.MetadataTestConstants.REF_LIST;
import static com.etendoerp.metadata.MetadataTestConstants.SELECTOR;
import static com.etendoerp.metadata.MetadataTestConstants.WINDOW;
import static com.etendoerp.metadata.MetadataTestConstants.WINDOW_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.RefWindow;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.utils.Constants;

/**
 * Unit tests for ParameterBuilder using pure mocking approach.
 * Tests all the functionality including readonly logic, selector references, list references, and window references.
 */
@ExtendWith(MockitoExtension.class)
class ParameterBuilderTest {

  private Parameter mockParameter;
  private OBContext mockContext;
  private Language mockLanguage;


  /**
   * Sets up mock objects before each test execution.
   * Initializes mockParameter, mockContext, and mockLanguage with basic configuration.
   */
  @BeforeEach
  void setUp() {
    mockParameter = mock(Parameter.class);
    mockContext = mock(OBContext.class);
    mockLanguage = mock(Language.class);

    when(mockContext.getLanguage()).thenReturn(mockLanguage);
  }

  /**
   * Tests that the ParameterBuilder constructor creates an instance successfully.
   */
  @Test
  void constructorCreatesInstanceSuccessfully() {
    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);

      assertNotNull(parameterBuilder);
    }
  }

  /**
   * Tests the toJSON method returns basic parameter JSON without additional features.
   * Verifies that when a parameter has no readonly logic or reference, the method
   * returns a basic JSON object with core parameter information only.
   * 
   * @throws Exception if JSON processing fails
   */
  @Test
  void toJSONReturnsBasicParameterJSON() throws Exception {
    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               parameterJson.put("name", "Test Parameter");
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertEquals(PARAMETER_ID, result.getString("id"));
      assertEquals("Test Parameter", result.getString("name"));
      assertFalse(result.has(READ_ONLY_LOGIC_EXPRESSION));
      assertFalse(result.has(SELECTOR));
      assertFalse(result.has(REF_LIST));
      assertFalse(result.has(WINDOW));
    }
  }

  /**
   * Tests the toJSON method includes readonly logic expression when present.
   * Verifies that when a parameter has readonly logic defined, the method
   * processes it through DynamicExpressionParser and includes the JavaScript expression.
   * 
   * @throws Exception if JSON processing or expression parsing fails
   */
  @Test
  void toJSONWithReadOnlyLogicIncludesExpression() throws Exception {
    when(mockParameter.getReadOnlyLogic()).thenReturn(READONLY_LOGIC);
    when(mockParameter.getReference()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             });
         MockedConstruction<DynamicExpressionParser> ignored2 = mockConstruction(DynamicExpressionParser.class,
             (mock, context) -> when(mock.getJSExpression()).thenReturn(JS_EXPRESSION))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(READ_ONLY_LOGIC_EXPRESSION));
      assertEquals(JS_EXPRESSION, result.getString(READ_ONLY_LOGIC_EXPRESSION));
    }
  }

  /**
   * Tests the toJSON method excludes readonly logic expression when blank.
   * Verifies that when a parameter has blank or whitespace-only readonly logic,
   * the method does not include the readOnlyLogicExpression in the JSON output.
   * 
   * @throws Exception if JSON processing fails
   */
  @Test
  void toJSONWithBlankReadOnlyLogicDoesNotIncludeExpression() throws Exception {
    when(mockParameter.getReadOnlyLogic()).thenReturn("   ");
    when(mockParameter.getReference()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertFalse(result.has(READ_ONLY_LOGIC_EXPRESSION));
    }
  }

  /**
   * Tests the toJSON method includes selector information for selector references.
   * Verifies that when a parameter has a selector reference type (table reference),
   * the method includes selector information obtained from FieldBuilder.
   * 
   * @throws Exception if JSON processing or selector info retrieval fails
   */
  @Test
  void toJSONWithSelectorReferenceIncludesSelectorInfo() throws Exception {
    Reference mockReference = mock(Reference.class);
    Reference mockReferenceSearchKey = mock(Reference.class);

    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(mockReference);
    when(mockParameter.getReferenceSearchKey()).thenReturn(mockReferenceSearchKey);
    when(mockParameter.getId()).thenReturn(PARAMETER_ID);
    when(mockReference.getId()).thenReturn("18");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      JSONObject selectorInfo = new JSONObject();
      selectorInfo.put(DATASOURCE_NAME, "TestDataSource");
      mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(PARAMETER_ID, mockReferenceSearchKey))
          .thenReturn(selectorInfo);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(SELECTOR));
      JSONObject selector = result.getJSONObject(SELECTOR);
      assertEquals("TestDataSource", selector.getString(DATASOURCE_NAME));
    }
  }

  /**
   * Tests the toJSON method includes reference list information for list references.
   * Verifies that when a parameter has a list reference type, the method
   * includes reference list information with list items obtained from FieldBuilder.
   * 
   * @throws Exception if JSON processing or list info retrieval fails
   */
  @Test
  void toJSONWithListReferenceIncludesRefListInfo() throws Exception {
    Reference mockReference = mock(Reference.class);
    Reference mockReferenceSearchKey = mock(Reference.class);

    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(mockReference);
    when(mockParameter.getReferenceSearchKey()).thenReturn(mockReferenceSearchKey);
    when(mockReference.getId()).thenReturn(Constants.LIST_REFERENCE_ID);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      JSONArray refListInfo = new JSONArray();
      JSONObject listItem = new JSONObject();
      listItem.put("id", "list-item-1");
      listItem.put("value", "VALUE1");
      listItem.put("label", "List Item 1");
      refListInfo.put(listItem);

      mockedFieldBuilder.when(() -> FieldBuilder.getListInfo(mockReferenceSearchKey, mockLanguage))
          .thenReturn(refListInfo);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(REF_LIST));
      JSONArray refList = result.getJSONArray(REF_LIST);
      assertEquals(1, refList.length());
      JSONObject item = refList.getJSONObject(0);
      assertEquals("list-item-1", item.getString("id"));
      assertEquals("VALUE1", item.getString("value"));
      assertEquals("List Item 1", item.getString("label"));
    }
  }

  /**
   * Tests the toJSON method includes window information for window references.
   * Verifies that when a parameter has a window reference type, the method
   * includes window information obtained from WindowBuilder for the referenced window.
   * 
   * @throws Exception if JSON processing or window info retrieval fails
   */
  @Test
  void toJSONWithWindowReferenceIncludesWindowInfo() throws Exception {
    Reference mockReference = mock(Reference.class);
    Reference mockReferenceSearchKey = mock(Reference.class);
    RefWindow mockRefWindow = mock(RefWindow.class);
    Window mockWindow = mock(Window.class);

    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(mockReference);
    when(mockParameter.getReferenceSearchKey()).thenReturn(mockReferenceSearchKey);
    when(mockReference.getId()).thenReturn(Constants.WINDOW_REFERENCE_ID);

    List<RefWindow> refWindows = new ArrayList<>();
    refWindows.add(mockRefWindow);
    when(mockReferenceSearchKey.getOBUIAPPRefWindowList()).thenReturn(refWindows);
    when(mockRefWindow.getWindow()).thenReturn(mockWindow);
    when(mockWindow.getId()).thenReturn(WINDOW_ID);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             });
         MockedConstruction<WindowBuilder> ignored2 = mockConstruction(WindowBuilder.class,
             (mock, context) -> {
               JSONObject windowJson = new JSONObject();
               windowJson.put("id", WINDOW_ID);
               windowJson.put("name", "Test Window");
               when(mock.toJSON()).thenReturn(windowJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertTrue(result.has(WINDOW));
      JSONObject window = result.getJSONObject(WINDOW);
      assertEquals(WINDOW_ID, window.getString("id"));
      assertEquals("Test Window", window.getString("name"));
    }
  }

  /**
   * Tests the toJSON method includes selector information for all supported selector reference types.
   * Verifies that the method correctly identifies and processes all known selector reference IDs
   * (table, table directory, search, product, and business partner references).
   * 
   * @throws Exception if JSON processing or selector info retrieval fails
   */
  @Test
  void toJSONWithAllSelectorReferenceTypesIncludesSelectorInfo() throws Exception {
    String[] selectorReferenceIds = { "18", "19", "30", "95E2A8B50A254B2AAE6774B8C2F28120", "8C57A4A2E05F4261A1FADF47C30398AD" };

    for (String referenceId : selectorReferenceIds) {
      Reference mockReference = mock(Reference.class);
      Reference mockReferenceSearchKey = mock(Reference.class);

      when(mockParameter.getReadOnlyLogic()).thenReturn(null);
      when(mockParameter.getReference()).thenReturn(mockReference);
      when(mockParameter.getReferenceSearchKey()).thenReturn(mockReferenceSearchKey);
      when(mockParameter.getId()).thenReturn(PARAMETER_ID);
      when(mockReference.getId()).thenReturn(referenceId);

      try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
           MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class);
           MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
               (mock, context) -> {
                 JSONObject parameterJson = new JSONObject();
                 parameterJson.put("id", PARAMETER_ID);
                 when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
               })) {

        mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

        JSONObject selectorInfo = new JSONObject();
        selectorInfo.put("referenceType", referenceId);
        mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(PARAMETER_ID, mockReferenceSearchKey))
            .thenReturn(selectorInfo);

        ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
        JSONObject result = parameterBuilder.toJSON();

        assertNotNull(result);
        assertTrue(result.has(SELECTOR), "Selector should be present for reference ID: " + referenceId);
        JSONObject selector = result.getJSONObject(SELECTOR);
        assertEquals(referenceId, selector.getString("referenceType"));
      }
    }
  }

  /**
   * Tests the toJSON method with a complex parameter that includes all features.
   * Verifies that when a parameter has readonly logic, selector reference, and all
   * other features combined, the method correctly includes all information in the JSON output.
   * 
   * @throws Exception if JSON processing, expression parsing, or selector info retrieval fails
   */
  @Test
  void toJSONWithComplexParameterIncludesAllFeatures() throws Exception {
    Reference mockReference = mock(Reference.class);
    Reference mockReferenceSearchKey = mock(Reference.class);

    when(mockParameter.getReadOnlyLogic()).thenReturn(READONLY_LOGIC);
    when(mockParameter.getReference()).thenReturn(mockReference);
    when(mockParameter.getReferenceSearchKey()).thenReturn(mockReferenceSearchKey);
    when(mockParameter.getId()).thenReturn(PARAMETER_ID);
    when(mockReference.getId()).thenReturn("18");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               parameterJson.put("name", "Complex Parameter");
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             });
         MockedConstruction<DynamicExpressionParser> ignored2 = mockConstruction(DynamicExpressionParser.class,
             (mock, context) -> when(mock.getJSExpression()).thenReturn(JS_EXPRESSION))) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      JSONObject selectorInfo = new JSONObject();
      selectorInfo.put(DATASOURCE_NAME, "ComplexDataSource");
      mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(PARAMETER_ID, mockReferenceSearchKey))
          .thenReturn(selectorInfo);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertEquals(PARAMETER_ID, result.getString("id"));
      assertEquals("Complex Parameter", result.getString("name"));
      assertTrue(result.has(READ_ONLY_LOGIC_EXPRESSION));
      assertEquals(JS_EXPRESSION, result.getString(READ_ONLY_LOGIC_EXPRESSION));
      assertTrue(result.has(SELECTOR));
      JSONObject selector = result.getJSONObject(SELECTOR);
      assertEquals("ComplexDataSource", selector.getString(DATASOURCE_NAME));
    }
  }

  /**
   * Tests the toJSON method excludes reference information when reference is null.
   * Verifies that when a parameter has no reference defined, the method
   * does not include selector, refList, or window information in the JSON output.
   * 
   * @throws Exception if JSON processing fails
   */
  @Test
  void toJSONWithNullReferenceDoesNotIncludeReferenceInfo() throws Exception {
    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(null);

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertFalse(result.has(SELECTOR));
      assertFalse(result.has(REF_LIST));
      assertFalse(result.has(WINDOW));
    }
  }

  /**
   * Tests the toJSON method excludes reference information for unknown reference types.
   * Verifies that when a parameter has an unknown or unsupported reference type,
   * the method does not include selector, refList, or window information in the JSON output.
   * 
   * @throws Exception if JSON processing fails
   */
  @Test
  void toJSONWithUnknownReferenceTypeDoesNotIncludeReferenceInfo() throws Exception {
    Reference mockReference = mock(Reference.class);

    when(mockParameter.getReadOnlyLogic()).thenReturn(null);
    when(mockParameter.getReference()).thenReturn(mockReference);
    when(mockReference.getId()).thenReturn("UNKNOWN_REFERENCE_ID");

    try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
         MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
             (mock, context) -> {
               JSONObject parameterJson = new JSONObject();
               parameterJson.put("id", PARAMETER_ID);
               when(mock.toJsonObject(any(), eq(DataResolvingMode.FULL_TRANSLATABLE))).thenReturn(parameterJson);
             })) {

      mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);

      ParameterBuilder parameterBuilder = new ParameterBuilder(mockParameter);
      JSONObject result = parameterBuilder.toJSON();

      assertNotNull(result);
      assertFalse(result.has(SELECTOR));
      assertFalse(result.has(REF_LIST));
      assertFalse(result.has(WINDOW));
    }
  }
}
