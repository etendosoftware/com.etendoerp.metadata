package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
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
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.utils.Constants;

/**
 * Test class for ProcessParameterBuilder.
 * Tests the JSON conversion of AD_Process_Para entities including
 * selector and list reference handling.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ProcessParameterBuilderTest {

    @Mock
    private ProcessParameter parameter;

    @Mock
    private Reference reference;

    @Mock
    private Reference referenceSearchKey;

    @Mock
    private Language language;

    @Mock
    private OBContext obContext;

    @Mock
    private org.openbravo.model.ad.domain.List listItem;

    private static final String SELECTOR_REFERENCE_ID = "95E2A8B50A254B2AAE6774B8C2F28120"; // Table reference
    private static final String LIST_REFERENCE_ID = "17";

    @BeforeEach
    void setUp() {
        when(parameter.getId()).thenReturn(PARAMETER_ID);
        when(parameter.getReferenceSearchKey()).thenReturn(referenceSearchKey);
        when(parameter.getReference()).thenReturn(reference);
        when(parameter.isRange()).thenReturn(false);
        when(parameter.getValueFormat()).thenReturn(null);
        when(parameter.getMinValue()).thenReturn(null);
        when(parameter.getMaxValue()).thenReturn(null);
    }

    /**
     * Helper method to execute toJSON with proper static mocks.
     */
    private JSONObject executeToJSON(Runnable extraMocks) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject()
                                    .put("id", PARAMETER_ID)
                                    .put("name", "TestParameter");
                            when(mock.toJsonObject(any(ProcessParameter.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            if (extraMocks != null) {
                extraMocks.run();
            }

            ProcessParameterBuilder builder = new ProcessParameterBuilder(parameter);
            return builder.toJSON();
        }
    }

    /**
     * Tests toJSON with a non-selector, non-list parameter.
     * Verifies that basic properties are included in the output.
     */
    @Test
    void testToJSONWithBasicParameter() throws JSONException {
        when(reference.getId()).thenReturn("10"); // String reference

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("id"), "Result should contain id");
        assertTrue(result.has("isRange"), "Result should contain isRange");
        assertFalse(result.getBoolean("isRange"));
    }

    /**
     * Tests toJSON with a range parameter.
     * Verifies that isRange is set to true.
     */
    @Test
    void testToJSONWithRangeParameter() throws JSONException {
        when(reference.getId()).thenReturn("10");
        when(parameter.isRange()).thenReturn(true);

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.getBoolean("isRange"), "isRange should be true");
    }

    /**
     * Tests toJSON with value format.
     * Verifies that valueFormat is included in the output.
     */
    @Test
    void testToJSONWithValueFormat() throws JSONException {
        when(reference.getId()).thenReturn("10");
        when(parameter.getValueFormat()).thenReturn("@A@.##");

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("valueFormat"), "Result should contain valueFormat");
        assertEquals("@A@.##", result.getString("valueFormat"));
    }

    /**
     * Tests toJSON with min and max values.
     * Verifies that value constraints are included.
     */
    @Test
    void testToJSONWithMinMaxValues() throws JSONException {
        when(reference.getId()).thenReturn("10");
        when(parameter.getMinValue()).thenReturn("0");
        when(parameter.getMaxValue()).thenReturn("100");

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("minValue"), "Result should contain minValue");
        assertTrue(result.has("maxValue"), "Result should contain maxValue");
        assertEquals("0", result.getString("minValue"));
        assertEquals("100", result.getString("maxValue"));
    }

    /**
     * Tests toJSON with a list reference parameter.
     * Verifies that refList is added for list references.
     */
    @Test
    void testToJSONWithListParameter() throws JSONException {
        when(reference.getId()).thenReturn(LIST_REFERENCE_ID);
        when(referenceSearchKey.getADListList()).thenReturn(Collections.emptyList());

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("refList"), "Result should contain refList for list reference");
    }

    /**
     * Tests toJSON with list items.
     * Verifies that list items are properly converted to refList.
     */
    @Test
    void testToJSONWithListItems() throws JSONException {
        when(reference.getId()).thenReturn(LIST_REFERENCE_ID);
        when(listItem.getId()).thenReturn(LIST_ID);
        when(listItem.getSearchKey()).thenReturn("OPTION1");
        when(listItem.get(anyString(), any(Language.class), anyString())).thenReturn("Option 1");
        when(listItem.isActive()).thenReturn(true);
        when(referenceSearchKey.getADListList()).thenReturn(List.of(listItem));

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertTrue(result.has("refList"));
        JSONArray refList = result.getJSONArray("refList");
        assertEquals(1, refList.length());

        JSONObject listJson = refList.getJSONObject(0);
        assertEquals(LIST_ID, listJson.getString("id"));
        assertEquals("OPTION1", listJson.getString("value"));
    }

    /**
     * Tests toJSON with a selector reference parameter.
     * Verifies that selector is added for selector references.
     */
    @Test
    void testToJSONWithSelectorParameter() throws JSONException {
        when(reference.getId()).thenReturn(SELECTOR_REFERENCE_ID);
        when(referenceSearchKey.getOBUISELSelectorList()).thenReturn(Collections.emptyList());
        when(referenceSearchKey.getADReferencedTreeList()).thenReturn(Collections.emptyList());

        try (MockedStatic<FieldBuilder> mockedFieldBuilder = mockStatic(FieldBuilder.class)) {
            mockedFieldBuilder.when(() -> FieldBuilder.getSelectorInfo(eq(PARAMETER_ID), any()))
                    .thenReturn(new JSONObject().put("datasource", "TestDatasource"));

            JSONObject result = executeToJSON(null);

            assertNotNull(result);
            assertTrue(result.has("selector"), "Result should contain selector for selector reference");
        }
    }

    /**
     * Tests toJSON with null reference.
     * Verifies that the builder handles null reference gracefully.
     */
    @Test
    void testToJSONWithNullReference() throws JSONException {
        when(parameter.getReference()).thenReturn(null);

        JSONObject result = executeToJSON(null);

        assertNotNull(result);
        assertFalse(result.has("selector"), "Should not have selector with null reference");
        assertFalse(result.has("refList"), "Should not have refList with null reference");
    }

    /**
     * Tests toJSON includes explicit legacy flags.
     * Verifies that all legacy flags are present.
     */
    @Test
    void testToJSONContainsExplicitLegacyFlags() throws JSONException {
        when(reference.getId()).thenReturn("10");
        when(parameter.isRange()).thenReturn(true);
        when(parameter.getValueFormat()).thenReturn("format");
        when(parameter.getMinValue()).thenReturn("min");
        when(parameter.getMaxValue()).thenReturn("max");

        JSONObject result = executeToJSON(null);

        assertTrue(result.has("isRange"), "Should contain isRange flag");
        assertTrue(result.has("valueFormat"), "Should contain valueFormat flag");
        assertTrue(result.has("minValue"), "Should contain minValue flag");
        assertTrue(result.has("maxValue"), "Should contain maxValue flag");
    }

    /**
     * Tests that ProcessParameterBuilder extends Builder.
     */
    @Test
    void testInheritanceFromBuilder() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            when(mock.toJsonObject(any(ProcessParameter.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", PARAMETER_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);
            when(reference.getId()).thenReturn("10");

            ProcessParameterBuilder builder = new ProcessParameterBuilder(parameter);

            assertTrue(builder instanceof Builder,
                    "ProcessParameterBuilder should extend Builder");
        }
    }

    /**
     * Tests constructor stores parameter correctly.
     * Verifies that the parameter passed to constructor is used in toJSON.
     */
    @Test
    void testConstructorStoresParameter() throws JSONException {
        ProcessParameter specificParam = mock(ProcessParameter.class);
        when(specificParam.getId()).thenReturn("specific-param-id");
        when(specificParam.getReference()).thenReturn(reference);
        when(specificParam.getReferenceSearchKey()).thenReturn(referenceSearchKey);
        when(specificParam.isRange()).thenReturn(true);
        when(specificParam.getValueFormat()).thenReturn("specific-format");
        when(specificParam.getMinValue()).thenReturn("10");
        when(specificParam.getMaxValue()).thenReturn("50");
        when(reference.getId()).thenReturn("10");

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class,
                        (mock, context) -> {
                            when(mock.toJsonObject(eq(specificParam), eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id", "specific-param-id"));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            ProcessParameterBuilder builder = new ProcessParameterBuilder(specificParam);
            JSONObject result = builder.toJSON();

            assertNotNull(result);
            assertEquals("specific-param-id", result.getString("id"));
            assertTrue(result.getBoolean("isRange"));
            assertEquals("specific-format", result.getString("valueFormat"));
        }
    }
}
