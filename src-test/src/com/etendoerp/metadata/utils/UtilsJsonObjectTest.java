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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.structure.BaseOBObject;

/**
 * Tests for Utils methods that handle JSON objects and HTTP responses.
 */
@ExtendWith(MockitoExtension.class)
class UtilsJsonObjectTest {

    @Mock
    private BaseOBObject mockBaseOBObject;

    @Mock
    private HttpServletResponse mockResponse;

    /**
     * Tests getJsonObject method with a valid BaseOBObject.
     */
    @Test
    void getJsonObjectWithValidBaseOBObjectReturnsJsonObject() {
        when(mockBaseOBObject.getId()).thenReturn("test-id");
        when(mockBaseOBObject.getEntityName()).thenReturn("TestEntity");

        JSONObject result = Utils.getJsonObject(mockBaseOBObject);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    /**
     * Tests getJsonObject method with null object.
     */
    @Test
    void getJsonObjectWithNullObjectHandlesGracefully() {
        JSONObject result = Utils.getJsonObject(null);
        
        assertNotNull(result);
        assertEquals(0, result.length());
    }

    /**
     * Tests writeJsonResponse method with valid parameters.
     */
    @Test
    void writeJsonResponseWithValidParametersWritesCorrectly() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        String jsonContent = "{\"test\": \"value\"}";
        int statusCode = 200;

        Utils.writeJsonResponse(mockResponse, statusCode, jsonContent);

        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setCharacterEncoding("UTF-8");
        verify(mockResponse).setStatus(statusCode);
        
        printWriter.flush();
        assertEquals(jsonContent, stringWriter.toString());
    }

    /**
     * Tests writeJsonErrorResponse method with error message.
     */
    @Test
    void writeJsonErrorResponseWithErrorMessageWritesCorrectly() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        String errorMessage = "Test error message";
        int statusCode = 400;

        Utils.writeJsonErrorResponse(mockResponse, statusCode, errorMessage);

        verify(mockResponse).setContentType("application/json");
        verify(mockResponse).setCharacterEncoding("UTF-8");
        verify(mockResponse).setStatus(statusCode);
        
        printWriter.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("error"));
        assertTrue(output.contains(errorMessage));
    }

    /**
     * Tests writeJsonResponse when IOException occurs.
     */
    @Test
    void writeJsonResponseWithIOExceptionHandlesGracefully() throws IOException {
        when(mockResponse.getWriter()).thenThrow(new IOException("Test IO Exception"));

        assertDoesNotThrow(() -> {
            Utils.writeJsonResponse(mockResponse, 200, "{\"test\": \"value\"}");
        });
    }

    /**
     * Tests writeJsonErrorResponse with null error message.
     */
    @Test
    void writeJsonErrorResponseWithNullErrorMessageHandlesGracefully() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        Utils.writeJsonErrorResponse(mockResponse, 500, null);

        verify(mockResponse).setStatus(500);
        printWriter.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("error"));
    }
}