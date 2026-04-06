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

import static com.etendoerp.metadata.MetadataTestConstants.KEY_SUCCESS;
import static com.etendoerp.metadata.MetadataTestConstants.PARAM_RECORD_ID;
import static com.etendoerp.metadata.MetadataTestConstants.PARAM_TAB_ID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailService}.
 */
public class EmailServiceTest extends BaseMetadataServiceTest {

    private EmailService emailService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_PATH;
    }

    @Before
    public void setUpEmailService() {
        emailService = new EmailService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailServiceInstantiation() {
        assertNotNull("EmailService should be successfully instantiated", emailService);
    }

    @Test
    public void testProcessMissingParameters() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean(KEY_SUCCESS));
        assertTrue("Should have error message", jsonResponse.has("message"));
    }

    @Test
    public void testProcessTabNotFound() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab");

        emailService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean(KEY_SUCCESS));
        assertEquals("Tab not found.", jsonResponse.getString("message"));
    }

    @Test
    public void testProcessRecordNotFound_withRealTab() throws Exception {
        @SuppressWarnings("unchecked")
        List<Tab> tabs = OBDal.getInstance().createCriteria(Tab.class).setMaxResults(1).list();
        if (tabs.isEmpty()) {
            return;
        }
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("00000000000000000000000000000001");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(tabs.get(0).getId());

        emailService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Validation should fail for non-existent record", jsonResponse.getBoolean(KEY_SUCCESS));
    }
}
