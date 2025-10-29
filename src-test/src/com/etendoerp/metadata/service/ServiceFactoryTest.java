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

import com.etendoerp.metadata.exceptions.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Test class for ServiceFactory.
 * Tests the factory pattern implementation for creating appropriate service instances.
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceFactoryTest {
    private static final String SERVICE_NOT_NULL = "Service should not be null";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    /**
     * Sets up test fixtures before each test method.
     */
    @Before
    public void setUp() {
        // Common setup if needed
    }

    /**
     * Tests that ServiceFactory returns SessionService for session path.
     */
    @Test
    public void testGetSessionService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/session");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return SessionService", service instanceof SessionService);
    }

    /**
     * Tests that ServiceFactory returns MenuService for menu path.
     */
    @Test
    public void testGetMenuService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/menu");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return MenuService", service instanceof MenuService);
    }

    /**
     * Tests that ServiceFactory returns WindowService for window path.
     */
    @Test
    public void testGetWindowService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/window/123");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return WindowService", service instanceof WindowService);
    }

    /**
     * Tests that ServiceFactory returns TabService for tab path.
     */
    @Test
    public void testGetTabService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/tab/456");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return TabService", service instanceof TabService);
    }

    /**
     * Tests that ServiceFactory returns LanguageService for language path.
     */
    @Test
    public void testGetLanguageService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/language/en_US");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return LanguageService", service instanceof LanguageService);
    }

    /**
     * Tests that ServiceFactory returns MessageService for message path.
     */
    @Test
    public void testGetMessageService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/message");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return MessageService", service instanceof MessageService);
    }

    /**
     * Tests that ServiceFactory returns LabelsService for labels path.
     */
    @Test
    public void testGetLabelsService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/labels");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return LabelsService", service instanceof LabelsService);
    }

    /**
     * Tests that ServiceFactory returns LocationMetadataService for location path.
     */
    @Test
    public void testGetLocationMetadataService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/location/test");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return LocationMetadataService", service instanceof LocationMetadataService);
    }

    /**
     * Tests that ServiceFactory returns ToolbarService for toolbar path.
     */
    @Test
    public void testGetToolbarService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/toolbar");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return ToolbarService", service instanceof ToolbarService);
    }

    /**
     * Tests that ServiceFactory returns LegacyService for legacy path.
     */
    @Test
    public void testGetLegacyService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/legacy/test");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull(SERVICE_NOT_NULL, service);
        assertTrue("Should return LegacyService", service instanceof LegacyService);
    }

    /**
     * Tests that ServiceFactory throws NotFoundException for unknown paths.
     */
    @Test(expected = NotFoundException.class)
    public void testGetServiceUnknownPath() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/unknown");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    /**
     * Tests that ServiceFactory throws NotFoundException for null path.
     */
    @Test(expected = NotFoundException.class)
    public void testGetServiceNullPath() {
        when(mockRequest.getPathInfo()).thenReturn(null);
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    /**
     * Tests that ServiceFactory throws NotFoundException for empty path.
     */
    @Test(expected = NotFoundException.class)
    public void testGetServiceEmptyPath() {
        when(mockRequest.getPathInfo()).thenReturn("");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    /**
     * Tests that ServiceFactory throws NotFoundException for root path.
     */
    @Test(expected = NotFoundException.class)
    public void testGetServiceRootPath() {
        when(mockRequest.getPathInfo()).thenReturn("/");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }
}