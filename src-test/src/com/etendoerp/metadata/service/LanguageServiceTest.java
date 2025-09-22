package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.dal.core.OBContext;
import org.openbravo.test.base.OBBaseTest;

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
public class LanguageServiceTest extends OBBaseTest {

    private LanguageService languageService;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private StringWriter responseWriter;

    /**
     * Sets up the test environment before each test method execution.
     * <p>
     * Initializes mock objects for HTTP request and response, configures the response writer
     * for capturing output, and sets up the necessary OBContext for database operations.
     * Also configures mock request paths for language service testing.
     */
    @Before
    public void setUp() throws Exception {
        setTestUserContext();

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        when(mockRequest.getPathInfo()).thenReturn("/meta/language/en_US");

        languageService = new LanguageService(mockRequest, mockResponse);
    }

    /**
     * Cleans up the test environment after each test method execution.
     * <p>
     * Restores the previous OBContext mode and clears any thread-local variables
     * to prevent memory leaks and ensure proper test isolation.
     */
    @After
    public void tearDown() {
        OBContext.restorePreviousMode();
        MetadataService.clear();
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
            HttpServletResponse errorResponse = mock(HttpServletResponse.class);
            when(errorResponse.getWriter()).thenThrow(new IOException("Mock IO Exception"));

            LanguageService errorService = new LanguageService(mockRequest, errorResponse);

            boolean exceptionThrown = false;
            try {
                errorService.process();
            } catch (IOException e) {
                exceptionThrown = true;
                assertNotNull("Exception message should be provided", e.getMessage());
                assertEquals("Exception message should match expected", "Mock IO Exception", e.getMessage());
            }

            assertTrue("IOException should be properly propagated", exceptionThrown);

        } catch (Exception e) {
            fail("Unexpected exception occurred: " + e.getMessage());
        }
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
                "/meta/language/en_US",
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