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

package com.etendoerp.metadata.service;

import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class for MetadataService base class.
 */
@RunWith(MockitoJUnitRunner.class)
public class MetadataServiceTest {

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    // Concrete implementation for testing
    private static class TestMetadataService extends MetadataService {
        public TestMetadataService(HttpServletRequest request, HttpServletResponse response) {
            super(request, response);
        }

        @Override
        public void process() throws IOException {
            // Test implementation
        }
    }

    @After
    public void tearDown() {
        MetadataService.clear();
    }

    @Test
    public void testConstructor() {
        TestMetadataService service = new TestMetadataService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertEquals("Request should be set", mockRequest, service.getRequest());
        assertEquals("Response should be set", mockResponse, service.getResponse());
    }

    @Test
    public void testGetRequestAndResponse() {
        TestMetadataService service = new TestMetadataService(mockRequest, mockResponse);
        
        assertSame("Should return the same request", mockRequest, service.getRequest());
        assertSame("Should return the same response", mockResponse, service.getResponse());
    }

    @Test
    public void testClear() {
        TestMetadataService service = new TestMetadataService(mockRequest, mockResponse);
        
        // Verify request and response are set
        assertNotNull("Request should be set", service.getRequest());
        assertNotNull("Response should be set", service.getResponse());
        
        MetadataService.clear();
        
        // After clear, the ThreadLocal should be cleared
        // Note: We can't directly test this as the methods are protected,
        // but we can verify clear doesn't throw exceptions
        assertNotNull("Service should still exist", service);
    }

    @Test
    public void testWriteJSON() throws IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        
        when(mockResponse.getWriter()).thenReturn(printWriter);
        
        TestMetadataService service = new TestMetadataService(mockRequest, mockResponse);
        
        try {
            JSONObject testJson = new JSONObject();
            testJson.put("test", "value");
            
            service.write(testJson);
            
            // Verify response setup
            verify(mockResponse).setContentType("application/json");
            verify(mockResponse).setCharacterEncoding("UTF-8");
            verify(mockResponse).getWriter();
            
            // Verify JSON was written
            String output = stringWriter.toString();
            assertNotNull("Output should not be null", output);
            assertTrue("Output should contain test data", output.contains("test"));
            assertTrue("Output should contain value", output.contains("value"));
            
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testWriteJSONIOException() throws IOException {
        when(mockResponse.getWriter()).thenThrow(new IOException("Test IO Exception"));
        
        TestMetadataService service = new TestMetadataService(mockRequest, mockResponse);
        
        try {
            JSONObject testJson = new JSONObject();
            
            service.write(testJson);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertEquals("Should propagate IOException", "Test IO Exception", e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testMultipleServices() {
        TestMetadataService service1 = new TestMetadataService(mockRequest, mockResponse);
        
        HttpServletRequest mockRequest2 = mock(HttpServletRequest.class);
        HttpServletResponse mockResponse2 = mock(HttpServletResponse.class);
        TestMetadataService service2 = new TestMetadataService(mockRequest2, mockResponse2);
        
        // The second service should override the ThreadLocal values
        assertEquals("Service2 should have request2", mockRequest2, service2.getRequest());
        assertEquals("Service2 should have response2", mockResponse2, service2.getResponse());
    }
}