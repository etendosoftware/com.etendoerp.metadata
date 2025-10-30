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

package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Simple unit tests for FieldBuilderWithoutColumn class.
 * Tests basic inheritance and class structure without complex mocking.
 */
class FieldBuilderWithoutColumnSimpleTest {

    /**
     * Tests that FieldBuilderWithoutColumn class exists and can be instantiated conceptually.
     */
    @Test
    void classExistsAndIsProperSubclass() {
        // Verify the class exists in the inheritance hierarchy
        assertTrue(FieldBuilderWithoutColumn.class.getSuperclass().equals(FieldBuilder.class));
        
        // Verify it's not abstract
        assertFalse(java.lang.reflect.Modifier.isAbstract(FieldBuilderWithoutColumn.class.getModifiers()));
    }

    /**
     * Tests that the class has the expected constructor.
     */
    @Test 
    void hasExpectedConstructor() throws Exception {
        // Verify constructor exists with expected parameters
        assertDoesNotThrow(() -> {
            FieldBuilderWithoutColumn.class.getDeclaredConstructor(
                org.openbravo.model.ad.ui.Field.class,
                org.openbravo.model.ad.access.FieldAccess.class
            );
        });
    }

    /**
     * Tests basic class properties.
     */
    @Test
    void classHasCorrectProperties() {
        // Verify package
        assertEquals("com.etendoerp.metadata.builders", FieldBuilderWithoutColumn.class.getPackage().getName());
        
        // Verify it's public
        assertTrue(java.lang.reflect.Modifier.isPublic(FieldBuilderWithoutColumn.class.getModifiers()));
    }

    /**
     * Tests that it extends the correct parent class.
     */
    @Test
    void extendsFieldBuilder() {
        assertTrue(FieldBuilder.class.isAssignableFrom(FieldBuilderWithoutColumn.class));
    }
}