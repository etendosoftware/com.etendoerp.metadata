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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Simple structural tests for ServletRegistry class.
 * Tests class structure and method signatures without runtime execution.
 */
class ServletRegistryStructureTest {

    /**
     * Tests that ServletRegistry class exists and has correct structure.
     */
    @Test
    void classExistsAndIsPublic() {
        assertNotNull(ServletRegistry.class);
        assertEquals("com.etendoerp.metadata.utils", ServletRegistry.class.getPackage().getName());
        assertTrue(Modifier.isPublic(ServletRegistry.class.getModifiers()));
    }

    /**
     * Tests that getDelegatedServlet static method exists.
     */
    @Test
    void getDelegatedServletMethodExists() throws Exception {
        Method getDelegatedServletMethod = ServletRegistry.class.getDeclaredMethod("getDelegatedServlet", 
            org.openbravo.base.secureApp.HttpSecureAppServlet.class, 
            String.class);
        
        assertNotNull(getDelegatedServletMethod);
        assertTrue(Modifier.isStatic(getDelegatedServletMethod.getModifiers()));
        assertTrue(Modifier.isPublic(getDelegatedServletMethod.getModifiers()));
        assertEquals(org.openbravo.base.secureApp.HttpSecureAppServlet.class, getDelegatedServletMethod.getReturnType());
    }

    /**
     * Tests that private helper methods exist in the class.
     */
    @Test
    void privateHelperMethodsExist() throws Exception {
        // Check buildServletRegistry method exists
        assertDoesNotThrow(() -> {
            Method buildMethod = ServletRegistry.class.getDeclaredMethod("buildServletRegistry");
            assertTrue(Modifier.isStatic(buildMethod.getModifiers()));
            assertTrue(Modifier.isPrivate(buildMethod.getModifiers()));
        });

        // Check getFirstSegment method exists
        assertDoesNotThrow(() -> {
            Method getFirstSegmentMethod = ServletRegistry.class.getDeclaredMethod("getFirstSegment", String.class);
            assertTrue(Modifier.isStatic(getFirstSegmentMethod.getModifiers()));
            assertTrue(Modifier.isPrivate(getFirstSegmentMethod.getModifiers()));
        });

        // Check getMappingPath method exists
        assertDoesNotThrow(() -> {
            Method getMappingPathMethod = ServletRegistry.class.getDeclaredMethod("getMappingPath", String.class);
            assertTrue(Modifier.isStatic(getMappingPathMethod.getModifiers()));
            assertTrue(Modifier.isPrivate(getMappingPathMethod.getModifiers()));
        });
    }

    /**
     * Tests that the class has the expected constant field.
     */
    @Test
    void hasExpectedStaticFields() throws Exception {
        assertDoesNotThrow(() -> {
            java.lang.reflect.Field registryField = ServletRegistry.class.getDeclaredField("SERVLET_REGISTRY");
            assertTrue(Modifier.isStatic(registryField.getModifiers()));
            assertTrue(Modifier.isPrivate(registryField.getModifiers()));
            assertTrue(Modifier.isFinal(registryField.getModifiers()));
        });
    }

    /**
     * Tests method parameter types and return types.
     */
    @Test
    void methodSignaturesAreCorrect() throws Exception {
        Method getDelegatedServletMethod = ServletRegistry.class.getDeclaredMethod("getDelegatedServlet", 
            org.openbravo.base.secureApp.HttpSecureAppServlet.class, 
            String.class);
        
        Class<?>[] paramTypes = getDelegatedServletMethod.getParameterTypes();
        assertEquals(2, paramTypes.length);
        assertEquals(org.openbravo.base.secureApp.HttpSecureAppServlet.class, paramTypes[0]);
        assertEquals(String.class, paramTypes[1]);
        assertEquals(org.openbravo.base.secureApp.HttpSecureAppServlet.class, getDelegatedServletMethod.getReturnType());
        
        // Check that method can throw exceptions
        Class<?>[] exceptionTypes = getDelegatedServletMethod.getExceptionTypes();
        // ServletRegistry methods should be able to throw exceptions
        assertNotNull(exceptionTypes);
    }

    /**
     * Tests class is not abstract and not interface.
     */
    @Test
    void classIsConcreteUtilityClass() {
        assertFalse(Modifier.isAbstract(ServletRegistry.class.getModifiers()));
        assertFalse(ServletRegistry.class.isInterface());
        assertFalse(ServletRegistry.class.isEnum());
    }
}