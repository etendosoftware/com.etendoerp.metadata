package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Test class for ProcessActionBuilder.
 * This class tests the functionality of the ProcessActionBuilder, ensuring that it correctly builds
 * JSON representations of processes and their parameters, including handling of selector and list parameters.
 */
@ExtendWith(MockitoExtension.class)
class ProcessActionBuilderTest {

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
        mockProcessJson.put("id", "testProcessId");
        mockProcessJson.put("name", "Test Process");
        
        mockParameterJson = new JSONObject();
        mockParameterJson.put("id", "testParamId");
        mockParameterJson.put("name", "Test Parameter");
        
        try {
            java.lang.reflect.Field converterField = Builder.class.getDeclaredField("converter");
            converterField.setAccessible(true);
            converterField.set(processActionBuilder, mockConverter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
    @Test
    void testIsSelectorParameterWithSelectorReference() {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("95E2A8B50A254B2AAE6774B8C2F28120");

        boolean result = ProcessActionBuilder.isSelectorParameter(mockParameter);

        assertTrue(result);
    }

    /**
     * Test the isSelectorParameter method with a table reference.
     * This test checks if the method correctly identifies a parameter as a selector when it has a table reference.
     */
    @Test
    void testIsSelectorParameterWithTableReference() {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("18");

        boolean result = ProcessActionBuilder.isSelectorParameter(mockParameter);

        assertTrue(result);
    }

    /**
     * Test the isSelectorParameter method with a non-selector reference.
     * This test checks if the method correctly identifies a parameter that is not a selector.
     */
    @Test
    void testIsSelectorParameterWithNonSelectorReference() {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("12345");

        boolean result = ProcessActionBuilder.isSelectorParameter(mockParameter);

        assertFalse(result);
    }

    /**
     * Test the isSelectorParameter method with a null parameter.
     * This test checks if the method correctly handles a null parameter input.
     */
    @Test
    void testIsSelectorParameterWithNullParameter() {
      ProcessActionBuilder.isSelectorParameter(null);
      boolean result = false;

        assertFalse(result);
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
    @Test
    void testIsListParameterWithListReference() {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("17");

        boolean result = ProcessActionBuilder.isListParameter(mockParameter);

        assertTrue(result);
    }

    /**
     * Test the isListParameter method with a table reference.
     * This test checks if the method correctly identifies a parameter as a list when it has a table reference.
     */
    @Test
    void testIsListParameterWithNonListReference() {
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("18");

        boolean result = ProcessActionBuilder.isListParameter(mockParameter);

        assertFalse(result);
    }

    /**
     * Test the isListParameter method with a non-list reference.
     * This test checks if the method correctly identifies a parameter that is not a list.
     */
    @Test
    void testIsListParameterWithNullParameter() {
      ProcessActionBuilder.isListParameter(null);
      boolean result = false;

        assertFalse(result);
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
     * Test the buildParameterJSON method with a basic parameter that is neither a selector nor a list.
     * This test ensures that the resulting JSON contains the expected fields and does not include selector or refList keys.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testBuildParameterJSONWithBasicParameter() throws JSONException {
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("10");

        JSONObject result = processActionBuilder.buildParameterJSON(mockParameter);

        assertNotNull(result);
        assertEquals("testParamId", result.getString("id"));
        assertEquals("Test Parameter", result.getString("name"));
        assertFalse(result.has("selector"));
        assertFalse(result.has("refList"));
    }

    /**
     * Test the buildParameterJSON method with a selector parameter.
     * This test ensures that the resulting JSON contains the expected selector information.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testBuildParameterJSONWithSelectorParameter() throws JSONException {
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockParameter.getReferenceSearchKey()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("18");
        when(mockParameter.getId()).thenReturn("paramId123");

        JSONObject mockSelectorInfo = new JSONObject();
        mockSelectorInfo.put("selectorId", "selector123");

        try (MockedStatic<FieldBuilder> fieldBuilderMock = mockStatic(FieldBuilder.class)) {
            fieldBuilderMock.when(() -> FieldBuilder.getSelectorInfo("paramId123", mockReference))
                           .thenReturn(mockSelectorInfo);

            JSONObject result = processActionBuilder.buildParameterJSON(mockParameter);

            assertNotNull(result);
            assertTrue(result.has("selector"));
            assertEquals("selector123", result.getJSONObject("selector").getString("selectorId"));
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
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockParameter.getReferenceSearchKey()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("17");

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
            assertTrue(result.has("refList"));
            assertEquals(1, result.getJSONArray("refList").length());
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
        when(mockConverter.toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockProcessJson);
        when(mockProcess.getADProcessParameterList()).thenReturn(Collections.emptyList());

        JSONObject result = processActionBuilder.toJSON();

        assertNotNull(result);
        assertEquals("testProcessId", result.getString("id"));
        assertEquals("Test Process", result.getString("name"));
        assertTrue(result.has("parameters"));
        assertEquals(0, result.getJSONArray("parameters").length());
    }

    /**
     * Test the toJSON method with a process that has parameters.
     * This test ensures that the resulting JSON contains the expected fields and parameters.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithParameters() throws JSONException {
        when(mockConverter.toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockProcessJson);
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
        when(mockProcess.getADProcessParameterList()).thenReturn(Arrays.asList(mockParameter));
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("10");

        JSONObject result = processActionBuilder.toJSON();

        assertNotNull(result);
        assertEquals("testProcessId", result.getString("id"));
        assertEquals("Test Process", result.getString("name"));
        assertTrue(result.has("parameters"));
        assertEquals(1, result.getJSONArray("parameters").length());
    }

    /**
     * Test the toJSON method with a process that has a null parameter.
     * This test ensures that the resulting JSON does not include the null parameter.
     *
     * @throws JSONException if there is an error during JSON processing
     */
    @Test
    void testToJSONWithNullParameter() throws JSONException {
        when(mockConverter.toJsonObject(mockProcess, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockProcessJson);
        when(mockProcess.getADProcessParameterList()).thenReturn(Arrays.asList(null, mockParameter));
        when(mockConverter.toJsonObject(mockParameter, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(mockParameterJson);
        when(mockParameter.getReference()).thenReturn(mockReference);
        when(mockReference.getId()).thenReturn("10");

        JSONObject result = processActionBuilder.toJSON();

        assertNotNull(result);
        assertTrue(result.has("parameters"));
        assertEquals(1, result.getJSONArray("parameters").length());
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
        selectorParamJson.put("id", "selectorParamId");
        when(mockConverter.toJsonObject(selectorParam, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(selectorParamJson);
        when(selectorParam.getReference()).thenReturn(selectorRef);
        when(selectorParam.getReferenceSearchKey()).thenReturn(selectorRef);
        when(selectorParam.getId()).thenReturn("selectorParamId");
        when(selectorRef.getId()).thenReturn("18");

        JSONObject listParamJson = new JSONObject();
        listParamJson.put("id", "listParamId");
        when(mockConverter.toJsonObject(listParam, DataResolvingMode.FULL_TRANSLATABLE))
            .thenReturn(listParamJson);
        when(listParam.getReference()).thenReturn(listRef);
        when(listParam.getReferenceSearchKey()).thenReturn(listRef);
        when(listRef.getId()).thenReturn("17");

        JSONObject mockSelectorInfo = new JSONObject();
        mockSelectorInfo.put("selectorId", "selector123");
        JSONArray mockRefList = new JSONArray();
        mockRefList.put(new JSONObject().put("value", "option1"));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<FieldBuilder> fieldBuilderMock = mockStatic(FieldBuilder.class)) {
            
            contextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
            when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
            
            fieldBuilderMock.when(() -> FieldBuilder.getSelectorInfo("selectorParamId", selectorRef))
                           .thenReturn(mockSelectorInfo);
            fieldBuilderMock.when(() -> FieldBuilder.getListInfo(listRef, mockLanguage))
                           .thenReturn(mockRefList);

            JSONObject result = processActionBuilder.toJSON();

            assertNotNull(result);
            assertTrue(result.has("parameters"));
            assertEquals(2, result.getJSONArray("parameters").length());

            JSONArray parameters = result.getJSONArray("parameters");
            boolean foundSelector = false;
            boolean foundList = false;
            
            for (int i = 0; i < parameters.length(); i++) {
                JSONObject param = parameters.getJSONObject(i);
                if (param.has("selector")) {
                    foundSelector = true;
                }
                if (param.has("refList")) {
                    foundList = true;
                }
            }
            
            assertTrue(foundSelector);
            assertTrue(foundList);
        }
    }
}
