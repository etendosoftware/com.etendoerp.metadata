/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2024 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailSendService}.
 */
public class EmailSendServiceTest extends BaseMetadataServiceTest {

    private EmailSendService emailSendService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_SEND_PATH;
    }

    @Before
    public void setUpEmailSendService() {
        emailSendService = new EmailSendService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailSendServiceInstantiation() {
        assertNotNull("EmailSendService should be successfully instantiated", emailSendService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("tabId")).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean("success"));
            assertTrue("Should have error message", jsonResponse.has("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingToField() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter("recordId")).thenReturn("some-record");
        when(mockRequest.getParameter("tabId")).thenReturn("some-tab");
        when(mockRequest.getParameter("to")).thenReturn(null);
        when(mockRequest.getParameter("subject")).thenReturn("Test Subject");

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when to is missing", jsonResponse.getBoolean("success"));
            assertTrue("Should have error message", jsonResponse.has("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testProcessTabNotFound() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter("recordId")).thenReturn("some-record");
        when(mockRequest.getParameter("tabId")).thenReturn("non-existent-tab");
        when(mockRequest.getParameter("to")).thenReturn("dest@example.com");
        when(mockRequest.getParameter("subject")).thenReturn("Test Subject");

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean("success"));
            assertEquals("Tab not found.", jsonResponse.getString("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingSubjectField() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter("recordId")).thenReturn("some-record");
        when(mockRequest.getParameter("tabId")).thenReturn("some-tab");
        when(mockRequest.getParameter("to")).thenReturn("dest@example.com");
        when(mockRequest.getParameter("subject")).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when subject is missing", jsonResponse.getBoolean("success"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testProcessMultipartRequest_parsesAndValidatesParams()
            throws IOException, javax.servlet.ServletException {
        // multipart content type triggers createSecureTempDir + extractMultipartParams;
        // the mock request cannot supply a real multipart body, so parsing is caught
        // internally and continues to validateParams, which returns an error response.
        when(mockRequest.getContentType()).thenReturn("multipart/form-data; boundary=----xyz");
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("tabId")).thenReturn(null);
        when(mockRequest.getParameter("to")).thenReturn(null);
        when(mockRequest.getParameter("subject")).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when required params are missing", jsonResponse.getBoolean("success"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }
}
