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
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailSendService}.
 */
public class EmailSendServiceTest extends BaseMetadataServiceTest {

    private static final String PARAM_RECORD_ID  = "recordId";
    private static final String PARAM_TAB_ID     = "tabId";
    private static final String PARAM_SUBJECT    = "subject";
    private static final String KEY_SUCCESS      = "success";
    private static final String KEY_MESSAGE      = "message";
    private static final String SAMPLE_RECORD    = "some-record";
    private static final String INVALID_JSON_MSG = "Response should be valid JSON: ";

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
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean(KEY_SUCCESS));
            assertTrue("Should have error message", jsonResponse.has(KEY_MESSAGE));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingToField() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("some-tab");
        when(mockRequest.getParameter("to")).thenReturn(null);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn("Test Subject");

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when to is missing", jsonResponse.getBoolean(KEY_SUCCESS));
            assertTrue("Should have error message", jsonResponse.has(KEY_MESSAGE));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessTabNotFound() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab");
        when(mockRequest.getParameter("to")).thenReturn("dest@example.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn("Test Subject");

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean(KEY_SUCCESS));
            assertEquals("Tab not found.", jsonResponse.getString(KEY_MESSAGE));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingSubjectField() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("some-tab");
        when(mockRequest.getParameter("to")).thenReturn("dest@example.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when subject is missing", jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessMultipartRequest_parsesAndValidatesParams()
            throws IOException, javax.servlet.ServletException {
        when(mockRequest.getContentType()).thenReturn("multipart/form-data; boundary=----xyz");
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter("to")).thenReturn(null);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when required params are missing", jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessWithRecordAttachmentIds_missingRequiredParams()
            throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameterValues("recordAttachmentId"))
                .thenReturn(new String[]{"att-001", " att-002 ", null, ""});

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when required params are missing", jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testResolveRecord_nullTable_returnsNull() {
        Tab mockTab = mock(Tab.class);
        when(mockTab.getTable()).thenReturn(null);
        assertNull("Should return null when tab has no table", emailSendService.resolveRecord(mockTab, "some-id"));
    }

    @Test
    public void testResolveRecord_noEntityMapping_returnsNull() {
        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getDBTableName()).thenReturn("non_existent_table_xyz_abc");
        assertNull("Should return null when entity mapping does not exist", emailSendService.resolveRecord(mockTab, "some-id"));
    }
}
