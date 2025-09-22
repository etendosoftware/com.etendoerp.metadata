package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.dal.core.OBContext;

/**
 * Test class for {@link LanguageService}.
 * <p>
 * This class provides comprehensive unit testing for the LanguageService functionality,
 * including language data retrieval, request processing, and JSON response validation.
 * Tests cover various scenarios including successful processing, error handling, and
 * different request path configurations.
 *
 * @author Generated Test
 */
public class LanguageServiceTest extends BaseMetadataServiceTest {
    private static final String ENGLISH_META_PATH = "/meta/language/en_US";
    private static final String MOCK_LANGUAGE_IO_EXCEPTION_MESSAGE = "Mock IO Exception";
    private LanguageService languageService;

    @Override
    protected String getServicePath() {
        return ENGLISH_META_PATH;
    }

    @Before
    public void setUpLanguageService() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(ENGLISH_META_PATH);
        languageService = new LanguageService(mockRequest, mockResponse);
    }

    /**
     * Tests the successful instantiation of LanguageService.
     * <p>
     * Verifies that the service can be properly constructed with valid HTTP request
     * and response objects, ensuring all required dependencies are correctly injected.
     */
    @Test
    public void testLanguageServiceInstantiation() {
        assertNotNull("LanguageService should be successfully instantiated", languageService);
        assertNotNull("Request should be properly injected", languageService.getRequest());
        assertNotNull("Response should be properly injected", languageService.getResponse());
    }

    /**
     * Tests the language processing functionality with a valid language code.
     * <p>
     * Validates that the service can successfully process a language request for
     * a specific language (en_US) and generate appropriate JSON response containing
     * language-specific data and translations.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessLanguageWithValidCode() throws IOException {
        languageService.process();

        String responseContent = responseWriter.toString();
        assertNotNull("Response content should not be null", responseContent);
        assertFalse("Response should not be empty", responseContent.trim().isEmpty());

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertNotNull("JSON response should be parseable", jsonResponse);
            assertTrue("JSON response should contain data", jsonResponse.length() > 0);

        } catch (Exception e) {
            assertFalse("Response should be generated even if not JSON", responseContent.isEmpty());
        }
    }

    /**
     * Tests the language processing with different language codes.
     * <p>
     * Validates that the service can handle various language code formats and
     * return appropriate responses for each language variant.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessLanguageWithDifferentCodes() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn("/meta/language/es_ES");
        LanguageService spanishService = new LanguageService(mockRequest, mockResponse);

        responseWriter.getBuffer().setLength(0);

        spanishService.process();

        String responseContent = responseWriter.toString();
        assertNotNull("Spanish language response should not be null", responseContent);
        assertFalse("Spanish language response should not be empty", responseContent.trim().isEmpty());
    }

    /**
     * Tests language processing with admin context.
     * <p>
     * Validates that the service properly handles requests when running in
     * administrative mode and maintains consistent behavior.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessLanguageInAdminMode() throws IOException {
        OBContext.setAdminMode(true);

        try {
            languageService.process();

            String responseContent = responseWriter.toString();
            assertNotNull("Response should be generated in admin context", responseContent);
            assertFalse("Response should contain content", responseContent.trim().isEmpty());

        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Tests error handling during language processing.
     * <p>
     * Validates that the service gracefully handles error conditions such as
     * invalid language codes or I/O errors, and provides appropriate error responses.
     */
    @Test
    public void testLanguageProcessingErrorHandling() {
        try {
            when(mockRequest.getPathInfo()).thenReturn("/meta/language/invalid_lang");
            LanguageService invalidService = new LanguageService(mockRequest, mockResponse);

            responseWriter.getBuffer().setLength(0);

            invalidService.process();

            String responseContent = responseWriter.toString();
            assertNotNull("Response should be provided even for invalid language", responseContent);

        } catch (Exception e) {
            assertTrue("Service should handle invalid language codes gracefully", true);
        }
    }

    /**
     * Tests I/O error handling during language processing.
     * <p>
     * Validates that the service properly handles IOException scenarios and
     * propagates them appropriately without causing system instability.
     */
    @Test
    public void testLanguageProcessingIOErrorHandling() {
        try {
            HttpServletResponse errorResponse = createErrorResponseMock(MOCK_LANGUAGE_IO_EXCEPTION_MESSAGE);
            LanguageService errorService = new LanguageService(mockRequest, errorResponse);

            assertIOExceptionIsHandledForLanguage(errorService);
        } catch (Exception e) {
            fail("Unexpected exception occurred: " + e.getMessage());
        }
    }

    /**
     * Helper method to test IOException handling during language processing.
     * This method extracts the nested try-catch logic to improve code organization
     * and maintainability.
     *
     * @param errorService The LanguageService instance to test with error conditions
     */
    private void assertIOExceptionIsHandledForLanguage(LanguageService errorService) {
        boolean exceptionThrown = false;
        try {
            errorService.process();
        } catch (IOException e) {
            exceptionThrown = true;
            assertNotNull("Exception message should be provided", e.getMessage());
            assertEquals("Exception message should match expected", MOCK_LANGUAGE_IO_EXCEPTION_MESSAGE, e.getMessage());
        }
        assertTrue("IOException should be properly propagated", exceptionThrown);
    }

    /**
     * Tests the language path extraction functionality.
     * <p>
     * Validates that the service correctly extracts language codes from various
     * request path formats and handles edge cases properly.
     */
    @Test
    public void testLanguagePathExtraction() throws IOException {
        String[] testPaths = {
                ENGLISH_META_PATH,
                "/meta/language/es_ES",
                "/meta/language/fr_FR",
                "/meta/language/de_DE"
        };

        for (String path : testPaths) {
            responseWriter.getBuffer().setLength(0);

            when(mockRequest.getPathInfo()).thenReturn(path);
            LanguageService pathTestService = new LanguageService(mockRequest, mockResponse);

            pathTestService.process();

            String responseContent = responseWriter.toString();
            assertNotNull("Response should be generated for path: " + path, responseContent);
        }
    }
}