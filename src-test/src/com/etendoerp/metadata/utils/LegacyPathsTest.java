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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * Test class for LegacyPaths utility class.
 * Verifies that all constants are correctly defined and that the class
 * follows utility class patterns (cannot be instantiated).
 */
class LegacyPathsTest {

    /**
     * Verifies that the constants have the expected values.
     */
    @Test
    void testConstants() {
        assertEquals("/utility/UsedByLink.html", LegacyPaths.USED_BY_LINK);
        assertEquals("/ad_forms/about.html", LegacyPaths.ABOUT_MODAL);
        assertEquals("/ad_actionButton/ActionButton_Responser.html", LegacyPaths.MANUAL_PROCESS);
    }

    /**
     * Verifies that the class cannot be instantiated via reflection
     * and that the constructor is private.
     */
    @Test
    void testConstructorIsPrivate() throws NoSuchMethodException {
        Constructor<LegacyPaths> constructor = LegacyPaths.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()), "Constructor should be private");

        constructor.setAccessible(true);
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        assertEquals("This is a utility class and cannot be instantiated", exception.getCause().getMessage());
    }
}
