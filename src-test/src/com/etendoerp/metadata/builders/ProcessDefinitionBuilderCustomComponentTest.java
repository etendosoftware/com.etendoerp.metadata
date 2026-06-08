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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
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
 * Verifies that ProcessDefinitionBuilder.toJSON() exposes the custom-component flag
 * (em_etmeta_custom_component) under the normalized JSON key {@code etmetaCustomComponent}.
 *
 * The flag is serialized automatically by the FULL_TRANSLATABLE converter, but its
 * property name may be emitted with the legacy ETMETA casing ({@code eTMETACustomComponent}),
 * exactly like {@code eTMETAOnload}. These tests confirm the builder normalizes that case
 * while leaving the already-correct key and the absent case untouched.
 *
 * Run manually: ant test -Dtest.case=ProcessDefinitionBuilderCustomComponentTest
 */
@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionBuilderCustomComponentTest extends ProcessDefinitionBuilderTestSupport {

  private static final String ETMETA_CUSTOM_COMPONENT = "etmetaCustomComponent";
  private static final String ETMETA_CUSTOM_COMPONENT_RAW = "eTMETACustomComponent";

  @BeforeEach
  void stubProcessDefaults() {
    when(mockProcess.getOBUIAPPParameterList()).thenReturn(new ArrayList<>());
  }

  /**
   * Builds the SUT and wires the supplied converter output as the return value of
   * the FULL_TRANSLATABLE conversion.
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
   * Case 1: the converter emits the flag with the legacy ETMETA casing.
   * toJSON() must rename it to {@code etmetaCustomComponent} and drop the raw key.
   *
   * @throws JSONException if reading the resulting JSON object fails.
   */
  @Test
  void testLegacyKeyIsNormalized() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);
    converterOutput.put(ETMETA_CUSTOM_COMPONENT_RAW, true);

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertFalse(result.has(ETMETA_CUSTOM_COMPONENT_RAW), "Raw ETMETA-cased key must be removed");
    assertTrue(result.getBoolean(ETMETA_CUSTOM_COMPONENT), "Normalized flag must be true");
  }

  /**
   * Case 2: the converter already emits the correct key. It must be preserved as-is.
   *
   * @throws JSONException if reading the resulting JSON object fails.
   */
  @Test
  void testCorrectKeyIsPreserved() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);
    converterOutput.put(ETMETA_CUSTOM_COMPONENT, true);

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertTrue(result.getBoolean(ETMETA_CUSTOM_COMPONENT), "Existing flag must be preserved");
  }

  /**
   * Case 3: neither key is present (process without the flag). The builder must not
   * inject {@code etmetaCustomComponent}.
   *
   * @throws JSONException if reading the resulting JSON object fails.
   */
  @Test
  void testAbsentFlagIsNotInjected() throws JSONException {
    JSONObject converterOutput = new JSONObject();
    converterOutput.put("id", TEST_PROCESS_ID);

    JSONObject result = builderReturning(converterOutput).toJSON();

    assertFalse(result.has(ETMETA_CUSTOM_COMPONENT), "Builder must not inject the flag when absent");
  }
}
