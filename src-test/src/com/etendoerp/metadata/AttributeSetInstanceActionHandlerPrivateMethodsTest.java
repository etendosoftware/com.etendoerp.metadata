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
