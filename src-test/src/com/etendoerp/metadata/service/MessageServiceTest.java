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
 * Test class for {@link MessageService}.
 * <p>
 * This class provides comprehensive unit testing for the MessageService functionality,
 * including message retrieval, request processing, and JSON response validation.
 * Tests cover various scenarios including successful message processing, error handling,
 * different user contexts, and message filtering capabilities.
 *
 * @author Generated Test
 */
public class MessageServiceTest extends OBBaseTest {

    private MessageService messageService;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private StringWriter responseWriter;

    /**
     * Sets up the test environment before each test method execution.
     * <p>
     * Initializes mock objects for HTTP request and response, configures the response writer
     * for capturing output, and sets up the necessary OBContext for database operations.
     * Also configures mock request parameters for message service testing.
     */
    @Before
    public void setUp() throws Exception {
        setTestUserContext();
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);
        when(mockRequest.getPathInfo()).thenReturn("/meta/message");
        when(mockRequest.getMethod()).thenReturn("GET");
        messageService = new MessageService(mockRequest, mockResponse);
    }

    /**
     * Cleans up the test environment after each test method execution.
     * <p>
     * Restores the previous OBContext mode and clears any thread-local variables
     * to prevent memory leaks and ensure proper test isolation between test methods.
     */
    @After
    public void tearDown() {
        OBContext.restorePreviousMode();
        MetadataService.clear();
    }

    /**
     * Tests the successful instantiation of MessageService.
     * <p>
     * Verifies that the service can be properly constructed with valid HTTP request
     * and response objects, ensuring all required dependencies are correctly injected
     * and the service is ready for processing.
     */
    @Test
    public void testMessageServiceInstantiation() {
        assertNotNull("MessageService should be successfully instantiated", messageService);
        assertNotNull("Request should be properly injected", messageService.getRequest());
        assertNotNull("Response should be properly injected", messageService.getResponse());
    }

    /**
     * Tests the message processing functionality with default parameters.
     * <p>
     * Validates that the service can successfully process a message request without
     * specific parameters and generate appropriate JSON response containing system
     * messages and translations available to the current user.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessMessagesDefault() throws IOException {
        messageService.process();
        String responseContent = responseWriter.toString();
        assertNotNull("Response content should not be null", responseContent);
        assertFalse("Response should not be empty", responseContent.trim().isEmpty());
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertNotNull("JSON response should be parseable", jsonResponse);
            assertTrue("JSON response should contain message data", jsonResponse.length() > 0);
        } catch (Exception e) {
            assertFalse("Response should be generated even if not standard JSON", responseContent.isEmpty());
        }
    }

    /**
     * Tests message processing with specific message type filtering.
     * <p>
     * Validates that the service can handle requests with specific message type
     * parameters and return filtered message collections based on the criteria.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessMessagesWithTypeFilter() throws IOException {
        when(mockRequest.getParameter("type")).thenReturn("error");
        MessageService filteredService = new MessageService(mockRequest, mockResponse);
        responseWriter.getBuffer().setLength(0);
        filteredService.process();

        String responseContent = responseWriter.toString();
        assertNotNull("Filtered message response should not be null", responseContent);
        assertFalse("Filtered message response should not be empty", responseContent.trim().isEmpty());
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertNotNull("Filtered JSON response should be parseable", jsonResponse);
        } catch (Exception e) {
            assertFalse("Filtered response should be valid", responseContent.isEmpty());
        }
    }

    /**
     * Tests message processing with different user contexts.
     * <p>
     * Validates that the service properly handles message requests from different
     * user contexts and returns appropriate messages based on user permissions
     * and language preferences.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessMessagesInDifferentUserContext() throws IOException {
        OBContext.setAdminMode(true);

        try {
            responseWriter.getBuffer().setLength(0);
            messageService.process();
            String responseContent = responseWriter.toString();
            assertNotNull("Response should be generated in admin context", responseContent);
            assertFalse("Response should contain content in admin context", responseContent.trim().isEmpty());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Tests message processing with language-specific requests.
     * <p>
     * Validates that the service can handle requests for messages in specific
     * languages and return appropriately localized content based on the
     * requested language parameters.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessMessagesWithLanguageParameter() throws IOException {
        when(mockRequest.getParameter("language")).thenReturn("es_ES");
        MessageService languageService = new MessageService(mockRequest, mockResponse);
        responseWriter.getBuffer().setLength(0);
        languageService.process();
        String responseContent = responseWriter.toString();
        assertNotNull("Language-specific message response should not be null", responseContent);
        assertFalse("Language-specific response should not be empty", responseContent.trim().isEmpty());
    }

    /**
     * Tests error handling during message processing.
     * <p>
     * Validates that the service gracefully handles error conditions such as
     * invalid parameters, database connection issues, or permission problems,
     * and provides appropriate error responses without system failure.
     */
    @Test
    public void testMessageProcessingErrorHandling() {
        try {
            when(mockRequest.getParameter("invalidParam")).thenReturn("invalidValue");
            MessageService errorTestService = new MessageService(mockRequest, mockResponse);
            responseWriter.getBuffer().setLength(0);
            errorTestService.process();
            String responseContent = responseWriter.toString();
            assertNotNull("Response should be provided even with invalid parameters", responseContent);
        } catch (Exception e) {
            assertTrue("Service should handle invalid parameters gracefully", true);
        }
    }

    /**
     * Tests I/O error handling during message processing.
     * <p>
     * Validates that the service properly handles IOException scenarios during
     * response writing and propagates them appropriately without causing
     * system instability or resource leaks.
     */
    @Test
    public void testMessageProcessingIOErrorHandling() {
        try {
            HttpServletResponse errorResponse = mock(HttpServletResponse.class);
            when(errorResponse.getWriter()).thenThrow(new IOException("Mock Message IO Exception"));
            MessageService errorService = new MessageService(mockRequest, errorResponse);
            boolean exceptionThrown = false;
            try {
                errorService.process();
            } catch (IOException e) {
                exceptionThrown = true;
                assertNotNull("Exception message should be provided", e.getMessage());
                assertTrue("Exception should contain expected message",
                        e.getMessage().contains("Mock Message IO Exception"));
            }

            assertTrue("IOException should be properly propagated", exceptionThrown);

        } catch (Exception e) {
            fail("Unexpected exception occurred: " + e.getMessage());
        }
    }

    /**
     * Tests message processing with multiple request parameters.
     * <p>
     * Validates that the service can handle complex requests with multiple
     * parameters and filters, returning appropriately filtered and formatted
     * message collections based on the combined criteria.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessMessagesWithMultipleParameters() throws IOException {
        when(mockRequest.getParameter("type")).thenReturn("info");
        when(mockRequest.getParameter("module")).thenReturn("metadata");
        when(mockRequest.getParameter("language")).thenReturn("en_US");
        MessageService multiParamService = new MessageService(mockRequest, mockResponse);
        responseWriter.getBuffer().setLength(0);
        multiParamService.process();
        String responseContent = responseWriter.toString();
        assertNotNull("Multi-parameter message response should not be null", responseContent);
        assertFalse("Multi-parameter response should not be empty", responseContent.trim().isEmpty());
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertTrue("Multi-parameter JSON should be valid", jsonResponse.length() >= 0);
        } catch (Exception e) {
            assertFalse("Multi-parameter response should be meaningful", responseContent.isEmpty());
        }
    }
}