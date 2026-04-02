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
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Tests for {@link EmailAttachmentService}.
 */
public class EmailAttachmentServiceTest extends BaseMetadataServiceTest {

    private static final String PARAM_RECORD_ID  = "recordId";
    private static final String PARAM_TAB_ID     = "tabId";
    private static final String KEY_SUCCESS      = "success";
    private static final String INVALID_JSON_MSG = "Response should be valid JSON: ";

    private EmailAttachmentService emailAttachmentService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_CONFIG_PATH + "/attachments";
    }

    @Before
    public void setUpService() {
        emailAttachmentService = new EmailAttachmentService(mockRequest, mockResponse);
    }

    @Test
    public void testInstantiation() {
        assertNotNull("EmailAttachmentService should be instantiated", emailAttachmentService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException, ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailAttachmentService.process();
        String output = responseWriter.toString();

        try {
            JSONObject json = new JSONObject(output);
            assertFalse("Success should be false for missing parameters", json.getBoolean(KEY_SUCCESS));
            assertTrue("Should contain error message", json.has("message"));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessTabNotFound() throws IOException, ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab-id");

        emailAttachmentService.process();
        String output = responseWriter.toString();

        try {
            JSONObject json = new JSONObject(output);
            assertFalse("Success should be false for non-existent tab", json.getBoolean(KEY_SUCCESS));
            assertEquals("Tab not found.", json.getString("message"));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingRecordIdOnly() throws IOException, ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("some-tab-id");

        emailAttachmentService.process();
        String output = responseWriter.toString();

        try {
            JSONObject json = new JSONObject(output);
            assertFalse(json.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessWithRealTab_returnsAttachments() throws IOException, ServletException {
        @SuppressWarnings("unchecked")
        List<Tab> tabs = OBDal.getInstance().createCriteria(Tab.class).setMaxResults(1).list();
        if (tabs.isEmpty()) {
            return;
        }
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(tabs.get(0).getId());

        emailAttachmentService.process();
        String output = responseWriter.toString();

        try {
            JSONObject json = new JSONObject(output);
            assertTrue("Success should be true when tab exists", json.getBoolean(KEY_SUCCESS));
            assertTrue("Should have attachments key", json.has("attachments"));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }
}
