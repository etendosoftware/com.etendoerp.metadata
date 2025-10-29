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

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Before
    public void setUp() {
        // Common setup if needed
    }

    @Test
    public void testGetSessionService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/session");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return SessionService", service instanceof SessionService);
    }

    @Test
    public void testGetMenuService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/menu");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return MenuService", service instanceof MenuService);
    }

    @Test
    public void testGetWindowService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/window/123");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return WindowService", service instanceof WindowService);
    }

    @Test
    public void testGetTabService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/tab/456");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return TabService", service instanceof TabService);
    }

    @Test
    public void testGetLanguageService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/language/en_US");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return LanguageService", service instanceof LanguageService);
    }

    @Test
    public void testGetMessageService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/message");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return MessageService", service instanceof MessageService);
    }

    @Test
    public void testGetLabelsService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/labels");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return LabelsService", service instanceof LabelsService);
    }

    @Test
    public void testGetLocationMetadataService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/location/test");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return LocationMetadataService", service instanceof LocationMetadataService);
    }

    @Test
    public void testGetToolbarService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/toolbar");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return ToolbarService", service instanceof ToolbarService);
    }

    @Test
    public void testGetLegacyService() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/legacy/test");
        
        MetadataService service = ServiceFactory.getService(mockRequest, mockResponse);
        
        assertNotNull("Service should not be null", service);
        assertTrue("Should return LegacyService", service instanceof LegacyService);
    }

    @Test(expected = NotFoundException.class)
    public void testGetService_UnknownPath() {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/unknown");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    @Test(expected = NotFoundException.class)
    public void testGetService_NullPath() {
        when(mockRequest.getPathInfo()).thenReturn(null);
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    @Test(expected = NotFoundException.class)
    public void testGetService_EmptyPath() {
        when(mockRequest.getPathInfo()).thenReturn("");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }

    @Test(expected = NotFoundException.class)
    public void testGetService_RootPath() {
        when(mockRequest.getPathInfo()).thenReturn("/");
        
        ServiceFactory.getService(mockRequest, mockResponse);
    }
}