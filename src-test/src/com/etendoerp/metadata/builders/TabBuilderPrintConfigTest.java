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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.application.window.OBViewTab;
import org.openbravo.model.ad.ui.Process;

/**
 * Unit tests for the {@code printConfig} key emitted by {@link TabBuilder}, which tells the new UI
 * where the Print and Email toolbar buttons are offered (see {@link TabPrintConfig}).
 */
@ExtendWith(MockitoExtension.class)
class TabBuilderPrintConfigTest extends TabBuilderTestBase {

  private static final String REMITTANCE_URL = "../RptC_Remittance.pdf";

  /**
   * Tests that {@code printConfig} is always emitted, as JSON null when the tab has no print
   * process.
   *
   * @throws Exception when mock setup fails
   */
  @Test
  void toJSONEmitsNullPrintConfigWithoutPrintProcess() throws Exception {
    buildTab(result -> assertTrue(result.isNull(TabPrintConfig.PRINT_CONFIG),
        "printConfig should be JSON null without a print process"));
  }

  /**
   * Tests that {@code printConfig} carries the Classic print URL when the tab has a print process.
   *
   * @throws Exception when mock setup fails
   */
  @Test
  void toJSONEmitsPrintConfigWithPrintProcess() throws Exception {
    TestContext ctx = setupTestContext();
    setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
    when(ctx.tab.getProcess()).thenReturn(mock(Process.class));

    try (MockedStatic<OBViewTab> obViewTab = mockStatic(OBViewTab.class)) {
      obViewTab.when(() -> OBViewTab.getPrintUrl(ctx.tab)).thenReturn(REMITTANCE_URL);
      executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), result -> {
        try {
          JSONObject printConfig = result.getJSONObject(TabPrintConfig.PRINT_CONFIG);
          assertEquals(REMITTANCE_URL, printConfig.getString(TabPrintConfig.URL));
          assertTrue(printConfig.getBoolean(TabPrintConfig.LEGACY), "Remittance print should be legacy");
        } catch (JSONException e) {
          fail("JSON exception: " + e.getMessage());
        }
      });
    }
  }

  /**
   * Builds the JSON of a basic tab without print process and runs the given assertions on it.
   *
   * @param assertions the assertions to run on the tab JSON
   * @throws Exception when mock setup fails
   */
  private void buildTab(Consumer<JSONObject> assertions) throws Exception {
    TestContext ctx = setupTestContext();
    setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
    executeTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), assertions);
  }
}
