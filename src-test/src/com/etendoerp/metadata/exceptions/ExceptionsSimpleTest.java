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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Additional simple tests for custom exception classes.
 */
class ExceptionsSimpleTest {
    private final static String MODULE_EXCEPTIONS = "com.etendoerp.metadata.exceptions";

    /**
     * Tests NotFoundException can be created with message.
     */
    @Test
    void notFoundExceptionWithMessage() {
        String message = "Resource not found";
        NotFoundException exception = new NotFoundException(message);
        
        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }

    /**
     * Tests UnauthorizedException can be created with message.
     */
    @Test
    void unauthorizedExceptionWithMessage() {
        String message = "Access denied";
        UnauthorizedException exception = new UnauthorizedException(message);
        
        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }

    /**
     * Tests MethodNotAllowedException can be created with message.
     */
    @Test
    void methodNotAllowedExceptionWithMessage() {
        String message = "Method not allowed";
        MethodNotAllowedException exception = new MethodNotAllowedException(message);
        
        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }

    /**
     * Tests UnprocessableContentException can be created with message.
     */
    @Test
    void unprocessableContentExceptionWithMessage() {
        String message = "Content cannot be processed";
        UnprocessableContentException exception = new UnprocessableContentException(message);
        
        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }

    /**
     * Tests InternalServerException can be created with message.
     */
    @Test
    void internalServerExceptionWithMessage() {
        String message = "Internal server error";
        InternalServerException exception = new InternalServerException(message);
        
        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof RuntimeException);
    }

    /**
     * Tests exception class packages.
     */
    @Test
    void exceptionClassesHaveCorrectPackage() {
        assertEquals(MODULE_EXCEPTIONS, NotFoundException.class.getPackage().getName());
        assertEquals(MODULE_EXCEPTIONS, UnauthorizedException.class.getPackage().getName());
        assertEquals(MODULE_EXCEPTIONS, MethodNotAllowedException.class.getPackage().getName());
        assertEquals(MODULE_EXCEPTIONS, UnprocessableContentException.class.getPackage().getName());
        assertEquals(MODULE_EXCEPTIONS, InternalServerException.class.getPackage().getName());
    }

    /**
     * Tests exceptions can be thrown and caught.
     */
    @Test
    void exceptionsCanBeThrownAndCaught() {
        assertThrows(NotFoundException.class, () -> {
            throw new NotFoundException("test");
        });

        assertThrows(UnauthorizedException.class, () -> {
            throw new UnauthorizedException("test");
        });

        assertThrows(MethodNotAllowedException.class, () -> {
            throw new MethodNotAllowedException("test");
        });

        assertThrows(UnprocessableContentException.class, () -> {
            throw new UnprocessableContentException("test");
        });

        assertThrows(InternalServerException.class, () -> {
            throw new InternalServerException("test");
        });
    }
}