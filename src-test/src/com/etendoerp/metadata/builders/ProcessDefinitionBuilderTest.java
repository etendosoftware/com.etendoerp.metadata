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

import static com.etendoerp.metadata.MetadataTestConstants.CONVERTER;
import static com.etendoerp.metadata.MetadataTestConstants.COULD_NOT_SET_CONVERTER_FIELD;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_ONLOAD;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_ONLOAD_TYPO;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_ONPROCESS;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_ON_REFRESH;
import static com.etendoerp.metadata.MetadataTestConstants.ETMETA_PAYSCRIPT_LOGIC;
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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

/**
 * Unit tests for ProcessDefinitionBuilder.
 * <p>
 * The builder no longer puts {@code onLoad}/{@code onProcess} explicitly. The
 * onProcess, onRefresh and payscriptLogic hooks flow through the
 * {@link DataToJsonConverter} unchanged (their property names are already
 * correctly cased). The builder's only direct manipulation is renaming the
 * typo'd {@code eTMETAOnload} key emitted by the converter to {@code etmetaOnload}.
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderTest {

  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String SCRIPT_ONLOAD = "onLoadScript";
  private static final String SCRIPT_ONPROCESS = "onProcessScript";
  private static final String SCRIPT_ONREFRESH = "onRefreshScript";
  private static final String SCRIPT_PAYSCRIPT = "payScriptLogic";
  private static final String PARAM1_ID_VALUE = "param1Id";
  private static final String PARAM2_ID_VALUE = "param2Id";
  private static final String PARAM_ID_VALUE = "paramId";

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

  /**
   * Sets up the OBContext static mock (required by {@code Builder}'s constructor)
   * and wires the default two-parameter list on the shared {@code mockProcess}.
   * Individual tests override these stubs when they need empty lists.
   */
  @BeforeEach
  void setUp() {
    mockedOBContextStatic = mockStatic(OBContext.class);
    when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
    when(mockLanguage.getLanguage()).thenReturn("en_US");
    mockedOBContextStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

    List<Parameter> parameterList = new ArrayList<>();
    parameterList.add(mockParameter1);
    parameterList.add(mockParameter2);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(parameterList);
    when(mockParameter1.getDBColumnName()).thenReturn(PARAM1_COLUMN);
    when(mockParameter2.getDBColumnName()).thenReturn(PARAM2_COLUMN);
  }

  /**
   * Closes the OBContext static mock to avoid leaking it across tests.
   */
  @AfterEach
  void tearDown() {
    if (mockedOBContextStatic != null) {
      mockedOBContextStatic.close();
    }
  }

  /**
   * Injects {@link #mockConverter} into the private {@code converter} field of
   * the supplied builder via reflection, failing the test if it cannot be set.
   */
  private void injectConverter(ProcessDefinitionBuilder builder) {
    injectConverter(builder, mockConverter);
  }

  /**
   * Injects an arbitrary converter (including {@code null}) into the private
   * {@code converter} field of the supplied builder.
   */
  private void injectConverter(ProcessDefinitionBuilder builder, DataToJsonConverter converter) {
    try {
      Field converterField = Builder.class.getDeclaredField(CONVERTER);
      converterField.setAccessible(true);
      converterField.set(builder, converter);
    } catch (Exception e) {
      fail(COULD_NOT_SET_CONVERTER_FIELD + e.getMessage());
    }
  }

  /**
   * Builds a {@link ProcessDefinitionBuilder} with {@link #mockConverter}
   * injected and stubbed to return the supplied JSON for the Process under
   * test. Centralizes the repeated wiring used by every test.
   */
  private ProcessDefinitionBuilder newBuilderWithConverterReturning(JSONObject converterJson) {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    injectConverter(builder);
    when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
        .thenReturn(converterJson);
    return builder;
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
   * Tests the happy path: parameters are nested under {@code parameters}, the
   * converter-emitted {@code eTMETAOnload} key is renamed to {@code etmetaOnload},
   * and the other etmeta hooks pass through unchanged. Also verifies the legacy
   * {@code onLoad}/{@code onProcess} keys are not added by the builder.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithParametersAndScripts() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(NAME, "Test Process");
    converterJson.put(ETMETA_ONLOAD_TYPO, SCRIPT_ONLOAD);
    converterJson.put(ETMETA_ONPROCESS, SCRIPT_ONPROCESS);
    converterJson.put(ETMETA_ON_REFRESH, SCRIPT_ONREFRESH);
    converterJson.put(ETMETA_PAYSCRIPT_LOGIC, SCRIPT_PAYSCRIPT);

    JSONObject mockParamJSON1 = new JSONObject();
    mockParamJSON1.put(ID, PARAM1_ID_VALUE);
    JSONObject mockParamJSON2 = new JSONObject();
    mockParamJSON2.put(ID, PARAM2_ID_VALUE);

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(
            context.arguments().get(0) == mockParameter1 ? mockParamJSON1 : mockParamJSON2
        ))) {

      JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

      assertNotNull(result);
      assertEquals(TEST_PROCESS_ID, result.getString(ID));
      assertEquals("Test Process", result.getString(NAME));

      // Rename happens: etmetaOnload present, eTMETAOnload gone.
      assertEquals(SCRIPT_ONLOAD, result.getString(ETMETA_ONLOAD));
      assertNull(result.opt(ETMETA_ONLOAD_TYPO));

      // Other hooks pass through unchanged.
      assertEquals(SCRIPT_ONPROCESS, result.getString(ETMETA_ONPROCESS));
      assertEquals(SCRIPT_ONREFRESH, result.getString(ETMETA_ON_REFRESH));
      assertEquals(SCRIPT_PAYSCRIPT, result.getString(ETMETA_PAYSCRIPT_LOGIC));

      // Legacy duplicate keys are not added.
      assertNull(result.opt(ON_LOAD));
      assertNull(result.opt(ON_PROCESS));

      assertTrue(result.has(PARAMETERS));
      JSONObject parameters = result.getJSONObject(PARAMETERS);
      assertTrue(parameters.has(PARAM1_COLUMN));
      assertTrue(parameters.has(PARAM2_COLUMN));
      assertEquals(PARAM1_ID_VALUE, parameters.getJSONObject(PARAM1_COLUMN).getString(ID));
      assertEquals(PARAM2_ID_VALUE, parameters.getJSONObject(PARAM2_COLUMN).getString(ID));
    }
  }

  /**
   * Tests an empty parameter list with no etmeta scripts emitted by the converter:
   * the parameters JSON is empty and neither legacy nor etmeta keys appear.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithEmptyParameterList() throws JSONException {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNotNull(result);
    assertEquals(TEST_PROCESS_ID, result.getString(ID));

    assertTrue(result.has(PARAMETERS));
    assertEquals(0, result.getJSONObject(PARAMETERS).length());

    // No script keys (legacy or new) — converter returned nothing for them.
    assertNull(result.opt(ON_LOAD));
    assertNull(result.opt(ON_PROCESS));
    assertNull(result.opt(ETMETA_ONLOAD));
    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
  }

  /**
   * Tests that {@link JSONObject#NULL} emitted by the converter under the
   * typo'd key is preserved under the renamed key after the builder runs.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONPreservesNullThroughRename() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(ETMETA_ONLOAD_TYPO, JSONObject.NULL);

    JSONObject mockParamJSON = new JSONObject();
    mockParamJSON.put(ID, PARAM_ID_VALUE);

    try (MockedConstruction<ParameterBuilder> ignored = mockConstruction(ParameterBuilder.class,
        (mock, context) -> when(mock.toJSON()).thenReturn(mockParamJSON))) {

      JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

      assertNotNull(result);
      assertNull(result.opt(ETMETA_ONLOAD_TYPO));
      // Renamed key exists and holds JSONObject.NULL.
      assertTrue(result.has(ETMETA_ONLOAD));
      assertEquals(JSONObject.NULL, result.opt(ETMETA_ONLOAD));
    }
  }

  /**
   * Asserts that {@code key} is present in {@code json} and holds {@link JSONObject#NULL}.
   * Encapsulates the always-present / JSON-null contract assertion so it is not
   * repeated (with its message strings) once per metadata key.
   *
   * @param json the JSON object under test
   * @param key  the metadata key that must always be present with a JSON-null value
   */
  private static void assertPresentAndNull(JSONObject json, String key) {
    assertTrue(json.has(key), key + " must always be present in the payload");
    assertEquals(JSONObject.NULL, json.opt(key), key + " must hold JSON null when the column is empty");
  }

  /**
   * Locks the §5.2 stable null-vs-absent contract for the four process-level
   * {@code etmeta*} keys: when every metadata column is empty the converter emits
   * each key with {@link JSONObject#NULL}, and the builder must keep all four keys
   * present (never absent) with their null value — including {@code etmetaOnload}
   * after the typo-key rename. A downstream FE consumer can therefore rely on the
   * keys always existing and only test for {@code null}.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONKeepsAllProcessEtmetaKeysPresentWhenColumnsEmpty() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(ETMETA_ONLOAD_TYPO, JSONObject.NULL);
    converterJson.put(ETMETA_ONPROCESS, JSONObject.NULL);
    converterJson.put(ETMETA_ON_REFRESH, JSONObject.NULL);
    converterJson.put(ETMETA_PAYSCRIPT_LOGIC, JSONObject.NULL);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNotNull(result);
    // The typo'd raw key must not survive the rename.
    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
    // All four process-level hooks remain present and null.
    assertPresentAndNull(result, ETMETA_ONLOAD);
    assertPresentAndNull(result, ETMETA_ONPROCESS);
    assertPresentAndNull(result, ETMETA_ON_REFRESH);
    assertPresentAndNull(result, ETMETA_PAYSCRIPT_LOGIC);
  }

  /**
   * Verifies the rename in isolation: when the converter emits {@code eTMETAOnload},
   * the result exposes {@code etmetaOnload} with the same value and the raw key
   * is removed.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONRenamesTypoToCamelCase() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ETMETA_ONLOAD_TYPO, SCRIPT_ONLOAD);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
    assertEquals(SCRIPT_ONLOAD, result.getString(ETMETA_ONLOAD));
  }

  /**
   * Verifies the builder does not re-introduce the legacy {@code onLoad} or
   * {@code onProcess} keys (which were previously added explicitly).
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONDropsLegacyKeys() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(ETMETA_ONLOAD_TYPO, SCRIPT_ONLOAD);
    converterJson.put(ETMETA_ONPROCESS, SCRIPT_ONPROCESS);

    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNull(result.opt(ON_LOAD));
    assertNull(result.opt(ON_PROCESS));
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
