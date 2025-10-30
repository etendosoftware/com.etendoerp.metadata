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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Simple unit tests for BaseWebService abstract class.
 * Tests class structure and method signatures without runtime instantiation.
 */
class BaseWebServiceSimpleTest {

    /**
     * Tests that BaseWebService class exists and has correct structure.
     */
    @Test
    void classExistsAndIsAbstract() {
        assertTrue(BaseWebService.class.isInterface() || 
                   java.lang.reflect.Modifier.isAbstract(BaseWebService.class.getModifiers()));
    }

    /**
     * Tests that BaseWebService implements WebService interface.
     */
    @Test
    void implementsWebServiceInterface() {
        assertTrue(org.openbravo.service.web.WebService.class.isAssignableFrom(BaseWebService.class));
    }

    /**
     * Tests that all expected HTTP methods are present.
     */
    @Test
    void hasExpectedHttpMethods() throws Exception {
        // Check doGet method
        assertDoesNotThrow(() -> {
            Method doGet = BaseWebService.class.getDeclaredMethod("doGet", 
                String.class, 
                javax.servlet.http.HttpServletRequest.class, 
                javax.servlet.http.HttpServletResponse.class);
            assertNotNull(doGet);
        });

        // Check doPost method
        assertDoesNotThrow(() -> {
            Method doPost = BaseWebService.class.getDeclaredMethod("doPost", 
                String.class, 
                javax.servlet.http.HttpServletRequest.class, 
                javax.servlet.http.HttpServletResponse.class);
            assertNotNull(doPost);
        });

        // Check doPut method
        assertDoesNotThrow(() -> {
            Method doPut = BaseWebService.class.getDeclaredMethod("doPut", 
                String.class, 
                javax.servlet.http.HttpServletRequest.class, 
                javax.servlet.http.HttpServletResponse.class);
            assertNotNull(doPut);
        });

        // Check doDelete method
        assertDoesNotThrow(() -> {
            Method doDelete = BaseWebService.class.getDeclaredMethod("doDelete", 
                String.class, 
                javax.servlet.http.HttpServletRequest.class, 
                javax.servlet.http.HttpServletResponse.class);
            assertNotNull(doDelete);
        });
    }

    /**
     * Tests that the abstract process method exists.
     */
    @Test
    void hasAbstractProcessMethod() throws Exception {
        Method processMethod = BaseWebService.class.getDeclaredMethod("process", 
            javax.servlet.http.HttpServletRequest.class, 
            javax.servlet.http.HttpServletResponse.class);
        
        assertNotNull(processMethod);
        assertTrue(java.lang.reflect.Modifier.isAbstract(processMethod.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isProtected(processMethod.getModifiers()));
    }

    /**
     * Tests class package and visibility.
     */
    @Test
    void classHasCorrectProperties() {
        assertEquals("com.etendoerp.metadata.http", BaseWebService.class.getPackage().getName());
        assertTrue(java.lang.reflect.Modifier.isPublic(BaseWebService.class.getModifiers()));
    }
}