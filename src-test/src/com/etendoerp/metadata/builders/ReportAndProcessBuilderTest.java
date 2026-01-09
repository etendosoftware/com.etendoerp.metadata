package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Test class for ReportAndProcessBuilder.
 * Tests the JSON conversion of AD_Process entities (legacy Report and Process)
 * including their parameters.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ReportAndProcessBuilderTest {

    @Mock
    private Process process;

    @Mock
    private ProcessParameter param1;

    @Mock
    private ProcessParameter param2;

    @Mock
    private Reference reference;

    @Mock
    private Reference referenceSearchKey;

    @Mock
    private Language language;

    @Mock
    private OBContext obContext;

    private static final String SPECIFIC_PROCESS_ID_STRING = "specific-process-id";

    @BeforeEach
    void setUp() {
        when(process.getId()).thenReturn(PROCESS_ID);
        when(process.getADProcessParameterList()).thenReturn(Collections.emptyList());
    }

    /**
     * Helper method to set up parameter mock with common behavior.
     */
    private void setupParameterMock(ProcessParameter param, String id, String columnName) {
        when(param.getId()).thenReturn(id);
        when(param.getDBColumnName()).thenReturn(columnName);
        when(param.getReference()).thenReturn(reference);
        when(param.getReferenceSearchKey()).thenReturn(referenceSearchKey);
        when(param.isRange()).thenReturn(false);
        when(param.getValueFormat()).thenReturn(null);
        when(param.getMinValue()).thenReturn(null);
        when(param.getMaxValue()).thenReturn(null);
        when(reference.getId()).thenReturn("10"); // String reference
    }

    /**
     * Helper method to execute toJSON with proper static mocks.
     */
    private JSONObject executeToJSON(Runnable extraMocks) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> converterConstruction = mockConstruction(
                        DataToJsonConverter.class, (mock, context) -> {
                            JSONObject processJson = new JSONObject()
                                    .put("id", PROCESS_ID)
                                    .put("name", TEST_PROCESS);
                            when(mock.toJsonObject(any(Process.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(processJson);
                            when(mock.toJsonObject(any(ProcessParameter.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenAnswer(invocation -> {
                                        ProcessParameter p = invocation.getArgument(0);
                                        return new JSONObject().put("id", p.getId());
                                    });
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            if (extraMocks != null) {
                extraMocks.run();
            }

            ReportAndProcessBuilder builder = new ReportAndProcessBuilder(process);
            return builder.toJSON();
        }
    }

    /**
     * Tests toJSON with a process that has no parameters.
     * Verifies that an empty parameters object is included.
     */
    @Test
    void testToJSONWithNoParameters() throws JSONException {
        when(process.getADProcessParameterList()).thenReturn(Collections.emptyList());

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("id"), "Result should contain process id");
        assertTrue(result.has("name"), "Result should contain process name");
        assertTrue(result.has(PARAMETERS), "Result should contain parameters object");
        assertEquals(0, result.getJSONObject(PARAMETERS).length(),
                "Parameters object should be empty");
    }

    /**
     * Tests toJSON with a process that has one parameter.
     * Verifies that the parameter is correctly included.
     */
    @Test
    void testToJSONWithSingleParameter() throws JSONException {
        setupParameterMock(param1, SELECTOR_PARAM_ID, PARAM1_COLUMN);
        when(process.getADProcessParameterList()).thenReturn(List.of(param1));

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has(PARAMETERS));

        JSONObject params = result.getJSONObject(PARAMETERS);
        assertEquals(1, params.length(), "Should have exactly one parameter");
        assertTrue(params.has(PARAM1_COLUMN), "Parameters should contain param1Column");
    }

    /**
     * Tests toJSON with a process that has multiple parameters.
     * Verifies that all parameters are correctly included.
     */
    @Test
    void testToJSONWithMultipleParameters() throws JSONException {
        setupParameterMock(param1, "param1-id", PARAM1_COLUMN);
        setupParameterMock(param2, "param2-id", PARAM2_COLUMN);
        when(process.getADProcessParameterList()).thenReturn(List.of(param1, param2));

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has(PARAMETERS));

        JSONObject params = result.getJSONObject(PARAMETERS);
        assertEquals(2, params.length(), "Should have exactly two parameters");
        assertTrue(params.has(PARAM1_COLUMN), "Parameters should contain param1Column");
        assertTrue(params.has(PARAM2_COLUMN), "Parameters should contain param2Column");
    }

    /**
     * Tests that parameter JSON includes parameter properties.
     * Verifies that each parameter's JSON has id.
     */
    @Test
    void testParameterJSONIncludesProperties() throws JSONException {
        setupParameterMock(param1, SELECTOR_PARAM_ID, PARAM1_COLUMN);
        when(process.getADProcessParameterList()).thenReturn(List.of(param1));

        JSONObject result = executeToJSON(null);

        JSONObject params = result.getJSONObject(PARAMETERS);
        JSONObject paramJson = params.getJSONObject(PARAM1_COLUMN);

        assertNotNull(paramJson);
        assertTrue(paramJson.has("id"), "Parameter JSON should contain id");
    }

    /**
     * Tests that process base metadata is included.
     * Verifies that the base process properties are present.
     */
    @Test
    void testProcessBaseMetadataIncluded() throws JSONException {
        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("id"));
        assertTrue(result.has("name"));
        assertEquals(PROCESS_ID, result.getString("id"));
        assertEquals(TEST_PROCESS, result.getString("name"));
    }

    /**
     * Tests that ReportAndProcessBuilder extends Builder.
     */
    @Test
    void testInheritanceFromBuilder() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) ->
                            when(mock.toJsonObject(any(Process.class), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", PROCESS_ID))
                        )) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            ReportAndProcessBuilder builder = new ReportAndProcessBuilder(process);

            assertTrue(builder instanceof Builder,
                    "ReportAndProcessBuilder should extend Builder");
        }
    }

    /**
     * Tests constructor stores process correctly.
     * Verifies that the process passed to constructor is used in toJSON.
     */
    @Test
    void testConstructorStoresProcess() throws JSONException {
        Process specificProcess = mock(Process.class);
        when(specificProcess.getId()).thenReturn(SPECIFIC_PROCESS_ID_STRING);
        when(specificProcess.getADProcessParameterList()).thenReturn(Collections.emptyList());

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) ->
                            when(mock.toJsonObject(eq(specificProcess), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject()
                                            .put("id", SPECIFIC_PROCESS_ID_STRING)
                                            .put("name", "Specific Process"))
                        )) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            ReportAndProcessBuilder builder = new ReportAndProcessBuilder(specificProcess);
            JSONObject result = builder.toJSON();

            assertNotNull(result);
            assertEquals(SPECIFIC_PROCESS_ID_STRING, result.getString("id"));
            assertEquals("Specific Process", result.getString("name"));
        }
    }

    /**
     * Tests that parameters are keyed by DBColumnName.
     * Verifies the correct key is used for each parameter in the parameters object.
     */
    @Test
    void testParametersKeyedByDBColumnName() throws JSONException {
        String expectedColumnName = "CustomColumnName";
        setupParameterMock(param1, "any-id", expectedColumnName);
        when(process.getADProcessParameterList()).thenReturn(List.of(param1));

        JSONObject result = executeToJSON(null);

        JSONObject params = result.getJSONObject(PARAMETERS);
        assertTrue(params.has(expectedColumnName),
                "Parameter should be keyed by its DBColumnName");
    }

    /**
     * Tests toJSON with empty parameter list returns valid JSON.
     * Verifies that the result is valid even without parameters.
     */
    @Test
    void testToJSONWithEmptyParameterListReturnsValidJSON() throws JSONException {
        when(process.getADProcessParameterList()).thenReturn(new ArrayList<>());

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertDoesNotThrow(() -> result.getJSONObject(PARAMETERS));
    }

    /**
     * Tests that ProcessParameterBuilder is used for each parameter.
     * Verifies that parameters are processed using ProcessParameterBuilder.
     */
    @Test
    void testUsesProcessParameterBuilderForParameters() throws JSONException {
        setupParameterMock(param1, "param1-id", PARAM1_COLUMN);
        when(param1.isRange()).thenReturn(true);
        when(process.getADProcessParameterList()).thenReturn(List.of(param1));

        JSONObject result = executeToJSON(null);

        JSONObject params = result.getJSONObject(PARAMETERS);
        JSONObject paramJson = params.getJSONObject(PARAM1_COLUMN);

        // ProcessParameterBuilder adds isRange property
        assertTrue(paramJson.has("isRange"),
                "Parameter should have isRange from ProcessParameterBuilder");
    }

    /**
     * Tests that multiple parameters don't override each other.
     * Verifies that each parameter maintains its own properties.
     */
    @Test
    void testMultipleParametersAreDistinct() throws JSONException {
        setupParameterMock(param1, "unique-id-1", "Column1");
        setupParameterMock(param2, "unique-id-2", "Column2");
        when(process.getADProcessParameterList()).thenReturn(List.of(param1, param2));

        JSONObject result = executeToJSON(null);

        JSONObject params = result.getJSONObject(PARAMETERS);

        JSONObject param1Json = params.getJSONObject("Column1");
        JSONObject param2Json = params.getJSONObject("Column2");

        assertEquals("unique-id-1", param1Json.getString("id"));
        assertEquals("unique-id-2", param2Json.getString("id"));
    }
}
