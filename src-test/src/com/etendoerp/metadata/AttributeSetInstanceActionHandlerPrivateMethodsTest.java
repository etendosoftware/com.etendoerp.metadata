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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Tests for the private utility methods of {@link AttributeSetInstanceActionHandler}:
 * {@code convertToClassicDateFormat} and {@code replace}.
 */
class AttributeSetInstanceActionHandlerPrivateMethodsTest {

    private static final String EXPIRATION_DATE_VAL = "2025-06-15";
    private static final String METHOD_CONVERT_DATE = "convertToClassicDateFormat";
    private static final String METHOD_REPLACE = "replace";

    @SuppressWarnings("java:S3011")
    private Method getConvertDateMethod() throws ReflectiveOperationException {
        Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod(METHOD_CONVERT_DATE, String.class);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("java:S3011")
    private Method getReplaceMethod() throws ReflectiveOperationException {
        Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod(METHOD_REPLACE, String.class);
        method.setAccessible(true);
        return method;
    }

    @Test
    void convertToClassicDateFormatConvertsValidDate() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, EXPIRATION_DATE_VAL);
        assertEquals("15-06-2025", result);
    }

    @Test
    void convertToClassicDateFormatReturnsEmptyForEmpty() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, "");
        assertEquals("", result);
    }

    @Test
    void convertToClassicDateFormatReturnsNullForNull() throws Exception {
        assertNull(getConvertDateMethod().invoke(null, (String) null));
    }

    @Test
    void convertToClassicDateFormatReturnsUnchangedForNonMatchingFormat() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, "15/06/2025");
        assertEquals("15/06/2025", result);
    }

    @Test
    void replaceRemovesSpecialCharacters() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, "Test Name (With #Chars, & More)");
        assertEquals("TestNameWithCharsMore", result);
    }

    @Test
    void replaceReturnsEmptyForNull() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, (String) null);
        assertEquals("", result);
    }

    @Test
    void replaceReturnsUnchangedForCleanString() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, "CleanName");
        assertEquals("CleanName", result);
    }
}
