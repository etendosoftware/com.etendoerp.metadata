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

import org.junit.jupiter.api.Test;

/**
 * Tests for Utils.formatMessage that execute real formatting logic.
 */
class UtilsFormatExecutionTest {

    /**
     * Tests formatMessage with simple string returns the string unchanged.
     */
    @Test
    void formatMessageWithSimpleStringReturnsUnchanged() {
        String simple = "Simple message without parameters";
        String result = Utils.formatMessage(simple);
        
        assertEquals(simple, result);
    }

    /**
     * Tests formatMessage with basic parameter substitution.
     */
    @Test
    void formatMessageWithBasicParametersWorks() {
        String template = "Hello {}";
        String result = Utils.formatMessage(template, "World");
        
        assertEquals("Hello World", result);
    }

    /**
     * Tests formatMessage with multiple parameters.
     */
    @Test
    void formatMessageWithMultipleParametersWorks() {
        String template = "User {} has {} points";
        String result = Utils.formatMessage(template, "John", 100);
        
        assertEquals("User John has 100 points", result);
    }

    /**
     * Tests formatMessage with no parameters on a template returns template.
     */
    @Test
    void formatMessageWithNoParametersReturnsTemplate() {
        String template = "Message with {} placeholder";
        String result = Utils.formatMessage(template);
        
        assertEquals(template, result);
    }

    /**
     * Tests formatMessage handles null parameters gracefully.
     */
    @Test
    void formatMessageWithNullParameterHandlesGracefully() {
        String template = "Value: {}";
        String result = Utils.formatMessage(template, (Object) null);
        
        assertNotNull(result);
        assertTrue(result.contains("null"));
    }

    /**
     * Tests formatMessage with empty string template.
     */
    @Test
    void formatMessageWithEmptyTemplateReturnsEmpty() {
        String result = Utils.formatMessage("", "param");
        
        assertEquals("", result);
    }

    /**
     * Tests formatMessage exception handling returns original message.
     */
    @Test
    void formatMessageWithExceptionReturnsOriginalMessage() {
        String template = "Invalid template {0} {1} {";
        String result = Utils.formatMessage(template, "param1", "param2");
        
        assertEquals(template, result);
    }
}