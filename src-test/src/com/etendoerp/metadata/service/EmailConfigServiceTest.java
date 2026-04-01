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
import org.openbravo.base.model.Entity;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

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

    /**
     * Tests populateEmailConfig directly using fully mocked dependencies.
     * Entity reports no properties, so all field helpers short-circuit to empty strings
     * and docTypeId resolves to null, exercising the else-branch for templates.
     */
    @Test
    public void testPopulateEmailConfig_withMockedRecord() throws Exception {
        Entity mockEntity = mock(Entity.class);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(false);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("100");
        when(mockTab.getTable().getName()).thenReturn("TestTable");

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "sender@test.com", "rec-123");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue("Success should be true", result.getBoolean(KEY_SUCCESS));
        assertEquals("to should be empty when no BP email property", "", result.getString("to"));
        assertEquals("toName should be empty when no name property", "", result.getString("toName"));
        assertEquals("replyTo should be empty when no sales rep property", "", result.getString("replyTo"));
        assertEquals("senderAddress should match ctx value", "sender@test.com", result.getString("senderAddress"));
        assertNotNull("templates should be present", result.get("templates"));
        assertNotNull("recordAttachments should be present", result.get("recordAttachments"));
    }

    /**
     * Tests populateEmailConfig when the record has a documentType property that
     * returns a non-BOB value, so getDocumentTypeId returns null and
     * the null docTypeId branch is followed (templates = empty array).
     */
    @Test
    public void testPopulateEmailConfig_docTypePropertyPresent_returnsNonBob() throws Exception {
        Entity mockEntity = mock(Entity.class);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(true);
        when(mockEntity.hasProperty("documentType")).thenReturn(true);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);
        when(mockRecord.get("documentType")).thenReturn("notABaseOBObject");

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("200");
        when(mockTab.getTable().getName()).thenReturn("InvoiceTable");

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "addr@test.com", "inv-001");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue("Success should be true", result.getBoolean(KEY_SUCCESS));
        assertNotNull("templates key should be present", result.get("templates"));
    }
}
