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

import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROCESS_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.ArrayList;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.openbravo.service.json.DataResolvingMode;

/**
 * Verifies that ProcessDefinitionBuilder.toJSON() preserves the Pick-and-Execute fields
 * (uiPattern and isMultiRecord) emitted by the FULL_TRANSLATABLE converter.
 *
 * These fields are serialized automatically because toJSON() calls
 * converter.toJsonObject(process, FULL_TRANSLATABLE), which serializes every column
 * of OBUIAPP_PROCESS — including uiPattern and isMultiRecord. No Java emission changes
 * are required; these tests confirm the contract holds.
 *
 * Run manually: ant test -Dtest.case=ProcessDefinitionBuilderPickAndExecuteTest
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderPickAndExecuteTest extends ProcessDefinitionBuilderTestSupport {

  private static final String UI_PATTERN = "uiPattern";
  private static final String IS_MULTI_RECORD = "isMultiRecord";
  private static final String PE_UI_PATTERN = "OBUIAPP_PickAndExecute";

  @BeforeEach
  void stubProcessDefaults() {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());
    when(mockProcess.getETMETAOnload()).thenReturn(null);
    when(mockProcess.getEtmetaOnprocess()).thenReturn(null);
  }

  /**
   * Builds the SUT and wires the supplied converter output as the return value of
   * the FULL_TRANSLATABLE conversion. Keeps each test focused on the assertions.
   *
   * @param converterOutput The JSON object the mocked converter must return.
   * @return The configured {@link ProcessDefinitionBuilder} ready to invoke.
   */
  private ProcessDefinitionBuilder builderReturning(JSONObject converterOutput) {
    ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder(mockProcess);
    injectConverter(builder);
    when(mockConverter.toJsonObject(eq(mockProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
        .thenReturn(converterOutput);
    return builder;
  }

  /**
   * Case 1: uiPattern = "OBUIAPP_PickAndExecute" and isMultiRecord = true.
   * The converter returns both fields; toJSON() must pass them through unchanged.
   */
  @Test
  void testPickAndExecuteMultiRecord() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);
    converterOutput.put(UI_PATTERN, PE_UI_PATTERN);
    converterOutput.put(IS_MULTI_RECORD, true);

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertEquals(PE_UI_PATTERN, result.getString(UI_PATTERN));
    assertTrue(result.getBoolean(IS_MULTI_RECORD));
  }

  /**
   * Case 2: uiPattern = "OBUIAPP_PickAndExecute" and isMultiRecord = false.
   * Single-record P&E (e.g. Modify Payment Plan). The false flag must not be dropped.
   */
  @Test
  void testPickAndExecuteSingleRecord() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);
    converterOutput.put(UI_PATTERN, PE_UI_PATTERN);
    converterOutput.put(IS_MULTI_RECORD, false);

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertEquals(PE_UI_PATTERN, result.getString(UI_PATTERN));
    assertFalse(result.getBoolean(IS_MULTI_RECORD));
  }

  /**
   * Case 3: No uiPattern in the converter output (older seed without explicit pattern).
   * The absence must be preserved — no default injection by the builder.
   * The client-side predicate `isPickAndExecute` falls back to Window Reference parameter
   * detection in this case.
   */
  @Test
  void testAbsentUiPatternIsNotInjected() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);
    // uiPattern is intentionally absent

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertFalse(result.has(UI_PATTERN), "Builder must not inject uiPattern when absent in converter output");
  }

  /**
   * Verifies that the converter is always called with FULL_TRANSLATABLE mode.
   * This mode guarantees that all OBUIAPP_PROCESS columns — including uiPattern and
   * isMultiRecord — are included in the serialized output without explicit mapping code.
   */
  @Test
  void testConverterCalledWithFullTranslatableMode() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);

    builderReturning(converterOutput).toJSON();

    verify(mockConverter).toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE);
  }
}
