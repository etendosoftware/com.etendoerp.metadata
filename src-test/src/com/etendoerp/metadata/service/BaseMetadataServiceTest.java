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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.service;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.openbravo.dal.core.OBContext;
import org.openbravo.test.base.OBBaseTest;

/**
 * Base test class for MetadataService tests.
 * <p>
 * This class provides common setup and teardown functionality for all metadata service tests,
 * reducing code duplication and ensuring consistent test environment across all service test classes.
 * <p>
 * Subclasses should override {@link #getServicePath()} to provide the specific path for their service.
 *
 * @author Generated Test
 */
public abstract class BaseMetadataServiceTest extends OBBaseTest {

    protected HttpServletRequest mockRequest;
    protected HttpServletResponse mockResponse;
    protected StringWriter responseWriter;

    /**
     * Returns the service path for the specific service being tested.
     * This method must be implemented by subclasses to provide the correct path.
     *
     * @return the service path (e.g., "/meta/message", "/meta/menu", etc.)
     */
    protected abstract String getServicePath();

    /**
     * Sets up the test environment before each test method execution.
     * <p>
     * Initializes mock objects for HTTP request and response, configures the response writer
     * for capturing output, and sets up the necessary OBContext for database operations.
     * Also configures mock request paths using the service-specific path.
     */
    @Before
    public void setUp() throws Exception {
        setTestUserContext();

        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);

        responseWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(responseWriter);
        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockRequest.getMethod()).thenReturn("GET");
        lenient().when(mockRequest.getPathInfo()).thenReturn(getServicePath());
    }

    /**
     * Cleans up the test environment after each test method execution.
     * <p>
     * Restores the previous OBContext mode and clears any thread-local variables
     * to prevent memory leaks and ensure proper test isolation between test methods.
     */
    @After
    public void tearDown() {
        try {
            if (OBContext.getOBContext() != null) {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            // Ignore context restoration errors during cleanup
        }
        MetadataService.clear();
    }

    /**
     * Helper method to create an error response mock that throws IOException.
     *
     * @param exceptionMessage the exception message to throw
     * @return a mocked HttpServletResponse that throws IOException
     * @throws IOException when getWriter() is called
     */
    protected HttpServletResponse createErrorResponseMock(String exceptionMessage) throws IOException {
        HttpServletResponse errorResponse = mock(HttpServletResponse.class);
        when(errorResponse.getWriter()).thenThrow(new IOException(exceptionMessage));
        return errorResponse;
    }
}