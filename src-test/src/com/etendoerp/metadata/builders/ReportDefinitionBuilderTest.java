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

import static com.etendoerp.metadata.MetadataTestConstants.TEST_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.client.application.ReportDefinition;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Tests for {@link ReportDefinitionBuilder}, which publishes the export flags an OBUIAPP_Report
 * offers so the new UI can render one button per available format.
 * <p>
 * Each flag is true when its own template is filled <em>or</em> when the report reuses the PDF
 * template for that format, which is the rule the classic ParameterWindowComponent applies.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ReportDefinitionBuilderTest {

    private static final String ID_KEY = "id";
    private static final String PDF_EXPORT_KEY = "pdfExport";
    private static final String XLS_EXPORT_KEY = "xlsExport";
    private static final String HTML_EXPORT_KEY = "htmlExport";
    private static final String TEMPLATE_PATH = "@basedesign@/org/openbravo/report.jrxml";

    @Mock
    private ReportDefinition report;

    @Mock
    private OBContext obContext;

    @Mock
    private Language language;

    @BeforeEach
    void setUp() {
        when(report.getId()).thenReturn(TEST_ID);
    }

    /**
     * Builds the JSON with the AD singletons stubbed: {@link Builder} reads the context language
     * and creates a converter while being constructed, neither of which this builder uses.
     *
     * @return the export flags JSON
     * @throws JSONException if the JSON cannot be assembled
     */
    private JSONObject toJSON() throws JSONException {
        try (MockedStatic<OBContext> mockedContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {
            mockedContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            return new ReportDefinitionBuilder(report).toJSON();
        }
    }

    /**
     * A report with no template at all offers no export, and still publishes its id.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void reportWithoutTemplatesOffersNoExport() throws JSONException {
        JSONObject result = toJSON();

        assertEquals(TEST_ID, result.getString(ID_KEY));
        assertFalse(result.getBoolean(PDF_EXPORT_KEY));
        assertFalse(result.getBoolean(XLS_EXPORT_KEY));
        assertFalse(result.getBoolean(HTML_EXPORT_KEY));
    }

    /**
     * An empty template string is not a template: the flags stay false.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void emptyTemplatesOfferNoExport() throws JSONException {
        when(report.getPDFTemplate()).thenReturn("");
        when(report.getXLSTemplate()).thenReturn("");
        when(report.getHTMLTemplate()).thenReturn("");

        JSONObject result = toJSON();

        assertFalse(result.getBoolean(PDF_EXPORT_KEY));
        assertFalse(result.getBoolean(XLS_EXPORT_KEY));
        assertFalse(result.getBoolean(HTML_EXPORT_KEY));
    }

    /**
     * A PDF template enables the PDF export only; it does not imply the other two.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void pdfTemplateEnablesPdfExportAlone() throws JSONException {
        when(report.getPDFTemplate()).thenReturn(TEMPLATE_PATH);

        JSONObject result = toJSON();

        assertTrue(result.getBoolean(PDF_EXPORT_KEY));
        assertFalse(result.getBoolean(XLS_EXPORT_KEY));
        assertFalse(result.getBoolean(HTML_EXPORT_KEY));
    }

    /**
     * An XLS template of its own enables the XLS export.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void xlsTemplateEnablesXlsExport() throws JSONException {
        when(report.getXLSTemplate()).thenReturn(TEMPLATE_PATH);

        JSONObject result = toJSON();

        assertTrue(result.getBoolean(XLS_EXPORT_KEY));
    }

    /**
     * Reusing the PDF template for XLS enables the XLS export even with no XLS template of its own.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void reusingThePdfTemplateEnablesXlsExport() throws JSONException {
        when(report.getXLSTemplate()).thenReturn(null);
        when(report.isUsePDFAsXLSTemplate()).thenReturn(Boolean.TRUE);

        JSONObject result = toJSON();

        assertTrue(result.getBoolean(XLS_EXPORT_KEY));
    }

    /**
     * An HTML template of its own enables the HTML export.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void htmlTemplateEnablesHtmlExport() throws JSONException {
        when(report.getHTMLTemplate()).thenReturn(TEMPLATE_PATH);

        JSONObject result = toJSON();

        assertTrue(result.getBoolean(HTML_EXPORT_KEY));
    }

    /**
     * Reusing the PDF template for HTML enables the HTML export even with no HTML template.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void reusingThePdfTemplateEnablesHtmlExport() throws JSONException {
        when(report.getHTMLTemplate()).thenReturn(null);
        when(report.isUsePDFAsHTMLTemplate()).thenReturn(Boolean.TRUE);

        JSONObject result = toJSON();

        assertTrue(result.getBoolean(HTML_EXPORT_KEY));
    }

    /**
     * The reuse flags are nullable Booleans in the model, so an unset one must read as false rather
     * than raising a null unboxing error.
     *
     * @throws JSONException if the JSON cannot be assembled
     */
    @Test
    void unsetReuseFlagsAreTreatedAsFalse() throws JSONException {
        when(report.isUsePDFAsXLSTemplate()).thenReturn(null);
        when(report.isUsePDFAsHTMLTemplate()).thenReturn(null);

        JSONObject result = toJSON();

        assertFalse(result.getBoolean(XLS_EXPORT_KEY));
        assertFalse(result.getBoolean(HTML_EXPORT_KEY));
    }

    /** The builder takes part in the shared Builder hierarchy the metadata services dispatch on. */
    @Test
    void isABuilder() {
        try (MockedStatic<OBContext> mockedContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {
            mockedContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            assertInstanceOf(Builder.class, new ReportDefinitionBuilder(report));
        }
    }
}
