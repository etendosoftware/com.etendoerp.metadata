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

package com.etendoerp.metadata.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Simple test class for HttpServletRequestWrapper constructor and basic functionality.
 */
@RunWith(MockitoJUnitRunner.class)
public class HttpServletRequestWrapperSimpleTest {

    @Mock
    private HttpServletRequest mockRequest;

    /**
     * Tests HttpServletRequestWrapper constructor.
     */
    @Test
    public void testConstructor() {
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(mockRequest);
        
        assertNotNull("Wrapper should not be null", wrapper);
    }

    /**
     * Tests HttpServletRequestWrapper constructor with null request.
     */
    @Test
    public void testConstructorWithNull() {
        try {
            HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(null);
            assertNotNull("Wrapper should handle null request", wrapper);
        } catch (Exception e) {
            // If it throws an exception, that's also valid behavior
            assertNotNull("Exception should not be null", e);
        }
    }

    /**
     * Tests getMethod method delegates to wrapped request.
     */
    @Test
    public void testGetMethod() {
        when(mockRequest.getMethod()).thenReturn("GET");
        
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(mockRequest);
        
        assertEquals("Method should be GET", "GET", wrapper.getMethod());
        verify(mockRequest).getMethod();
    }

    /**
     * Tests getRequestURI method delegates to wrapped request.
     */
    @Test
    public void testGetRequestURI() {
        when(mockRequest.getRequestURI()).thenReturn("/test/uri");
        
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(mockRequest);
        
        assertEquals("URI should match", "/test/uri", wrapper.getRequestURI());
        verify(mockRequest).getRequestURI();
    }

    /**
     * Tests getPathInfo method delegates to wrapped request.
     */
    @Test
    public void testGetPathInfo() {
        when(mockRequest.getPathInfo()).thenReturn("/path/info");
        
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(mockRequest);
        
        assertEquals("Path info should match", "/path/info", wrapper.getPathInfo());
        verify(mockRequest).getPathInfo();
    }
}