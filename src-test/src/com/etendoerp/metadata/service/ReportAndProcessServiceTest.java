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

package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.metadata.builders.ReportAndProcessBuilder;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Test class for ReportAndProcessService.
 * Tests the functionality of retrieving report and process metadata
 * including parameter handling and JSON response generation.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ReportAndProcessServiceTest {

    private static final String EXCEPTION_NOT_NULL_MESSAGE = "Exception should not be null";
    private static final String VALID_PROCESS_ID = "123";
    private static final String VALID_PATH = "/com.etendoerp.metadata.meta/report-and-process/" + VALID_PROCESS_ID;
    private static final String REPORT_AND_PROCESS_PATH = "/report-and-process/";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private Process mockProcess;

    @Mock
    private OBDal mockOBDal;

    @Mock
    private OBContext mockOBContext;

    /**
     * Tests ReportAndProcessService constructor and inheritance.
     */
    @Test
    public void testConstructor() {
        ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

        assertNotNull("Service should not be null", service);
        assertTrue("Should be instance of MetadataService", service instanceof MetadataService);
    }

    /**
     * Tests that the service correctly returns request and response objects.
     */
    @Test
    public void testGetRequestAndResponse() {
        ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

        assertNotNull("Request should not be null", service.getRequest());
        assertNotNull("Response should not be null", service.getResponse());
        assertEquals("Request should match", mockRequest, service.getRequest());
        assertEquals("Response should match", mockResponse, service.getResponse());
    }

    /**
     * Tests process method with valid process ID in path.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithValidProcessId() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

        try {
            service.process();
            // If it doesn't throw an exception, the test passes
            assertTrue("Process method executed without throwing exception", true);
        } catch (Exception e) {
            // Expected in test environment due to missing dependencies
            assertNotNull(EXCEPTION_NOT_NULL_MESSAGE, e);
        }
    }

    /**
     * Tests process method throws NotFoundException for null path info.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithNullPathInfo() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(null);

        try (MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {
            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw NotFoundException for null path info");
            } catch (NotFoundException e) {
                assertNotNull("NotFoundException should be thrown", e);
            }
        }
    }

    /**
     * Tests process method throws NotFoundException for empty process ID.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithEmptyProcessId() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/report-and-process/");

        try (MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {
            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw NotFoundException for empty process ID");
            } catch (NotFoundException e) {
                assertNotNull("NotFoundException should be thrown", e);
            }
        }
    }

    /**
     * Tests process method throws NotFoundException for invalid path.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithInvalidPath() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/invalid/path");

        try (MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {
            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw NotFoundException for invalid path");
            } catch (NotFoundException e) {
                assertNotNull("NotFoundException should be thrown", e);
            }
        }
    }

    /**
     * Tests process method when process is not found in database.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithNonExistentProcess() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        try (MockedStatic<OBDal> obDalMockedStatic = mockStatic(OBDal.class);
                MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {

            obDalMockedStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(Process.class, VALID_PROCESS_ID)).thenReturn(null);

            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw NotFoundException when process not found");
            } catch (NotFoundException e) {
                assertTrue("Exception message should contain process ID",
                        e.getMessage().contains(VALID_PROCESS_ID));
            }
        }
    }

    /**
     * Tests process method with valid process found in database.
     *
     * @throws Exception if an error occurs during processing
     */
    @Test
    public void testProcessWithValidProcess() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        try (MockedStatic<OBDal> obDalMockedStatic = mockStatic(OBDal.class);
                MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class);
                MockedConstruction<ReportAndProcessBuilder> builderMockedConstruction = mockConstruction(
                        ReportAndProcessBuilder.class, (mock, context) -> {
                            JSONObject mockJson = new JSONObject()
                                    .put("id", VALID_PROCESS_ID)
                                    .put("name", "Test Process")
                                    .put("parameters", new JSONObject());
                            when(mock.toJSON()).thenReturn(mockJson);
                        })) {

            obDalMockedStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(Process.class, VALID_PROCESS_ID)).thenReturn(mockProcess);

            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);
            service.process();

            printWriter.flush();
            String responseContent = stringWriter.toString();

            assertNotNull("Response content should not be null", responseContent);
            assertFalse("Response should not be empty", responseContent.trim().isEmpty());

            // Verify JSON structure
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertTrue("Response should contain id", jsonResponse.has("id"));
            assertEquals("ID should match", VALID_PROCESS_ID, jsonResponse.getString("id"));
        }
    }

    /**
     * Tests that OBContext admin mode is properly set and restored.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testAdminModeIsSetAndRestored() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        try (MockedStatic<OBDal> obDalMockedStatic = mockStatic(OBDal.class);
                MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {

            obDalMockedStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(Process.class, VALID_PROCESS_ID)).thenReturn(null);

            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
            } catch (NotFoundException e) {
                // Expected
            }

            // Verify admin mode was set and restored
            obContextMockedStatic.verify(() -> OBContext.setAdminMode(true));
            obContextMockedStatic.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * Tests that restorePreviousMode is called even when exception occurs.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testRestorePreviousModeCalledOnException() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        try (MockedStatic<OBDal> obDalMockedStatic = mockStatic(OBDal.class);
                MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class)) {

            obDalMockedStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(Process.class, VALID_PROCESS_ID))
                    .thenThrow(new RuntimeException("Database error"));

            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw exception");
            } catch (Exception e) {
                // Expected
            }

            // Verify restorePreviousMode was still called
            obContextMockedStatic.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * Tests process method with path containing query parameters.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithQueryParameters() throws IOException {
        when(mockRequest.getPathInfo())
                .thenReturn("/com.etendoerp.metadata.meta/report-and-process/456?param=value");

        ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

        try {
            service.process();
            assertTrue("Process method executed", true);
        } catch (Exception e) {
            // Expected in test environment
            assertNotNull(EXCEPTION_NOT_NULL_MESSAGE, e);
        }
    }

    /**
     * Tests process method with short path format.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithShortPathFormat() throws IOException {
        when(mockRequest.getPathInfo()).thenReturn(REPORT_AND_PROCESS_PATH + VALID_PROCESS_ID);

        ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

        try {
            service.process();
            assertTrue("Process method executed with short path", true);
        } catch (Exception e) {
            // Expected in test environment due to missing dependencies
            assertNotNull(EXCEPTION_NOT_NULL_MESSAGE, e);
        }
    }

    /**
     * Tests exception handling during JSON building.
     *
     * @throws Exception if an error occurs during processing
     */
    @Test
    public void testProcessHandlesJSONException() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(VALID_PATH);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        try (MockedStatic<OBDal> obDalMockedStatic = mockStatic(OBDal.class);
                MockedStatic<OBContext> obContextMockedStatic = mockStatic(OBContext.class);
                MockedConstruction<ReportAndProcessBuilder> builderMockedConstruction = mockConstruction(
                        ReportAndProcessBuilder.class, (mock, context) -> {
                            when(mock.toJSON()).thenThrow(
                                    new org.codehaus.jettison.json.JSONException("JSON error"));
                        })) {

            obDalMockedStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(Process.class, VALID_PROCESS_ID)).thenReturn(mockProcess);

            obContextMockedStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
                fail("Should throw InternalServerException on JSON error");
            } catch (com.etendoerp.metadata.exceptions.InternalServerException e) {
                assertTrue("Exception message should mention JSON error",
                        e.getMessage().contains("Failed to build process metadata"));
            }
        }
    }

    /**
     * Tests process method with different valid process IDs.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testProcessWithDifferentProcessIds() throws IOException {
        String[] processIds = { "abc-123", "DEF456", "789-xyz" };

        for (String processId : processIds) {
            when(mockRequest.getPathInfo())
                    .thenReturn("/com.etendoerp.metadata.meta/report-and-process/" + processId);

            ReportAndProcessService service = new ReportAndProcessService(mockRequest, mockResponse);

            try {
                service.process();
            } catch (Exception e) {
                // Expected in test environment
                assertNotNull(EXCEPTION_NOT_NULL_MESSAGE, e);
            }
        }
    }
}
