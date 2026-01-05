package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.I18NComponent;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;

/**
 * Test class for LabelsBuilder.
 * Tests the functionality of building internationalization labels into JSON
 * format.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class LabelsBuilderTest {

    @Mock
    private OBContext obContext;

    @Mock
    private Language language;

    @Mock
    private I18NComponent i18nComponent;

    @Mock
    private I18NComponent.Label label1;

    @Mock
    private I18NComponent.Label label2;

    private static final String SINGLE_LABEL_STRING = "SINGLE_LABEL";
    private static final String EMPTY_LABEL_STRING = "EMPTY_VALUE_LABEL";

    /**
     * Functional interface for test assertions that may throw JSONException.
     */
    @FunctionalInterface
    private interface JSONAssertions {
        void assertResult(JSONObject result) throws JSONException;
    }

    /**
     * Helper method to execute toJSON with a list of labels and common mock setup.
     * Encapsulates the repeated pattern of mocking OBContext and WeldUtils.
     *
     * @param labels     the list of labels to use
     * @param assertions the assertions to perform on the result
     * @throws JSONException if JSON processing fails
     */
    private void executeToJSONWithLabels(List<I18NComponent.Label> labels, JSONAssertions assertions)
            throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                    .thenReturn(i18nComponent);
            when(i18nComponent.getLabels()).thenReturn(labels);

            LabelsBuilder builder = new LabelsBuilder();
            JSONObject result = builder.toJSON();

            assertions.assertResult(result);
        }
    }

    /**
     * Helper method for single label tests.
     * Sets up label1 with the given key/value and verifies the result.
     *
     * @param labelKey   the key for the label
     * @param labelValue the value for the label
     * @param assertions the assertions to perform on the result
     * @throws JSONException if JSON processing fails
     */
    private void executeSingleLabelTest(String labelKey, String labelValue, JSONAssertions assertions)
            throws JSONException {
        List<I18NComponent.Label> labels = new ArrayList<>();
        labels.add(label1);

        when(label1.getKey()).thenReturn(labelKey);
        when(label1.getValue()).thenReturn(labelValue);

        executeToJSONWithLabels(labels, assertions);
    }

    /**
     * Tests toJSON with multiple labels.
     * Verifies that all labels are correctly converted to JSON key-value pairs.
     */
    @Test
    void testToJSONWithMultipleLabels() throws JSONException {
        List<I18NComponent.Label> labels = new ArrayList<>();
        labels.add(label1);
        labels.add(label2);

        when(label1.getKey()).thenReturn("LABEL_KEY_1");
        when(label1.getValue()).thenReturn("Label Value 1");
        when(label2.getKey()).thenReturn("LABEL_KEY_2");
        when(label2.getValue()).thenReturn("Label Value 2");

        executeToJSONWithLabels(labels, result -> {
            assertNotNull(result, "Result should not be null");
            assertEquals(2, result.length(), "Result should contain 2 labels");
            assertEquals("Label Value 1", result.getString("LABEL_KEY_1"));
            assertEquals("Label Value 2", result.getString("LABEL_KEY_2"));
        });
    }

    /**
     * Tests toJSON with an empty label list.
     * Verifies that an empty JSON object is returned when no labels exist.
     */
    @Test
    void testToJSONWithEmptyLabels() throws JSONException {
        executeToJSONWithLabels(new ArrayList<>(), result -> {
            assertNotNull(result, "Result should not be null even with no labels");
            assertEquals(0, result.length(), "Result should be empty when no labels exist");
        });
    }

    /**
     * Tests toJSON with a single label.
     * Verifies that a single label is correctly converted to JSON.
     */
    @Test
    void testToJSONWithSingleLabel() throws JSONException {
        executeSingleLabelTest(SINGLE_LABEL_STRING, "Single Value", result -> {
            assertNotNull(result);
            assertEquals(1, result.length(), "Result should contain exactly 1 label");
            assertTrue(result.has(SINGLE_LABEL_STRING), "Result should have the label key");
            assertEquals("Single Value", result.getString(SINGLE_LABEL_STRING));
        });
    }

    /**
     * Tests toJSON with labels containing special characters.
     * Verifies that special characters in label values are preserved.
     */
    @Test
    void testToJSONWithSpecialCharacters() throws JSONException {
        String expectedValue = "Value with 'quotes' and \"double quotes\"";

        executeSingleLabelTest("SPECIAL_LABEL", expectedValue, result -> {
            assertNotNull(result);
            assertEquals(expectedValue, result.getString("SPECIAL_LABEL"));
        });
    }

    /**
     * Tests toJSON with Unicode characters in label values.
     * Verifies that Unicode characters are correctly preserved.
     */
    @Test
    void testToJSONWithUnicodeCharacters() throws JSONException {
        String expectedValue = "Etiqueta en español: ñ, á, é, í, ó, ú";

        executeSingleLabelTest("UNICODE_LABEL", expectedValue, result -> {
            assertNotNull(result);
            assertEquals(expectedValue, result.getString("UNICODE_LABEL"));
        });
    }

    /**
     * Tests that LabelsBuilder extends Builder class.
     * Verifies the inheritance hierarchy is correct.
     */
    @Test
    void testInheritanceFromBuilder() throws JSONException {
        executeToJSONWithLabels(new ArrayList<>(), result -> {
            // We need to access the builder instance, so we test differently
        });

        // Alternative approach: create builder in separate try block to test
        // inheritance
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                    .thenReturn(i18nComponent);
            when(i18nComponent.getLabels()).thenReturn(new ArrayList<>());

            LabelsBuilder builder = new LabelsBuilder();

            assertTrue(builder instanceof Builder, "LabelsBuilder should extend Builder");
        }
    }

    /**
     * Tests toJSON when WeldUtils throws IllegalStateException.
     * This is expected in unit test environments where Weld container is not
     * initialized.
     */
    @Test
    void testToJSONWhenWeldNotInitialized() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                    .thenThrow(new IllegalStateException(WELD_CONTAINER_NOT_INITIALIZED_ERROR));

            LabelsBuilder builder = new LabelsBuilder();

            assertThrows(IllegalStateException.class, () -> builder.toJSON(),
                    "Should throw IllegalStateException when Weld container is not initialized");
        }
    }

    /**
     * Tests toJSON with labels having empty values.
     * Verifies that empty string values are handled correctly.
     */
    @Test
    void testToJSONWithEmptyLabelValue() throws JSONException {
        executeSingleLabelTest(EMPTY_LABEL_STRING, "", result -> {
            assertNotNull(result);
            assertTrue(result.has(EMPTY_LABEL_STRING));
            assertEquals("", result.getString(EMPTY_LABEL_STRING));
        });
    }
}
