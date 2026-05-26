/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.ON_LOAD;
import static com.etendoerp.metadata.MetadataTestConstants.ON_PROCESS;
import static com.etendoerp.metadata.MetadataTestConstants.PARAM1_COLUMN;
import static com.etendoerp.metadata.MetadataTestConstants.PARAM2_COLUMN;
import static com.etendoerp.metadata.MetadataTestConstants.PARAMETERS;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROCESS_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.openbravo.client.application.Parameter;
import org.openbravo.service.json.DataResolvingMode;

/**
 * Unit tests for ProcessDefinitionBuilder class - Fixed version that handles Openbravo dependencies
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderTest extends ProcessDefinitionBuilderTestSupport {

  @Mock
  private Parameter mockParameter1;

  @Mock
  private Parameter mockParameter2;

  /**
   * Wires the default two-parameter list and the standard onLoad/onProcess scripts
   * on the shared {@code mockProcess}. Individual tests override these stubs when
   * they need empty lists or null scripts.
   */
  @BeforeEach
  void stubProcessParametersAndScripts() {
    List<Parameter> parameterList = new ArrayList<>();
    parameterList.add(mockParameter1);
    parameterList.add(mockParameter2);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(parameterList);
    when(mockParameter1.getDBColumnName()).thenReturn(PARAM1_COLUMN);
    when(mockParameter2.getDBColumnName()).thenReturn(PARAM2_COLUMN);
    when(mockProcess.getETMETAOnload()).thenReturn("onLoadScript");
    when(mockProcess.getEtmetaOnprocess()).thenReturn("onProcessScript");
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
    mockProcessJSON.put("id", TEST_PROCESS_ID);
    mockProcessJSON.put("name", "Test Process");

    JSONObject mockParamJSON1 = new JSONObject();
    mockParamJSON1.put("id", "param1Id");

    JSONObject mockParamJSON2 = new JSONObject();
    mockParamJSON2.put("id", "param2Id");

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(
            context.arguments().get(0) == mockParameter1 ? mockParamJSON1 : mockParamJSON2
        ))) {

      ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
      injectConverter(builder);

      when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
          .thenReturn(mockProcessJSON);

      JSONObject result = builder.toJSON();

      assertNotNull(result);
      assertEquals(TEST_PROCESS_ID, result.getString("id"));
      assertEquals("Test Process", result.getString("name"));
      assertEquals("onLoadScript", result.getString(ON_LOAD));
      assertEquals("onProcessScript", result.getString(ON_PROCESS));

      assertTrue(result.has(PARAMETERS));
      JSONObject parameters = result.getJSONObject(PARAMETERS);
      assertTrue(parameters.has(PARAM1_COLUMN));
      assertTrue(parameters.has(PARAM2_COLUMN));

      assertEquals("param1Id", parameters.getJSONObject(PARAM1_COLUMN).getString("id"));
      assertEquals("param2Id", parameters.getJSONObject(PARAM2_COLUMN).getString("id"));
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
    when(mockProcess.getETMETAOnload()).thenReturn(null);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(null);

    JSONObject mockProcessJSON = new JSONObject();
    mockProcessJSON.put("id", TEST_PROCESS_ID);

    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    injectConverter(builder);

    when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
        .thenReturn(mockProcessJSON);

    JSONObject result = builder.toJSON();

    assertNotNull(result);
    assertEquals(TEST_PROCESS_ID, result.getString("id"));

    assertTrue(result.has(PARAMETERS));
    JSONObject parameters = result.getJSONObject(PARAMETERS);
    assertEquals(0, parameters.length());

    assertNull(result.opt(ON_LOAD));
    assertNull(result.opt(ON_PROCESS));
  }

  /**
   * Tests that the toJSON method correctly handles null scripts.
   * Ensures that the resulting JSON does not contain onLoad and onProcess fields.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithNullScripts() throws JSONException {
    when(mockProcess.getETMETAOnload()).thenReturn(null);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(null);

    JSONObject mockProcessJSON = new JSONObject();
    mockProcessJSON.put("id", TEST_PROCESS_ID);

    JSONObject mockParamJSON = new JSONObject();
    mockParamJSON.put("id", "paramId");

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(mockParamJSON))) {

      ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
      injectConverter(builder);

      when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
          .thenReturn(mockProcessJSON);

      JSONObject result = builder.toJSON();

      assertNotNull(result);
      assertNull(result.opt(ON_LOAD));
      assertNull(result.opt(ON_PROCESS));
    }
  }

  /**
   * Tests that the toJSON method throws a RuntimeException when the converter throws an exception.
   */
  @Test
  void testToJSONThrowsRuntimeException() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    injectConverter(builder);

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
    injectConverter(builder, null);

    assertThrows(NullPointerException.class, builder::toJSON);
  }
}
