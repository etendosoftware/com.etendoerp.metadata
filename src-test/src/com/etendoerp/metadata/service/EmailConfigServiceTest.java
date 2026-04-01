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
 * Test class for {@link EmailConfigService}.
 */
public class EmailConfigServiceTest extends BaseMetadataServiceTest {

    private static final String PARAM_RECORD_ID  = "recordId";
    private static final String PARAM_TAB_ID     = "tabId";
    private static final String KEY_SUCCESS      = "success";
    private static final String INVALID_JSON_MSG = "Response should be valid JSON: ";

    private EmailConfigService emailConfigService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_CONFIG_PATH;
    }

    @Before
    public void setUpEmailConfigService() {
        emailConfigService = new EmailConfigService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailConfigServiceInstantiation() {
        assertNotNull("EmailConfigService should be successfully instantiated", emailConfigService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailConfigService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean(KEY_SUCCESS));
            assertTrue("Should have error message", jsonResponse.has("message"));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessTabNotFound() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab-id");

        emailConfigService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean(KEY_SUCCESS));
            assertEquals("Tab not found.", jsonResponse.getString("message"));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcessMissingTabIdOnly() throws IOException, javax.servlet.ServletException {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailConfigService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse(jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }
}
