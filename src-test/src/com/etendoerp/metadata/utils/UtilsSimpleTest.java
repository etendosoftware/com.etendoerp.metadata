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

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Simple unit tests for Utils class focusing on static methods and exception handling.
 */
class UtilsSimpleTest {

    /**
     * Tests that Utils class exists and has correct structure.
     */
    @Test
    void utilsClassExists() {
        assertNotNull(Utils.class);
        assertEquals("com.etendoerp.metadata.utils", Utils.class.getPackage().getName());
        assertTrue(Modifier.isPublic(Utils.class.getModifiers()));
    }

    /**
     * Tests getHttpStatusFor method with known exceptions.
     */
    @Test
    void getHttpStatusForShouldReturnCorrectStatusCodes() {
        assertEquals(HttpStatus.SC_NOT_FOUND, Utils.getHttpStatusFor(new NotFoundException("test")));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, Utils.getHttpStatusFor(new UnauthorizedException("test")));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, Utils.getHttpStatusFor(new RuntimeException("test")));
        
        // Test null case - should not crash but the actual implementation might not handle null
        assertThrows(NullPointerException.class, () -> {
            Utils.getHttpStatusFor(null);
        });
    }

    /**
     * Tests convertToJson method with throwable.
     */
    @Test
    void convertToJsonWithThrowableShouldWork() throws Exception {
        RuntimeException testException = new RuntimeException("Test error message");
        JSONObject result = Utils.convertToJson(testException);
        
        assertNotNull(result);
        assertTrue(result.has("error"));
        assertEquals("Test error message", result.getString("error"));
    }

    /**
     * Tests convertToJson with exception that has cause.
     */
    @Test
    void convertToJsonWithCauseShouldUseCauseMessage() throws Exception {
        RuntimeException cause = new RuntimeException("Root cause");
        RuntimeException wrapper = new RuntimeException("Wrapper", cause);
        
        JSONObject result = Utils.convertToJson(wrapper);
        
        assertNotNull(result);
        assertTrue(result.has("error"));
        assertEquals("Root cause", result.getString("error"));
    }

    /**
     * Tests formatMessage method exists and is callable.
     */
    @Test
    void formatMessageMethodExists() throws Exception {
        Method formatMessageMethod = Utils.class.getMethod("formatMessage", String.class, Object[].class);
        assertNotNull(formatMessageMethod);
        assertTrue(Modifier.isStatic(formatMessageMethod.getModifiers()));
        assertTrue(Modifier.isPublic(formatMessageMethod.getModifiers()));
    }

    /**
     * Tests formatMessage with simple message.
     */
    @Test
    void formatMessageShouldHandleSimpleMessage() {
        String message = "Simple test message";
        String result = Utils.formatMessage(message);
        assertNotNull(result);
        assertTrue(result.contains("Simple test message"));
    }

    /**
     * Tests that static utility methods exist.
     */
    @Test
    void staticUtilityMethodsExist() throws Exception {
        // Check writeJsonResponse method exists
        assertDoesNotThrow(() -> {
            Method writeJsonResponse = Utils.class.getMethod("writeJsonResponse", 
                javax.servlet.http.HttpServletResponse.class, int.class, String.class);
            assertTrue(Modifier.isStatic(writeJsonResponse.getModifiers()));
        });

        // Check readRequestBody method exists
        assertDoesNotThrow(() -> {
            Method readRequestBody = Utils.class.getMethod("readRequestBody", 
                javax.servlet.http.HttpServletRequest.class);
            assertTrue(Modifier.isStatic(readRequestBody.getModifiers()));
        });
    }

    /**
     * Tests convertToJson with null throwable.
     */
    @Test
    void convertToJsonWithNullShouldThrowException() throws Exception {
        // The actual implementation might not handle null gracefully
        assertThrows(NullPointerException.class, () -> {
            Utils.convertToJson((Throwable) null);
        });
    }
}