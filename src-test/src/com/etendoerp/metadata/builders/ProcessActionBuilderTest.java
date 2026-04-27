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

import static com.etendoerp.metadata.MetadataTestConstants.PARAMETERS;
import static com.etendoerp.metadata.MetadataTestConstants.REF_LIST;
import static com.etendoerp.metadata.MetadataTestConstants.SELECTOR;
import static com.etendoerp.metadata.MetadataTestConstants.SELECTOR_123;
import static com.etendoerp.metadata.MetadataTestConstants.SELECTOR_ID;
import static com.etendoerp.metadata.MetadataTestConstants.SELECTOR_PARAM_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROCESS;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROCESS_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Test class for ProcessActionBuilder.
 * This class tests the functionality of the ProcessActionBuilder, ensuring that it correctly builds
 * JSON representations of processes and their parameters, including handling of selector and list parameters.
 */
@ExtendWith(MockitoExtension.class)
class ProcessActionBuilderTest {

    private static final String DISPLAY_LOGIC_EXPRESSION = "displayLogicExpression";

    @Mock
    private Process mockProcess;
    @Mock
    private ProcessParameter mockParameter;
    @Mock
    private Reference mockReference;
    @Mock
    private Field mockField;
    @Mock
    private Language mockLanguage;
    @Mock
    private OBContext mockOBContext;
    @Mock
    private DataToJsonConverter mockConverter;

    private ProcessActionBuilder processActionBuilder;
    private JSONObject mockProcessJson;
    private JSONObject mockParameterJson;
    private Field field;
    private Column column;
    private Reference columnReference;
    private Tab tab;

    /**
     * Tests the buildParameterJSON method with a basic parameter that is neither a selector nor a list.
     * Verifies that the resulting JSON contains the expected fields and does not include selector or refList keys.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @BeforeEach
    void setUp() throws JSONException {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);

            processActionBuilder = new ProcessActionBuilder(mockProcess);
        }

        mockProcessJson = new JSONObject();
        mockProcessJson.put("id", TEST_PROCESS_ID);
        mockProcessJson.put("name", TEST_PROCESS);

        mockParameterJson = new JSONObject();
        mockParameterJson.put("id", "testParamId");
        mockParameterJson.put("name", "Test Parameter");

        field = mock(Field.class);
        column = mock(Column.class);
        columnReference = mock(Reference.class);
        tab = mock(Tab.class);

        try {
            java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
            converterField.setAccessible(true);
            converterField.set(processActionBuilder, mockConverter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void mockProcessJsonConversion(JSONObject processJson) throws JSONException {
        when(mockConverter.toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE)).thenReturn(processJson);
    }

    private void mockFieldProcessContext(String fieldId, String fieldName, String displayLogic,
        String columnId, String buttonText) {
        when(field.getId()).thenReturn(fieldId);
        when(field.getName()).thenReturn(fieldName);
        when(field.getDisplayLogic()).thenReturn(displayLogic);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);
        when(column.getId()).thenReturn(columnId);
        when(column.getName()).thenReturn(buttonText);
        when(column.getReference()).thenReturn(columnReference);
        when(columnReference.getId()).thenReturn("28");
        when(mockProcess.getADProcessParameterList()).thenReturn(Collections.emptyList());
    }

    private JSONObject getFieldProcess(String fieldId, String fieldName, String displayLogic,
        String columnId, String buttonText, String manualUrl,
        Consumer<DataToJsonConverter> converterConfigurer,
        Consumer<DynamicExpressionParser> parserConfigurer) throws JSONException {
        mockFieldProcessContext(fieldId, fieldName, displayLogic, columnId, buttonText);

        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<Utility> utilMock = mockStatic(Utility.class);
             MockedConstruction<DataToJsonConverter> ignoredConverter = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> {
                     when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject().put("id", "proc"));
                     if (converterConfigurer != null) {
                         converterConfigurer.accept(mock);
                     }
                 });
             MockedConstruction<DynamicExpressionParser> ignoredParser = parserConfigurer == null ? null
                 : mockConstruction(DynamicExpressionParser.class,
                     (mock, context) -> parserConfigurer.accept(mock))) {

            ctxMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            lenient().when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            utilMock.when(() -> Utility.getTabURL(eq(tab), any(), eq(false))).thenReturn(manualUrl);

            return ProcessActionBuilder.getFieldProcess(field, mockProcess);
        }
    }

    private JSONObject getFieldProcess(String fieldId, String fieldName, String displayLogic,
        String columnId, String buttonText, String manualUrl) throws JSONException {
        return getFieldProcess(fieldId, fieldName, displayLogic, columnId, buttonText, manualUrl, null, null);
    }

    private JSONObject getFieldProcessWithDisplayLogic(String fieldId, String fieldName, String displayLogic,
        String columnId, String buttonText, String manualUrl, Consumer<DynamicExpressionParser> parserConfigurer)
        throws JSONException {
        return getFieldProcess(fieldId, fieldName, displayLogic, columnId, buttonText, manualUrl, null,
            parserConfigurer);
    }

    private void mockProcessField(Field targetField, String fieldId, String fieldName, String displayLogic,
        Column targetColumn, String columnId, String buttonText, Reference targetReference, Tab targetTab) {
        when(targetField.getId()).thenReturn(fieldId);
        when(targetField.getName()).thenReturn(fieldName);
        when(targetField.getDisplayLogic()).thenReturn(displayLogic);
        when(targetField.getColumn()).thenReturn(targetColumn);
        when(targetField.getTab()).thenReturn(targetTab);
        when(targetColumn.getId()).thenReturn(columnId);
        when(targetColumn.getName()).thenReturn(buttonText);
        when(targetColumn.getReference()).thenReturn(targetReference);
        when(targetReference.getId()).thenReturn("28");
    }

    private void mockReferenceId(String referenceId) {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn(referenceId);
    }

    private void assertFieldProcessBasics(JSONObject result, String fieldId, String columnId,
        String buttonText, String fieldName, String manualUrl) throws JSONException {
        assertNotNull(result);
        assertEquals(fieldId, result.getString("fieldId"));
        assertEquals(columnId, result.getString("columnId"));
        assertEquals(buttonText, result.getString("buttonText"));
        assertEquals(fieldName, result.getString("fieldName"));
        assertEquals("28", result.getString("reference"));
        assertEquals(manualUrl, result.getString("manualURL"));
    }



    private void mockParameterJsonConversion() throws JSONException {
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
    }

    private void mockBasicParameter(String referenceId) throws JSONException {
        mockParameterJsonConversion();
        mockReferenceId(referenceId);
    }

    private void assertBaseParameterJson(JSONObject result) throws JSONException {
        assertNotNull(result);
        assertEquals("testParamId", result.getString("id"));
        assertEquals("Test Parameter", result.getString("name"));
    }

    private void mockProcessParameters(ProcessParameter... parameters) {
        when(mockProcess.getADProcessParameterList()).thenReturn(Arrays.asList(parameters));
    }

    private void assertParametersSize(JSONObject result, int expectedSize) throws JSONException {
        assertNotNull(result);
        assertTrue(result.has(PARAMETERS));
        assertEquals(expectedSize, result.getJSONArray(PARAMETERS).length());
    }

    private void assertProcessJsonBasics(JSONObject result) throws JSONException {
        assertNotNull(result);
        assertEquals(TEST_PROCESS_ID, result.getString("id"));
        assertEquals(TEST_PROCESS, result.getString("name"));
    }

    /**
     * Test the constructor of ProcessActionBuilder.
     * This test ensures that the builder can be instantiated correctly with a mock process.
     */
    @Test
    void testConstructor() {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            
            ProcessActionBuilder builder = new ProcessActionBuilder(mockProcess);
            assertNotNull(builder);
        }
    }

    /**
     * Test the isSelectorParameter method with various scenarios.
     * This test checks if the method correctly identifies selector parameters based on their references.
     */
    @ParameterizedTest
    @CsvSource({
            "95E2A8B50A254B2AAE6774B8C2F28120,true",
            "18,true",
            "12345,false"
    })
    void testIsSelectorParameterWithReference(String referenceId, boolean expected) {
        mockReferenceId(referenceId);

        boolean result = ProcessActionBuilder.isSelectorParameter(mockParameter);

        assertEquals(expected, result);
    }

    /**
     * Test the isSelectorParameter method with a null parameter.
     * This test checks if the method correctly handles a null parameter input.
     */
    @Test
    void testIsSelectorParameterWithNullParameter() {
        assertFalse(ProcessActionBuilder.isSelectorParameter(null));
    }

    /**
     * Test the isSelectorParameter method with a null reference.
     * This test checks if the method correctly handles a parameter with a null reference.
     */
    @Test
    void testIsSelectorParameterWithNullReference() {
        when(mockParameter.getReference()).thenReturn(null);

        boolean result = ProcessActionBuilder.isSelectorParameter(mockParameter);

        assertFalse(result);
    }

    /**
     * Test the isListParameter method with various scenarios.
     * This test checks if the method correctly identifies list parameters based on their references.
     */
    @ParameterizedTest
    @CsvSource({
            "17,true",
            "18,false"
    })
    void testIsListParameterWithReference(String referenceId, boolean expected) {
        mockReferenceId(referenceId);

        boolean result = ProcessActionBuilder.isListParameter(mockParameter);

        assertEquals(expected, result);
    }

    /**
     * Test the isListParameter method with a non-list reference.
     * This test checks if the method correctly identifies a parameter that is not a list.
     */
    @Test
    void testIsListParameterWithNullParameter() {
        assertFalse(ProcessActionBuilder.isListParameter(null));
    }

    /**
     * Test the isListParameter method with a null reference.
     * This test checks if the method correctly handles a parameter with a null reference.
     */
    @Test
    void testIsListParameterWithNullReference() {
        when(mockParameter.getReference()).thenReturn(null);

        boolean result = ProcessActionBuilder.isListParameter(mockParameter);

        assertFalse(result);
    }


    /**
     * Test the getFieldProcess method with a valid field and process.
     * This test ensures that the resulting JSON contains the expected field information.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testGetFieldProcessWithNullProcess() throws JSONException {
        JSONObject result = ProcessActionBuilder.getFieldProcess(mockField, null);

        assertNotNull(result);
        assertEquals(0, result.length());
    }

    /**
     * Verifies that {@code getFieldProcess} merges the legacy URL/command/key-column
     * keys returned by {@link LegacyProcessResolver} into the process JSON. This is the
     * contract the frontend consumes via {@code button.processAction.url/command/...}.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testGetFieldProcessIncludesLegacyParamsWhenResolverResolves() throws JSONException {
        Column mockColumn = mock(Column.class);
        Reference mockRef = mock(Reference.class);
        Tab mockTab = mock(Tab.class);
        when(mockField.getId()).thenReturn("field-id");
        when(mockField.getColumn()).thenReturn(mockColumn);
        when(mockField.getName()).thenReturn("Field Name");
        when(mockField.getTab()).thenReturn(mockTab);
        when(mockColumn.getId()).thenReturn("col-id");
        when(mockColumn.getName()).thenReturn("Column Name");
        when(mockColumn.getReference()).thenReturn(mockRef);
        when(mockRef.getId()).thenReturn("28");
        when(mockProcess.getADProcessParameterList()).thenReturn(Collections.emptyList());

        LegacyProcessParams legacyParams = new LegacyProcessParams(
                "/ad_process/RescheduleProcess.html",
                "DEFAULT",
                "AD_Process_Request_ID",
                "AD_Process_Request_ID");

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<LegacyProcessResolver> resolverMock = mockStatic(LegacyProcessResolver.class);
             MockedStatic<Utility> utilityMock = mockStatic(Utility.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                     DataToJsonConverter.class,
                     (mock, context) ->
                             when(mock.toJsonObject(any(Process.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                     .thenReturn(new JSONObject().put("id", TEST_PROCESS_ID)))) {

            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            resolverMock.when(() -> LegacyProcessResolver.resolve(mockField))
                    .thenReturn(Optional.of(legacyParams));
            utilityMock.when(() -> Utility.getTabURL(mockTab, null, false)).thenReturn("/tab/url.html");

            JSONObject result = ProcessActionBuilder.getFieldProcess(mockField, mockProcess);

            assertNotNull(result);
            assertEquals("/ad_process/RescheduleProcess.html", result.getString("url"));
            assertEquals("DEFAULT", result.getString("command"));
            assertEquals("AD_Process_Request_ID", result.getString("keyColumnName"));
            assertEquals("AD_Process_Request_ID", result.getString("inpkeyColumnId"));
        }
    }

    /**
     * Test the buildParameterJSON method with a basic parameter that is neither a selector nor a list.
     * This test ensures that the resulting JSON contains the expected fields and does not include selector or refList keys.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testBuildParameterJSONWithBasicParameter() throws JSONException {
        mockBasicParameter("10");

        JSONObject result = processActionBuilder.buildParameterJSON(mockParameter);

        assertBaseParameterJson(result);
        assertFalse(result.has(SELECTOR));
        assertFalse(result.has(REF_LIST));
    }

    /**
     * Test the buildParameterJSON method with a selector parameter.
     * This test ensures that the resulting JSON contains the expected selector information.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testBuildParameterJSONWithSelectorParameter() throws JSONException {
        mockBasicParameter("18");
        when(mockParameter.getReferenceSearchKey()).thenReturn(mockReference);
        when(mockParameter.getId()).thenReturn("paramId123");

        JSONObject mockSelectorInfo = new JSONObject();
        mockSelectorInfo.put(SELECTOR_ID, SELECTOR_123);

        try (MockedStatic<FieldBuilder> fieldBuilderMock = mockStatic(FieldBuilder.class)) {
            fieldBuilderMock.when(() -> FieldBuilder.getSelectorInfo("paramId123", mockReference))
                           .thenReturn(mockSelectorInfo);

            JSONObject result = processActionBuilder.buildParameterJSON(mockParameter);

            assertNotNull(result);
            assertTrue(result.has(SELECTOR));
            assertEquals(SELECTOR_123, result.getJSONObject(SELECTOR).getString(SELECTOR_ID));
        }
    }

    /**
     * Test the buildParameterJSON method with a parameter that has a list reference.
     * This test ensures that the resulting JSON contains the refList key with the expected values.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testBuildParameterJSONWithListParameter() throws JSONException {
        mockBasicParameter("17");
        when(mockParameter.getReferenceSearchKey()).thenReturn(mockReference);

        JSONArray mockRefList = new JSONArray();
        mockRefList.put(new JSONObject().put("value", "option1"));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<FieldBuilder> fieldBuilderMock = mockStatic(FieldBuilder.class)) {
            
            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            
            fieldBuilderMock.when(() -> FieldBuilder.getListInfo(mockReference, mockLanguage))
                           .thenReturn(mockRefList);

            JSONObject result = processActionBuilder.buildParameterJSON(mockParameter);

            assertNotNull(result);
            assertTrue(result.has(REF_LIST));
            assertEquals(1, result.getJSONArray(REF_LIST).length());
        }
    }

    /**
     * Test the toJSON method with a process that has no parameters.
     * This test ensures that the resulting JSON contains the expected fields and an empty parameters array.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithNoParameters() throws JSONException {
        mockProcessJsonConversion(mockProcessJson);
        when(mockProcess.getADProcessParameterList()).thenReturn(Collections.emptyList());

        JSONObject result = processActionBuilder.toJSON();

        assertProcessJsonBasics(result);
        assertParametersSize(result, 0);
    }

    /**
     * Test the toJSON method with a process that has parameters.
     * This test ensures that the resulting JSON contains the expected fields and parameters.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithParameters() throws JSONException {
        mockProcessJsonConversion(mockProcessJson);
        mockBasicParameter("10");
        mockProcessParameters(mockParameter);

        JSONObject result = processActionBuilder.toJSON();

        assertProcessJsonBasics(result);
        assertParametersSize(result, 1);
    }

    /**
     * Test the toJSON method with a process that has a null parameter.
     * This test ensures that the resulting JSON does not include the null parameter.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithNullParameter() throws JSONException {
        mockProcessJsonConversion(mockProcessJson);
        mockBasicParameter("10");
        when(mockProcess.getADProcessParameterList()).thenReturn(Arrays.asList(null, mockParameter));

        JSONObject result = processActionBuilder.toJSON();

        assertParametersSize(result, 1);
    }

    /**
     * Test the toJSON method with a process that has both selector and list parameters.
     * This test ensures that the resulting JSON contains both selector and refList keys.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithBothSelectorAndListParameters() throws JSONException {
        ProcessParameter selectorParam = mock(ProcessParameter.class);
        ProcessParameter listParam = mock(ProcessParameter.class);
        Reference selectorRef = mock(Reference.class);
        Reference listRef = mock(Reference.class);

        when(mockConverter.toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockProcessJson);
        when(mockProcess.getADProcessParameterList())
            .thenReturn(Arrays.asList(selectorParam, listParam));

        JSONObject selectorParamJson = new JSONObject();
        selectorParamJson.put("id", SELECTOR_PARAM_ID);
        when(mockConverter.toJsonObject(selectorParam, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(selectorParamJson);
        when(selectorParam.getReference()).thenReturn(selectorRef);
        when(selectorParam.getReferenceSearchKey()).thenReturn(selectorRef);
        when(selectorParam.getId()).thenReturn(SELECTOR_PARAM_ID);
        when(selectorRef.getId()).thenReturn("18");

        JSONObject listParamJson = new JSONObject();
        listParamJson.put("id", "listParamId");
        when(mockConverter.toJsonObject(listParam, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(listParamJson);
        when(listParam.getReference()).thenReturn(listRef);
        when(listParam.getReferenceSearchKey()).thenReturn(listRef);
        when(listRef.getId()).thenReturn("17");

        JSONObject mockSelectorInfo = new JSONObject();
        mockSelectorInfo.put(SELECTOR_ID, SELECTOR_123);
        JSONArray mockRefList = new JSONArray();
        mockRefList.put(new JSONObject().put("value", "option1"));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<FieldBuilder> fieldBuilderMock = mockStatic(FieldBuilder.class)) {
            
            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            
            fieldBuilderMock.when(() -> FieldBuilder.getSelectorInfo(SELECTOR_PARAM_ID, selectorRef))
                           .thenReturn(mockSelectorInfo);
            fieldBuilderMock.when(() -> FieldBuilder.getListInfo(listRef, mockLanguage))
                           .thenReturn(mockRefList);

            JSONObject result = processActionBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has(PARAMETERS));
            assertEquals(2, result.getJSONArray(PARAMETERS).length());

            JSONArray parameters = result.getJSONArray(PARAMETERS);
            boolean foundSelector = false;
            boolean foundList = false;
            
            for (int i = 0; i < parameters.length(); i++) {
                JSONObject param = parameters.getJSONObject(i);
                if (param.has(SELECTOR)) {
                    foundSelector = true;
                }
                if (param.has(REF_LIST)) {
                    foundList = true;
                }
            }
            
            assertTrue(foundSelector);
            assertTrue(foundList);
        }
    }

    /**
     * Tests getFieldProcess with a valid (non-null) process.
     * This covers the main branch of getFieldProcess where it creates a ProcessActionBuilder,
     * calls toJSON, and enriches the result with field-related data.
     */
    @Test
    void testGetFieldProcessWithValidProcess() throws JSONException {
        Process process = mock(Process.class);
        when(process.getADProcessParameterList()).thenReturn(Collections.emptyList());
        mockProcessField(field, "field-1", "TestField", StringUtils.EMPTY, column, "col-1", "ButtonText",
            columnReference, tab);

        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<Utility> utilMock = mockStatic(Utility.class);
             MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                 (mock, context) -> when(mock.toJsonObject(any(), any())).thenReturn(new JSONObject().put("id", "proc-1")))) {

            ctxMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            lenient().when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            utilMock.when(() -> Utility.getTabURL(eq(tab), any(), eq(false))).thenReturn("/test/url");

            JSONObject result = ProcessActionBuilder.getFieldProcess(field, process);

            assertFieldProcessBasics(result, "field-1", "col-1", "ButtonText", "TestField", "/test/url");
        }
    }

    /**
     * Tests getFieldProcess with a non-blank display logic string.
     * This covers the display logic parsing branch where DynamicExpressionParser is invoked.
     */
    @Test
    void testGetFieldProcessWithDisplayLogic() throws JSONException {
        JSONObject result = getFieldProcessWithDisplayLogic("field-2", "TestField2", "@Processed@='Y'", "col-2",
            "Button2", "/test/url2",
            parser -> when(parser.getJSExpression()).thenReturn("OB.context.Processed === 'Y'"));

        assertFieldProcessBasics(result, "field-2", "col-2", "Button2", "TestField2", "/test/url2");
        assertTrue(result.has(DISPLAY_LOGIC_EXPRESSION));
        assertEquals("OB.context.Processed === 'Y'", result.getString(DISPLAY_LOGIC_EXPRESSION));
    }

    /**
     * Tests getFieldProcess when the DynamicExpressionParser throws an exception.
     * The code catches the exception silently, so the result should still be valid
     * but without a displayLogicExpression key.
     */
    @Test
    void testGetFieldProcessWithDisplayLogicParserException() throws JSONException {
        JSONObject result = getFieldProcessWithDisplayLogic("field-3", "TestField3", "@Invalid@", "col-3",
            "Button3", "/test/url3", parser -> when(parser.getJSExpression())
                .thenThrow(new RuntimeException("Parse error")));

        assertFieldProcessBasics(result, "field-3", "col-3", "Button3", "TestField3", "/test/url3");
        assertFalse(result.has(DISPLAY_LOGIC_EXPRESSION));
    }

    /**
     * Tests getFieldProcess with null displayLogic (covers the null check in the if-condition).
     */
    @Test
    void testGetFieldProcessWithNullDisplayLogic() throws JSONException {
        JSONObject result = getFieldProcess("field-4", "TestField4", null, "col-4", "Button4", "/test/url4");

        assertFieldProcessBasics(result, "field-4", "col-4", "Button4", "TestField4", "/test/url4");
        assertFalse(result.has(DISPLAY_LOGIC_EXPRESSION));
    }

}
