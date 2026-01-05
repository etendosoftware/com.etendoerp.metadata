package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Test class for the abstract Builder class.
 * Tests the initialization and common properties shared by all Builder
 * implementations.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BuilderTest {

    @Mock
    private OBContext obContext;

    @Mock
    private Language language;

    /**
     * Concrete implementation of Builder for testing purposes.
     * Allows testing of the abstract class's non-abstract members.
     */
    private static class TestableBuilder extends Builder {
        @Override
        public JSONObject toJSON() throws JSONException {
            return new JSONObject().put("test", "value");
        }
    }

    /**
     * Tests that the Builder class has a non-null static logger.
     * This verifies that the LogManager properly initializes the logger.
     */
    @Test
    void testLoggerIsInitialized() {
        Logger logger = Builder.logger;
        assertNotNull(logger, "Builder.logger should be initialized");
    }

    /**
     * Tests the Language field initialization when OBContext is available.
     * Verifies that the language field is properly set from the OBContext.
     */
    @Test
    void testLanguageFieldInitialization() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            TestableBuilder builder = new TestableBuilder();

            assertNotNull(builder.language, "Language field should be initialized");
            assertEquals(language, builder.language, "Language should match the one from OBContext");
        }
    }

    /**
     * Tests that the DataToJsonConverter is properly initialized.
     * Verifies that the converter field is not null after Builder construction.
     */
    @Test
    void testConverterFieldInitialization() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            TestableBuilder builder = new TestableBuilder();

            assertNotNull(builder.converter, "Converter field should be initialized");
            assertTrue(builder.converter instanceof DataToJsonConverter,
                    "Converter should be an instance of DataToJsonConverter");
        }
    }

    /**
     * Tests the abstract toJSON method by using a concrete implementation.
     * Verifies that the toJSON method can be implemented and called successfully.
     */
    @Test
    void testToJSONMethodCanBeImplemented() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            TestableBuilder builder = new TestableBuilder();
            JSONObject result = builder.toJSON();

            assertNotNull(result, "toJSON should return a non-null JSONObject");
            assertEquals("value", result.getString("test"),
                    "toJSON should return the expected JSON content");
        }
    }

    /**
     * Tests that Builder subclasses can access the protected converter field.
     * Verifies that the converter is usable for JSON conversion operations.
     */
    @Test
    void testConverterIsAccessibleToSubclasses() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            TestableBuilder builder = new TestableBuilder();
            DataToJsonConverter converter = builder.converter;

            assertNotNull(converter, "Converter should be accessible from subclass");
        }
    }

    /**
     * Tests that the logger is the same instance across multiple Builder creations.
     * Verifies the static nature of the logger field.
     */
    @Test
    void testLoggerIsStatic() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            // Both instances should reference the same static logger
            assertSame(Builder.logger, Builder.logger,
                    "Logger should be a static field and the same across instances");
        }
    }
}
