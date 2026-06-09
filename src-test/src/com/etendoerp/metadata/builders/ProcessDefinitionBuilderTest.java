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
 * The builder emits the process-level JS-hook columns ({@code etmetaOnload},
 * {@code etmetaOnprocess}, {@code etmetaOnRefresh}, {@code etmetaPayscriptLogic}
 * and {@code etmetaCustomComponent}) <em>explicitly from the entity getters</em>,
 * not from the {@link DataToJsonConverter} output. This guarantees the keys are
 * present regardless of the role's derived-read access to {@code OBUIAPP_Process}
 * (the converter skips non-derived-readable properties for business roles). The
 * builder also drops the converter's legacy-cased raw keys ({@code eTMETAOnload} /
 * {@code eTMETACustomComponent}). The custom-component flag is exercised in
 * {@link ProcessDefinitionBuilderCustomComponentTest}.
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderTest {

  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String PROCESS_NAME_VALUE = "Test Process";
  private static final String SCRIPT_ONLOAD = "onLoadScript";
  private static final String SCRIPT_ONPROCESS = "onProcessScript";
  private static final String SCRIPT_ONREFRESH = "onRefreshScript";
  private static final String SCRIPT_PAYSCRIPT = "payScriptLogic";
  private static final String PARAM1_ID_VALUE = "param1Id";
  private static final String PARAM2_ID_VALUE = "param2Id";

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
   * Stubs the four string-valued process-level hook getters on {@link #mockProcess}
   * with the canonical script constants, so a test can assert each hook is published
   * under its public key from the entity.
   */
  private void stubProcessHookGetters() {
    when(mockProcess.getETMETAOnload()).thenReturn(SCRIPT_ONLOAD);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(SCRIPT_ONPROCESS);
    when(mockProcess.getEtmetaOnRefresh()).thenReturn(SCRIPT_ONREFRESH);
    when(mockProcess.getEtmetaPayscriptLogic()).thenReturn(SCRIPT_PAYSCRIPT);
  }

  /**
   * Asserts the four string-valued process-level hooks hold their canonical script
   * values in the resulting payload.
   *
   * @param result the JSON produced by the builder
   * @throws JSONException if reading the resulting JSON object fails
   */
  private static void assertProcessHookValues(JSONObject result) throws JSONException {
    assertEquals(SCRIPT_ONLOAD, result.getString(ETMETA_ONLOAD));
    assertEquals(SCRIPT_ONPROCESS, result.getString(ETMETA_ONPROCESS));
    assertEquals(SCRIPT_ONREFRESH, result.getString(ETMETA_ON_REFRESH));
    assertEquals(SCRIPT_PAYSCRIPT, result.getString(ETMETA_PAYSCRIPT_LOGIC));
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
   * Tests that the constructor of ProcessDefinitionBuilder initializes correctly with a mock Process.
   */
  @Test
  void testConstructorSuccess() {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    assertNotNull(builder);
  }

  /**
   * Happy path: parameters are nested under {@code parameters}, each process-level
   * hook is published under its public key from the entity getter, the typo'd raw
   * key is absent, and the legacy {@code onLoad}/{@code onProcess} keys are not added.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONWithParametersAndScripts() throws JSONException {
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(NAME, PROCESS_NAME_VALUE);

    stubProcessHookGetters();

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
      assertEquals(PROCESS_NAME_VALUE, result.getString(NAME));

      // Hooks are emitted from the entity getters; the typo'd raw key never survives.
      assertProcessHookValues(result);
      assertNull(result.opt(ETMETA_ONLOAD_TYPO));

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
   * Tests an empty parameter list: the parameters JSON is empty, the legacy and
   * typo'd keys never appear, and the always-present hook keys are emitted (null
   * here, since the entity getters return null).
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

    // Legacy keys are never added; the typo'd raw key never survives.
    assertNull(result.opt(ON_LOAD));
    assertNull(result.opt(ON_PROCESS));
    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
    // The hook key is still present (null) even when the column is empty.
    assertPresentAndNull(result, ETMETA_ONLOAD);
  }

  /**
   * Regression for the derived-read gate: a business role's converter omits the
   * non-derived-readable {@code em_etmeta_*} properties entirely (and may leave a
   * stale value under the typo'd raw key). The builder must still publish every
   * hook from the entity getters and drop the raw key.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONEmitsHooksFromEntityWhenConverterOmitsThem() throws JSONException {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());
    stubProcessHookGetters();

    // Converter returns no etmeta keys (gate), except a stale value under the raw key.
    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);
    converterJson.put(ETMETA_ONLOAD_TYPO, "staleConverterValue");

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
    assertProcessHookValues(result);
  }

  /**
   * Locks the stable null-vs-absent contract for the four string-valued process-level
   * {@code etmeta*} keys: when every column is empty the builder keeps all four keys
   * present (never absent) with {@link JSONObject#NULL}. A downstream FE consumer can
   * therefore rely on the keys always existing and only test for {@code null}.
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONKeepsAllProcessEtmetaKeysPresentWhenColumnsEmpty() throws JSONException {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);

    JSONObject result = newBuilderWithConverterReturning(converterJson).toJSON();

    assertNotNull(result);
    // The typo'd raw key must not survive.
    assertNull(result.opt(ETMETA_ONLOAD_TYPO));
    // All four string-valued process-level hooks remain present and null.
    assertPresentAndNull(result, ETMETA_ONLOAD);
    assertPresentAndNull(result, ETMETA_ONPROCESS);
    assertPresentAndNull(result, ETMETA_ON_REFRESH);
    assertPresentAndNull(result, ETMETA_PAYSCRIPT_LOGIC);
  }

  /**
   * Verifies the builder does not re-introduce the legacy {@code onLoad} or
   * {@code onProcess} keys (which were previously added explicitly).
   *
   * @throws JSONException if there is an error creating or manipulating JSON objects
   */
  @Test
  void testToJSONDropsLegacyKeys() throws JSONException {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());

    JSONObject converterJson = new JSONObject();
    converterJson.put(ID, TEST_PROCESS_ID);

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
