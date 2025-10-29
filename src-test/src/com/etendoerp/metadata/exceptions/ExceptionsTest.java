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

package com.etendoerp.metadata.exceptions;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for all metadata exceptions.
 * Tests both constructors (with and without message) and default message behavior.
 */
public class ExceptionsTest {

    @Test
    public void testInternalServerException() {
        // Test with custom message
        InternalServerException exceptionWithMessage = new InternalServerException("Custom error");
        assertEquals("Custom error", exceptionWithMessage.getMessage());

        // Test with empty message (should use default)
        InternalServerException exceptionWithEmptyMessage = new InternalServerException("");
        assertEquals("Internal server error", exceptionWithEmptyMessage.getMessage());

        // Test default constructor
        InternalServerException exceptionDefault = new InternalServerException();
        assertEquals("Internal server error", exceptionDefault.getMessage());
    }

    @Test
    public void testMethodNotAllowedException() {
        // Test with custom message
        MethodNotAllowedException exceptionWithMessage = new MethodNotAllowedException("Custom method error");
        assertEquals("Custom method error", exceptionWithMessage.getMessage());

        // Test with empty message (should use default)
        MethodNotAllowedException exceptionWithEmptyMessage = new MethodNotAllowedException("");
        assertEquals("Method not allowed", exceptionWithEmptyMessage.getMessage());

        // Test default constructor
        MethodNotAllowedException exceptionDefault = new MethodNotAllowedException();
        assertEquals("Method not allowed", exceptionDefault.getMessage());
    }

    @Test
    public void testNotFoundException() {
        // Test with custom message
        NotFoundException exceptionWithMessage = new NotFoundException("Resource not found");
        assertEquals("Resource not found", exceptionWithMessage.getMessage());

        // Test with empty message (should use default)
        NotFoundException exceptionWithEmptyMessage = new NotFoundException("");
        assertEquals("Not found", exceptionWithEmptyMessage.getMessage());

        // Test default constructor
        NotFoundException exceptionDefault = new NotFoundException();
        assertEquals("Not found", exceptionDefault.getMessage());
    }

    @Test
    public void testUnauthorizedException() {
        // Test with custom message
        UnauthorizedException exceptionWithMessage = new UnauthorizedException("Access denied");
        assertEquals("Access denied", exceptionWithMessage.getMessage());

        // Test with empty message (should use default)
        UnauthorizedException exceptionWithEmptyMessage = new UnauthorizedException("");
        assertEquals("Invalid or missing token", exceptionWithEmptyMessage.getMessage());

        // Test default constructor
        UnauthorizedException exceptionDefault = new UnauthorizedException();
        assertEquals("Invalid or missing token", exceptionDefault.getMessage());
    }

    @Test
    public void testUnprocessableContentException() {
        // Test with custom message
        UnprocessableContentException exceptionWithMessage = new UnprocessableContentException("Invalid data");
        assertEquals("Invalid data", exceptionWithMessage.getMessage());

        // Test with empty message (should use default)
        UnprocessableContentException exceptionWithEmptyMessage = new UnprocessableContentException("");
        assertEquals("Unprocessable content", exceptionWithEmptyMessage.getMessage());

        // Test default constructor
        UnprocessableContentException exceptionDefault = new UnprocessableContentException();
        assertEquals("Unprocessable content", exceptionDefault.getMessage());
    }
}