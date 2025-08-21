package com.etendoerp.metadata.builders;

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
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

/**
 * Unit tests for ProcessDefinitionBuilder class - Fixed version that handles Openbravo dependencies
 */
@MockitoSettings (strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderTest {

  @Mock
  private Process mockProcess;

  @Mock
  private Parameter mockParameter1;

  @Mock
  private Parameter mockParameter2;

  @Mock
  private DataToJsonConverter mockConverter;

  @Mock
  private OBContext mockOBContext;

  @Mock
  private Language mockLanguage;

  private MockedStatic<OBContext> mockedOBContextStatic;
  private List<Parameter> parameterList;

  /**
   * Sets up the test environment before each test case.
   * Mocks static methods and initializes common mock behaviors.
   */
  @BeforeEach
  void setUp() {
    mockedOBContextStatic = mockStatic(OBContext.class);

    when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
    when(mockLanguage.getLanguage()).thenReturn("en_US");

    mockedOBContextStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

    parameterList = new ArrayList<>();
    parameterList.add(mockParameter1);
    parameterList.add(mockParameter2);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(parameterList);
    when(mockParameter1.getDBColumnName()).thenReturn("param1Column");
    when(mockParameter2.getDBColumnName()).thenReturn("param2Column");
    when(mockProcess.getEtmetaOnload()).thenReturn("onLoadScript");
    when(mockProcess.getEtmetaOnprocess()).thenReturn("onProcessScript");
  }

  /**
   * Cleans up the test environment after each test case.
   * Closes any mocked static methods to avoid side effects.
   */
  @AfterEach
  void tearDown() {
    if (mockedOBContextStatic != null) {
      mockedOBContextStatic.close();
    }
  }

  /**
   * Tests that the constructor of ProcessDefinitionBuilder initializes correctly with a mock Process.
   */
  @Test
  void testConstructorSuccess() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    assertNotNull(builder);
  }

  /**
   * Tests that the toJSON method correctly converts a Process with parameters and scripts to JSON.
   * Ensures that the resulting JSON contains the expected fields and values.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithParametersAndScripts() throws JSONException {
    JSONObject mockProcessJSON = new JSONObject();
    mockProcessJSON.put("id", "testProcessId");
    mockProcessJSON.put("name", "Test Process");

    JSONObject mockParamJSON1 = new JSONObject();
    mockParamJSON1.put("id", "param1Id");

    JSONObject mockParamJSON2 = new JSONObject();
    mockParamJSON2.put("id", "param2Id");

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> {
          when(mock.toJSON()).thenReturn(
              context.arguments().get(0) == mockParameter1 ? mockParamJSON1 : mockParamJSON2
          );
        })) {

      ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);

      try {
        java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
        converterField.setAccessible(true);
        converterField.set(builder, mockConverter);
      } catch (Exception e) {
        fail("Could not set converter field: " + e.getMessage());
      }

      when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
          .thenReturn(mockProcessJSON);

      JSONObject result = builder.toJSON();

      assertNotNull(result);
      assertEquals("testProcessId", result.getString("id"));
      assertEquals("Test Process", result.getString("name"));
      assertEquals("onLoadScript", result.getString("onLoad"));
      assertEquals("onProcessScript", result.getString("onProcess"));

      assertTrue(result.has("parameters"));
      JSONObject parameters = result.getJSONObject("parameters");
      assertTrue(parameters.has("param1Column"));
      assertTrue(parameters.has("param2Column"));

      assertEquals("param1Id", parameters.getJSONObject("param1Column").getString("id"));
      assertEquals("param2Id", parameters.getJSONObject("param2Column").getString("id"));
    }
  }

  /**
   * Tests that the toJSON method correctly handles an empty parameter list and null scripts.
   * Ensures that the resulting JSON contains an empty parameters object and null values for onLoad and onProcess.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithEmptyParameterList() throws JSONException {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());
    when(mockProcess.getEtmetaOnload()).thenReturn(null);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(null);

    JSONObject mockProcessJSON = new JSONObject();
    mockProcessJSON.put("id", "testProcessId");

    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);

    try {
      java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
      converterField.setAccessible(true);
      converterField.set(builder, mockConverter);
    } catch (Exception e) {
      fail("Could not set converter field: " + e.getMessage());
    }

    when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
        .thenReturn(mockProcessJSON);

    JSONObject result = builder.toJSON();

    assertNotNull(result);
    assertEquals("testProcessId", result.getString("id"));

    assertTrue(result.has("parameters"));
    JSONObject parameters = result.getJSONObject("parameters");
    assertEquals(0, parameters.length());

    assertNull(result.opt("onLoad"));
    assertNull(result.opt("onProcess"));
  }

  /**
   * Tests that the toJSON method correctly handles null scripts.
   * Ensures that the resulting JSON does not contain onLoad and onProcess fields.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithNullScripts() throws JSONException {
    when(mockProcess.getEtmetaOnload()).thenReturn(null);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(null);

    JSONObject mockProcessJSON = new JSONObject();
    mockProcessJSON.put("id", "testProcessId");

    JSONObject mockParamJSON = new JSONObject();
    mockParamJSON.put("id", "paramId");

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(mockParamJSON))) {

      ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);

      try {
        java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
        converterField.setAccessible(true);
        converterField.set(builder, mockConverter);
      } catch (Exception e) {
        fail("Could not set converter field: " + e.getMessage());
      }

      when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
          .thenReturn(mockProcessJSON);

      JSONObject result = builder.toJSON();

      assertNotNull(result);
      assertNull(result.opt("onLoad"));
      assertNull(result.opt("onProcess"));
    }
  }

  /**
   * Tests that the toJSON method throws a RuntimeException when the converter throws an exception.
   */
  @Test
  void testToJSONThrowsRuntimeException() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);

    try {
      java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
      converterField.setAccessible(true);
      converterField.set(builder, mockConverter);
    } catch (Exception e) {
      fail("Could not set converter field: " + e.getMessage());
    }

    when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
        .thenThrow(new RuntimeException("Test exception"));

    assertThrows(RuntimeException.class, builder::toJSON);
  }

  /**
   * Tests that ProcessDefinitionBuilder is a subclass of Builder.
   */
  @Test
  void testProcessBuilderInheritance() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    assertInstanceOf(Builder.class, builder);
  }

  /**
   * Tests that the toJSON method throws a NullPointerException when the converter is null.
   */
  @Test
  void testToJSONWithNullConverter() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);

    try {
      java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
      converterField.setAccessible(true);
      converterField.set(builder, null);
    } catch (Exception e) {
      fail("Could not set converter field: " + e.getMessage());
    }

    assertThrows(NullPointerException.class, builder::toJSON);
  }
}
