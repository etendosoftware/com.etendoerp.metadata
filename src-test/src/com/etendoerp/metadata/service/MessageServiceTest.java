package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.TAB_ID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.data.RequestVariables;

/**
 * Unit tests for the MessageService class, which handles message retrieval and CORS headers
 * in a web service context. This class extends OBBaseTest for Openbravo testing utilities.
 */
@RunWith(MockitoJUnitRunner.class)
public class MessageServiceTest extends OBBaseTest {

    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private OBError error;
    
    private MessageService messageService;
    
    private StringWriter stringWriter;
    
    private PrintWriter printWriter;

    /**
     * Sets up the test environment before each test method execution.
     * Initializes mock objects and creates a MessageService instance with mocked request and response.
     * 
     * @throws Exception if there's an error during setup, typically from parent class initialization
     *                   or when creating PrintWriter/StringWriter instances
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        messageService = new MessageService(request, response);
    }

    /**
     * Tests that CORS headers are properly set when the Origin header is present in the request.
     * Verifies that all required CORS headers are added to the response when a valid origin is provided.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     */
    @Test
    public void processShouldSetCORSHeadersWhenOriginPresent() throws IOException {
        String origin = "http://localhost:8080";
        when(request.getHeader("Origin")).thenReturn(origin);
        when(request.getParameter(TAB_ID)).thenReturn("testTabId");
        
        try (MockedConstruction<RequestVariables> ignored = mockConstruction(RequestVariables.class,
                (mock, context) -> {
                    when(mock.getMessage("testTabId")).thenReturn(null);
                })) {
            
            messageService.process();
            
            verify(response).setHeader("Access-Control-Allow-Origin", origin);
            verify(response).setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            verify(response).setHeader("Access-Control-Allow-Credentials", "true");
            verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, origin, accept, X-Requested-With");
            verify(response).setHeader("Access-Control-Max-Age", "1000");
        }
    }

    /**
     * Tests that CORS headers are not set when the Origin header is null in the request.
     * Verifies that no CORS headers are added to the response when no origin is provided.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     */
    @Test
    public void processShouldNotSetCORSHeadersWhenOriginNull() throws IOException {
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getParameter(TAB_ID)).thenReturn("testTabId");
        
        try (MockedConstruction<RequestVariables> ignored = mockConstruction(RequestVariables.class,
                (mock, context) -> {
                    when(mock.getMessage("testTabId")).thenReturn(null);
                })) {
            
            messageService.process();
            
            verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), any());
        }
    }

    /**
     * Tests that CORS headers are not set when the Origin header is empty in the request.
     * Verifies that no CORS headers are added to the response when an empty origin is provided.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     */
    @Test
    public void processShouldNotSetCORSHeadersWhenOriginEmpty() throws IOException {
        when(request.getHeader("Origin")).thenReturn("");
        when(request.getParameter(TAB_ID)).thenReturn("testTabId");
        
        try (MockedConstruction<RequestVariables> ignored = mockConstruction(RequestVariables.class,
                (mock, context) -> {
                    when(mock.getMessage("testTabId")).thenReturn(null);
                })) {
            
            messageService.process();
            
            verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), any());
        }
    }

    /**
     * Tests that an empty message is returned when no error is found for the given tab ID.
     * Verifies that the JSON response contains an empty message string and that the message
     * is properly removed from the RequestVariables after processing.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     * @throws JSONException if there's an error parsing the JSON response or accessing JSON properties
     */
    @Test
    public void processShouldReturnEmptyMessageWhenNoErrorFound() throws IOException, JSONException {
        String tabId = "testTabId";
        when(request.getParameter(TAB_ID)).thenReturn(tabId);
        when(request.getHeader("Origin")).thenReturn("http://localhost:8080");
        
        try (MockedConstruction<RequestVariables> mockedConstruction = mockConstruction(RequestVariables.class, 
                (mock, context) -> {
                    when(mock.getMessage(tabId)).thenReturn(null);
                })) {
            
            messageService.process();
            
            printWriter.flush();
            String jsonResponse = stringWriter.toString();
            JSONObject responseJson = new JSONObject(jsonResponse);
            
            assertEquals("", responseJson.getString("message"));
            
            RequestVariables varsInstance = mockedConstruction.constructed().get(0);
            verify(varsInstance).removeMessage(tabId);
        }
    }

    /**
     * Tests that error details are properly returned when an error exists for the given tab ID.
     * Verifies that the JSON response contains the correct error message, type, and title,
     * and that the message is properly removed from the RequestVariables after processing.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     * @throws JSONException if there's an error parsing the JSON response or accessing JSON properties
     */
    @Test
    public void processShouldReturnErrorDetailsWhenErrorExists() throws IOException, JSONException {
        String tabId = "testTabId";
        String errorMessage = "Test error message";
        String errorType = "Error";
        String errorTitle = "Error Title";
        
        when(request.getParameter(TAB_ID)).thenReturn(tabId);
        when(request.getHeader("Origin")).thenReturn("http://localhost:8080");
        when(error.getMessage()).thenReturn(errorMessage);
        when(error.getType()).thenReturn(errorType);
        when(error.getTitle()).thenReturn(errorTitle);
        
        try (MockedConstruction<RequestVariables> mockedConstruction = mockConstruction(RequestVariables.class, 
                (mock, context) -> {
                    when(mock.getMessage(tabId)).thenReturn(error);
                })) {
            
            messageService.process();
            
            printWriter.flush();
            String jsonResponse = stringWriter.toString();
            JSONObject responseJson = new JSONObject(jsonResponse);
            
            assertEquals(errorMessage, responseJson.getString("message"));
            assertEquals(errorType, responseJson.getString("type"));
            assertEquals(errorTitle, responseJson.getString("title"));
            
            RequestVariables varsInstance = mockedConstruction.constructed().get(0);
            verify(varsInstance).removeMessage(tabId);
        }
    }

    /**
     * Tests that the correct content type and character encoding are set in the response.
     * Verifies that the response content type is set to "application/json" and the character
     * encoding is set to "UTF-8".
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     */
    @Test
    public void processShouldSetCorrectContentType() throws IOException {
        when(request.getParameter(TAB_ID)).thenReturn("testTabId");
        when(request.getHeader("Origin")).thenReturn("http://localhost:8080");
        
        try (MockedConstruction<RequestVariables> ignored = mockConstruction(RequestVariables.class,
                (mock, context) -> {
                    when(mock.getMessage("testTabId")).thenReturn(null);
                })) {
            
            messageService.process();
            
            verify(response).setContentType("application/json");
            verify(response).setCharacterEncoding("UTF-8");
        }
    }

    /**
     * Tests that the service properly handles null tab ID parameters.
     * Verifies that when no tab ID is provided in the request, the service still processes
     * the request correctly and returns an empty message response.
     * 
     * @throws IOException if there's an error during the process method execution or when accessing
     *                     the response writer
     * @throws JSONException if there's an error parsing the JSON response or accessing JSON properties
     */
    @Test
    public void processShouldHandleNullTabId() throws IOException, JSONException {
        // Given
        when(request.getParameter(TAB_ID)).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn("http://localhost:8080");
        
        try (MockedConstruction<RequestVariables> mockedConstruction = mockConstruction(RequestVariables.class, 
                (mock, context) -> {
                    when(mock.getMessage(null)).thenReturn(null);
                })) {

            messageService.process();
            
            printWriter.flush();
            String jsonResponse = stringWriter.toString();
            JSONObject responseJson = new JSONObject(jsonResponse);
            
            assertEquals("", responseJson.getString("message"));
            
            RequestVariables varsInstance = mockedConstruction.constructed().get(0);
            verify(varsInstance).removeMessage(null);
        }
    }
}
