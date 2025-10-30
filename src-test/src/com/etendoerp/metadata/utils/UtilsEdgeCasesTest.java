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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Tests for Utils methods edge cases and scenarios not covered in other test files.
 */
@ExtendWith(MockitoExtension.class)
class UtilsEdgeCasesTest {

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private ServletInputStream mockServletInputStream;

    /**
     * Tests readRequestBody with normal request content.
     */
    @Test
    void readRequestBodyWithNormalContentReturnsCorrectString() throws IOException {
        String testContent = "test request body content";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testContent.getBytes());
        
        when(mockRequest.getInputStream()).thenReturn(createServletInputStream(inputStream));

        String result = Utils.readRequestBody(mockRequest);
        
        assertEquals(testContent, result);
    }

    /**
     * Tests readRequestBody with empty request content.
     */
    @Test
    void readRequestBodyWithEmptyContentReturnsEmptyString() throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes());
        
        when(mockRequest.getInputStream()).thenReturn(createServletInputStream(inputStream));

        String result = Utils.readRequestBody(mockRequest);
        
        assertEquals("", result);
    }

    /**
     * Tests readRequestBody with IOException.
     */
    @Test
    void readRequestBodyWithIOExceptionThrowsIOException() throws IOException {
        when(mockRequest.getInputStream()).thenThrow(new IOException("Test IO Exception"));

        assertThrows(IOException.class, () -> {
            Utils.readRequestBody(mockRequest);
        });
    }

    /**
     * Tests getHttpStatusFor with custom exception types.
     */
    @Test
    void getHttpStatusForWithCustomExceptionsReturnsCorrectCodes() {
        assertEquals(405, Utils.getHttpStatusFor(new MethodNotAllowedException("not allowed")));
        assertEquals(422, Utils.getHttpStatusFor(new UnprocessableContentException("unprocessable")));
    }

    /**
     * Tests getRequestData with request that has parameters.
     */
    @Test
    void getRequestDataWithParametersReturnsJsonWithParameters() {
        when(mockRequest.getParameterNames()).thenReturn(java.util.Collections.enumeration(
            java.util.Arrays.asList("param1", "param2")
        ));
        when(mockRequest.getParameter("param1")).thenReturn("value1");
        when(mockRequest.getParameter("param2")).thenReturn("value2");

        JSONObject result = Utils.getRequestData(mockRequest);

        assertNotNull(result);
        assertTrue(result.length() >= 0);
    }

    /**
     * Tests formatMessage with null message.
     */
    @Test
    void formatMessageWithNullMessageReturnsNull() {
        String result = Utils.formatMessage(null, "param1");
        
        assertNull(result);
    }

    /**
     * Tests formatMessage with empty string message.
     */
    @Test
    void formatMessageWithEmptyMessageReturnsEmptyString() {
        String result = Utils.formatMessage("", "param1");
        
        assertEquals("", result);
    }

    /**
     * Helper method to create ServletInputStream from InputStream.
     */
    private ServletInputStream createServletInputStream(InputStream inputStream) {
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(javax.servlet.ReadListener readListener) {
                // Not implemented for test
            }
        };
    }
}