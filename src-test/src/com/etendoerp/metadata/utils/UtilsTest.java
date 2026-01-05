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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.authentication.AuthenticationException;
import org.openbravo.base.exception.OBSecurityException;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests for the {@link Utils} utility class.
 * Tests various utility methods for HTTP handling, JSON conversion,
 * and exception status code mapping.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class UtilsTest {

    private static final String APPLICATION_JSON = "application/json";
    private static final String ERROR_KEY = "error";
    private static final String SUCCESS_KEY = "success";
    private static final String STATUS_KEY = "status";
    private static final String TEST_ERROR_MESSAGE = "Test error message";
    private static final String JSON_NOT_NULL = "JSON should not be null";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    // ========== getHttpStatusFor Tests ==========

    /**
     * Tests that NotFoundException maps to HTTP 404.
     */
    @Test
    public void testGetHttpStatusForNotFoundException() {
        int status = Utils.getHttpStatusFor(new NotFoundException());
        assertEquals("NotFoundException should map to 404", HttpStatus.SC_NOT_FOUND, status);
    }

    /**
     * Tests that UnauthorizedException maps to HTTP 401.
     */
    @Test
    public void testGetHttpStatusForUnauthorizedException() {
        int status = Utils.getHttpStatusFor(new UnauthorizedException());
        assertEquals("UnauthorizedException should map to 401", HttpStatus.SC_UNAUTHORIZED, status);
    }

    /**
     * Tests that MethodNotAllowedException maps to HTTP 405.
     */
    @Test
    public void testGetHttpStatusForMethodNotAllowedException() {
        int status = Utils.getHttpStatusFor(new MethodNotAllowedException());
        assertEquals("MethodNotAllowedException should map to 405", HttpStatus.SC_METHOD_NOT_ALLOWED, status);
    }

    /**
     * Tests that UnprocessableContentException maps to HTTP 422.
     */
    @Test
    public void testGetHttpStatusForUnprocessableContentException() {
        int status = Utils.getHttpStatusFor(new UnprocessableContentException());
        assertEquals("UnprocessableContentException should map to 422", HttpStatus.SC_UNPROCESSABLE_ENTITY, status);
    }

    /**
     * Tests that InternalServerException maps to HTTP 500.
     */
    @Test
    public void testGetHttpStatusForInternalServerException() {
        int status = Utils.getHttpStatusFor(new InternalServerException());
        assertEquals("InternalServerException should map to 500", HttpStatus.SC_INTERNAL_SERVER_ERROR, status);
    }

    /**
     * Tests that AuthenticationException maps to HTTP 401.
     */
    @Test
    public void testGetHttpStatusForAuthenticationException() {
        int status = Utils.getHttpStatusFor(new AuthenticationException("test"));
        assertEquals("AuthenticationException should map to 401", HttpStatus.SC_UNAUTHORIZED, status);
    }

    /**
     * Tests that OBSecurityException maps to HTTP 401.
     */
    @Test
    public void testGetHttpStatusForOBSecurityException() {
        int status = Utils.getHttpStatusFor(new OBSecurityException("test"));
        assertEquals("OBSecurityException should map to 401", HttpStatus.SC_UNAUTHORIZED, status);
    }

    /**
     * Tests that unknown exceptions default to HTTP 500.
     */
    @Test
    public void testGetHttpStatusForUnknownException() {
        int status = Utils.getHttpStatusFor(new RuntimeException("Unknown error"));
        assertEquals("Unknown exceptions should map to 500", HttpStatus.SC_INTERNAL_SERVER_ERROR, status);
    }

    // ========== convertToJson Tests ==========

    /**
     * Tests converting an exception to JSON.
     */
    @Test
    public void testConvertToJsonWithMessage() throws JSONException {
        Exception exception = new RuntimeException(TEST_ERROR_MESSAGE);
        JSONObject json = Utils.convertToJson(exception);

        assertNotNull(JSON_NOT_NULL, json);
        assertTrue("JSON should have error key", json.has(ERROR_KEY));
        assertEquals("Error message should match", TEST_ERROR_MESSAGE, json.getString(ERROR_KEY));
    }

    /**
     * Tests converting an exception with a cause to JSON.
     */
    @Test
    public void testConvertToJsonWithCause() throws JSONException {
        Exception cause = new IllegalStateException("Root cause");
        Exception exception = new RuntimeException("Wrapper", cause);
        JSONObject json = Utils.convertToJson(exception);

        assertNotNull(JSON_NOT_NULL, json);
        assertTrue("JSON should have error key", json.has(ERROR_KEY));
        assertEquals("Error should be from cause", "Root cause", json.getString(ERROR_KEY));
    }

    /**
     * Tests converting an exception with null message.
     * Note: JSONObject may not include keys with null values, so we just verify
     * that the method returns a valid JSON object without throwing exceptions.
     */
    @Test
    public void testConvertToJsonWithNullMessage() throws JSONException {
        Exception exception = new RuntimeException((String) null);
        JSONObject json = Utils.convertToJson(exception);

        assertNotNull(JSON_NOT_NULL, json);
        // JSONObject.has() returns false for null values in Jettison implementation
        // We just verify the method doesn't throw and returns a valid object
    }

    // ========== formatMessage Tests ==========

    /**
     * Tests formatting a message with parameters.
     */
    @Test
    public void testFormatMessageWithParams() {
        String result = Utils.formatMessage("Hello {}", "World");
        assertEquals("Message should be formatted", "Hello World", result);
    }

    /**
     * Tests formatting a message with multiple parameters.
     */
    @Test
    public void testFormatMessageWithMultipleParams() {
        String result = Utils.formatMessage("User {} logged in at {}", "john", "10:00");
        assertEquals("Message should be formatted with multiple params", "User john logged in at 10:00", result);
    }

    /**
     * Tests formatting a message with no parameters.
     */
    @Test
    public void testFormatMessageWithNoParams() {
        String result = Utils.formatMessage("Simple message");
        assertEquals("Message should remain unchanged", "Simple message", result);
    }

    // ========== getRequestData Tests ==========

    /**
     * Tests getting request data from valid JSON body.
     */
    @Test
    public void testGetRequestDataWithValidJson() throws Exception {
        String jsonBody = "{\"key\":\"value\"}";
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(jsonBody)));

        JSONObject result = Utils.getRequestData(mockRequest);

        assertNotNull("Result should not be null", result);
        assertEquals("Key should have correct value", "value", result.getString("key"));
    }

    /**
     * Tests getting request data from empty body.
     */
    @Test
    public void testGetRequestDataWithEmptyBody() throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        JSONObject result = Utils.getRequestData(mockRequest);

        assertNotNull("Result should not be null even with empty body", result);
        assertEquals("Result should be empty JSON", 0, result.length());
    }

    /**
     * Tests getting request data when reader throws exception.
     */
    @Test
    public void testGetRequestDataWithException() throws Exception {
        when(mockRequest.getReader()).thenThrow(new IOException("Reader error"));

        JSONObject result = Utils.getRequestData(mockRequest);

        assertNotNull("Result should not be null on exception", result);
        assertEquals("Result should be empty JSON on exception", 0, result.length());
    }

    /**
     * Tests getting request data from invalid JSON.
     */
    @Test
    public void testGetRequestDataWithInvalidJson() throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("not valid json")));

        JSONObject result = Utils.getRequestData(mockRequest);

        assertNotNull("Result should not be null for invalid JSON", result);
        assertEquals("Result should be empty JSON for invalid JSON", 0, result.length());
    }

    // ========== readRequestBody Tests ==========

    /**
     * Tests reading request body.
     */
    @Test
    public void testReadRequestBody() throws Exception {
        String body = "Request body content";
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        String result = Utils.readRequestBody(mockRequest);

        assertEquals("Body should be read correctly", body, result);
    }

    /**
     * Tests reading multiline request body.
     */
    @Test
    public void testReadRequestBodyMultiline() throws Exception {
        String body = "Line 1\nLine 2\nLine 3";
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        String result = Utils.readRequestBody(mockRequest);

        assertEquals("Multiline body should be read correctly", "Line 1Line 2Line 3", result);
    }

    /**
     * Tests reading empty request body.
     */
    @Test
    public void testReadRequestBodyEmpty() throws Exception {
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        String result = Utils.readRequestBody(mockRequest);

        assertEquals("Empty body should return empty string", "", result);
    }

    // ========== writeJsonResponse Tests ==========

    /**
     * Tests writing JSON response.
     */
    @Test
    public void testWriteJsonResponse() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        String jsonContent = "{\"status\":\"ok\"}";
        Utils.writeJsonResponse(mockResponse, HttpStatus.SC_OK, jsonContent);

        verify(mockResponse).setStatus(HttpStatus.SC_OK);
        verify(mockResponse).setContentType(APPLICATION_JSON);
        verify(mockResponse).setCharacterEncoding("UTF-8");

        printWriter.flush();
        assertEquals("JSON content should be written", jsonContent, stringWriter.toString());
    }

    /**
     * Tests writing JSON response with error status.
     */
    @Test
    public void testWriteJsonResponseWithErrorStatus() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        String jsonContent = "{\"error\":\"Not found\"}";
        Utils.writeJsonResponse(mockResponse, HttpStatus.SC_NOT_FOUND, jsonContent);

        verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
        verify(mockResponse).setContentType(APPLICATION_JSON);
    }

    // ========== writeJsonErrorResponse Tests ==========

    /**
     * Tests writing JSON error response.
     */
    @Test
    public void testWriteJsonErrorResponse() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        Utils.writeJsonErrorResponse(mockResponse, HttpStatus.SC_BAD_REQUEST, TEST_ERROR_MESSAGE);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        verify(mockResponse).setContentType(APPLICATION_JSON);

        printWriter.flush();
        String response = stringWriter.toString();
        JSONObject json = new JSONObject(response);

        assertFalse("Success should be false", json.getBoolean(SUCCESS_KEY));
        assertEquals("Error message should match", TEST_ERROR_MESSAGE, json.getString(ERROR_KEY));
        assertEquals("Status should match", HttpStatus.SC_BAD_REQUEST, json.getInt(STATUS_KEY));
    }

    /**
     * Tests writing JSON error response with special characters.
     */
    @Test
    public void testWriteJsonErrorResponseWithSpecialCharacters() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        String errorMessage = "Error with \"quotes\" and 'apostrophes'";
        Utils.writeJsonErrorResponse(mockResponse, HttpStatus.SC_INTERNAL_SERVER_ERROR, errorMessage);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        printWriter.flush();

        String response = stringWriter.toString();
        assertNotNull("Response should not be null", response);
        assertFalse("Response should not be empty", response.isEmpty());
    }

    // ========== getJsonObject Tests ==========

    /**
     * Tests getJsonObject with null input.
     */
    @Test
    public void testGetJsonObjectWithNull() {
        JSONObject result = Utils.getJsonObject(null);
        assertNull("Result should be null for null input", result);
    }
}
