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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.application.window.OBViewTab;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;

/**
 * Unit tests for {@link TabPrintConfig}, which mirrors the Classic Print / Email toolbar rules of
 * {@code OBViewTab.getIconButtons} and {@code OBViewTab.PrintButton}.
 */
@ExtendWith(MockitoExtension.class)
class TabPrintConfigTest {

  private static final String ORDERS_URL = "../orders/print.html";
  private static final String BPARTNER_URL = "../RptC_Bpartner.html";
  private static final String BPARTNER_KEY_COLUMN = "C_BPartner_ID";
  private static final String BPARTNER_KEY_PARAM = "inpcBpartnerId";

  /**
   * Provides Classic print URLs with the Email and legacy flags expected for each one.
   *
   * @return the (url, expected hasEmail, expected legacy) triples
   */
  static Stream<Arguments> printUrlCases() {
    return Stream.of(
        Arguments.of(ORDERS_URL, true, false),
        Arguments.of("../invoices/print.html", true, false),
        Arguments.of("../payments/print.html", true, false),
        Arguments.of("../shipments/print.html", false, false),
        Arguments.of(BPARTNER_URL, false, true),
        Arguments.of("../RptC_Remittance.pdf", false, true),
        Arguments.of("../ReportPaymentFilter.html", false, true));
  }

  /**
   * Tests that a tab without a print process has no print configuration and that the key column
   * is not resolved for it.
   *
   * @throws JSONException when the configuration cannot be written
   */
  @Test
  void toJSONReturnsNullWithoutPrintProcess() throws JSONException {
    Tab tab = mock(Tab.class);
    @SuppressWarnings("unchecked")
    Supplier<Column> keyColumn = mock(Supplier.class);

    assertNull(TabPrintConfig.toJSON(tab, keyColumn));
    verify(keyColumn, never()).get();
  }

  /**
   * Tests that the Email and legacy flags follow the Classic print URL, never the process name.
   *
   * @param url              the print URL returned by {@code OBViewTab.getPrintUrl}
   * @param expectedHasEmail whether the Email button is expected
   * @param expectedLegacy   whether printing is expected to be delegated to Classic
   * @throws JSONException when the configuration cannot be read
   */
  @ParameterizedTest
  @MethodSource("printUrlCases")
  void toJSONResolvesFlagsFromPrintUrl(String url, boolean expectedHasEmail, boolean expectedLegacy)
      throws JSONException {
    JSONObject config = buildConfig(url, null, mockKeyColumn());

    assertEquals(url, config.getString(TabPrintConfig.URL));
    assertEquals(expectedHasEmail, config.getBoolean(TabPrintConfig.HAS_EMAIL));
    assertEquals(expectedLegacy, config.getBoolean(TabPrintConfig.LEGACY));
  }

  /**
   * Tests that the key parameter is the Classic {@code inpKeyName} of the table key column.
   *
   * @throws JSONException when the configuration cannot be read
   */
  @Test
  void toJSONEmitsClassicKeyParam() throws JSONException {
    JSONObject config = buildConfig(BPARTNER_URL, null, mockKeyColumn());

    assertEquals(BPARTNER_KEY_PARAM, config.getString(TabPrintConfig.KEY_PARAM));
  }

  /**
   * Tests that the key parameter is JSON null when the table has no key column.
   *
   * @throws JSONException when the configuration cannot be read
   */
  @Test
  void toJSONEmitsNullKeyParamWithoutKeyColumn() throws JSONException {
    JSONObject config = buildConfig(BPARTNER_URL, null, null);

    assertTrue(config.isNull(TabPrintConfig.KEY_PARAM));
  }

  /**
   * Tests that the direct print flag mirrors {@code Process.isDirectPrint}, treating
   * {@code null} as false.
   *
   * @throws JSONException when the configuration cannot be read
   */
  @Test
  void toJSONMirrorsDirectPrintFlag() throws JSONException {
    assertTrue(buildConfig(ORDERS_URL, Boolean.TRUE, null).getBoolean(TabPrintConfig.DIRECT_PRINT));
    assertFalse(buildConfig(ORDERS_URL, Boolean.FALSE, null).getBoolean(TabPrintConfig.DIRECT_PRINT));
    assertFalse(buildConfig(ORDERS_URL, null, null).getBoolean(TabPrintConfig.DIRECT_PRINT));
  }

  /**
   * Builds the configuration of a tab with a print process whose Classic URL is {@code url}.
   *
   * @param url         the print URL returned by {@code OBViewTab.getPrintUrl}
   * @param directPrint the value returned by {@code Process.isDirectPrint}
   * @param keyColumn   the key column of the tab table, or {@code null}
   * @return the print configuration
   * @throws JSONException when the configuration cannot be written
   */
  private JSONObject buildConfig(String url, Boolean directPrint, Column keyColumn) throws JSONException {
    Tab tab = mock(Tab.class);
    Process process = mock(Process.class);
    when(tab.getProcess()).thenReturn(process);
    when(process.isDirectPrint()).thenReturn(directPrint);
    try (MockedStatic<OBViewTab> obViewTab = mockStatic(OBViewTab.class)) {
      obViewTab.when(() -> OBViewTab.getPrintUrl(tab)).thenReturn(url);
      return TabPrintConfig.toJSON(tab, () -> keyColumn);
    }
  }

  private Column mockKeyColumn() {
    Column column = mock(Column.class);
    when(column.getDBColumnName()).thenReturn(BPARTNER_KEY_COLUMN);
    return column;
  }
}
