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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.model.Entity;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailConfigService}.
 */
public class EmailConfigServiceTest extends BaseMetadataServiceTest {

    private static final String PROP_EMAIL        = "email";
    private static final String PROP_NAME         = "name";
    private static final String PROP_DOC_TYPE     = "documentType";
    private static final String JSON_TO_NAME      = "toName";
    private static final String JSON_TEMPLATES    = "templates";
    private static final String SENDER_ADDR       = "sender@test.com";
    private static final String MSG_SUCCESS_TRUE  = "Success should be true";
    private static final String TABLE_NAME_TEST   = "TestTable";

    /** Subclass that overrides SMTP lookup so we can test the full executeEmailAction path. */
    private static class TestableEmailConfigService extends EmailConfigService {
        private final EmailServerConfiguration smtpConfig;

        TestableEmailConfigService(HttpServletRequest req, HttpServletResponse res,
                EmailServerConfiguration config) {
            super(req, res);
            this.smtpConfig = config;
        }

        @Override
        protected EmailServerConfiguration getSmtpConfig(Organization org) {
            return smtpConfig;
        }
    }

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
    public void testProcessMissingParameters() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailConfigService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean(KEY_SUCCESS));
        assertTrue("Should have error message", jsonResponse.has("message"));
    }

    @Test
    public void testProcessTabNotFound() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab-id");

        emailConfigService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean(KEY_SUCCESS));
        assertEquals("Tab not found.", jsonResponse.getString("message"));
    }

    @Test
    public void testProcessMissingTabIdOnly() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("some-record-id");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailConfigService.process();
        assertFalse(parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
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
        when(mockTab.getTable().getName()).thenReturn(TABLE_NAME_TEST);

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, SENDER_ADDR, "rec-123");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue(MSG_SUCCESS_TRUE, result.getBoolean(KEY_SUCCESS));
        assertEquals("to should be empty when no BP email property", "", result.getString("to"));
        assertEquals("toName should be empty when no name property", "", result.getString(JSON_TO_NAME));
        assertEquals("replyTo should be empty when no sales rep property", "", result.getString("replyTo"));
        assertEquals("senderAddress should match ctx value", SENDER_ADDR, result.getString("senderAddress"));
        assertNotNull("templates should be present", result.get(JSON_TEMPLATES));
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
        when(mockEntity.hasProperty(PROP_DOC_TYPE)).thenReturn(true);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);
        when(mockRecord.get(PROP_DOC_TYPE)).thenReturn("notABaseOBObject");

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("200");
        when(mockTab.getTable().getName()).thenReturn("InvoiceTable");

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "addr@test.com", "inv-001");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue(MSG_SUCCESS_TRUE, result.getBoolean(KEY_SUCCESS));
        assertNotNull("templates key should be present", result.get(JSON_TEMPLATES));
    }

    /**
     * Tests populateEmailConfig when documentType is a real BaseOBObject with a non-null id.
     * Exercises: getDocumentTypeId BOB path, getTemplateData (returns empty for unknown id),
     * getTemplatesJson (empty loop), loadEmailDefinition (early return on empty templates),
     * getPropertyString deep path via BP email property, and safeStr.
     */
    @Test
    public void testPopulateEmailConfig_withDocumentTypeAsBob() throws Exception {
        BaseOBObject mockDocType = mock(BaseOBObject.class);
        when(mockDocType.getId()).thenReturn("doc-type-id-test");

        Entity bpEntity = mock(Entity.class);
        when(bpEntity.hasProperty(PROP_EMAIL)).thenReturn(true);
        when(bpEntity.hasProperty(PROP_NAME)).thenReturn(true);
        BaseOBObject mockBp = mock(BaseOBObject.class);
        when(mockBp.getEntity()).thenReturn(bpEntity);
        when(mockBp.get(PROP_EMAIL)).thenReturn("partner@test.com");
        when(mockBp.get(PROP_NAME)).thenReturn("Test Partner");

        Entity mockEntity = mock(Entity.class);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(false);
        when(mockEntity.hasProperty(PROP_DOC_TYPE)).thenReturn(true);
        when(mockEntity.hasProperty("businessPartner")).thenReturn(true);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);
        when(mockRecord.get(PROP_DOC_TYPE)).thenReturn(mockDocType);
        when(mockRecord.get("businessPartner")).thenReturn(mockBp);

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("400");
        when(mockTab.getTable().getName()).thenReturn("OrderTable");

        Organization mockOrg = mock(Organization.class);
        when(mockOrg.getId()).thenReturn("org-test-id");

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "from@test.com", "order-001");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue(MSG_SUCCESS_TRUE, result.getBoolean(KEY_SUCCESS));
        assertEquals("to should come from BP email", "partner@test.com", result.getString("to"));
        assertEquals("toName should come from BP name", "Test Partner", result.getString(JSON_TO_NAME));
        assertNotNull("templates should be present", result.get(JSON_TEMPLATES));
    }

    /**
     * Tests populateEmailConfig when userContact has an email — covers the first branch
     * of getRecipientEmail/getRecipientName (returns non-empty from userContact).
     * Also exercises the documentNo property path in getRecordSubject/getReportFileName.
     */
    @Test
    public void testPopulateEmailConfig_withUserContactEmail() throws Exception {
        Entity ucEntity = mock(Entity.class);
        when(ucEntity.hasProperty(PROP_EMAIL)).thenReturn(true);
        when(ucEntity.hasProperty(PROP_NAME)).thenReturn(true);
        BaseOBObject mockUc = mock(BaseOBObject.class);
        when(mockUc.getEntity()).thenReturn(ucEntity);
        when(mockUc.get(PROP_EMAIL)).thenReturn("uc@test.com");
        when(mockUc.get(PROP_NAME)).thenReturn("UC Name");

        Entity mockEntity = mock(Entity.class);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(false);
        when(mockEntity.hasProperty("userContact")).thenReturn(true);
        when(mockEntity.hasProperty("documentNo")).thenReturn(true);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);
        when(mockRecord.get("userContact")).thenReturn(mockUc);
        when(mockRecord.get("documentNo")).thenReturn("DOC-001");

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("500");
        when(mockTab.getTable().getName()).thenReturn(TABLE_NAME_TEST);

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "from@test.com", "rec-001");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue(MSG_SUCCESS_TRUE, result.getBoolean(KEY_SUCCESS));
        assertEquals("to should come from userContact email", "uc@test.com", result.getString("to"));
        assertEquals("toName should come from userContact name", "UC Name", result.getString(JSON_TO_NAME));
        assertTrue("reportFileName should contain documentNo",
                result.getString("reportFileName").contains("DOC-001"));
    }

    /**
     * Tests the full executeEmailAction flow using a subclass that overrides SMTP lookup,
     * with a real Tab and real record from the database. Covers lines 61-64 in
     * executeEmailAction (including the write(result) call).
     */
    @Test
    public void testExecuteEmailAction_withValidSmtpAndRealData()
            throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        EmailServerConfiguration mockSmtp = mock(EmailServerConfiguration.class);
        when(mockSmtp.getSmtpServerSenderAddress()).thenReturn(SENDER_ADDR);

        TestableEmailConfigService testService =
                new TestableEmailConfigService(mockRequest, mockResponse, mockSmtp);

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.dataRecord.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());

        testService.process();
        assertNotNull("Response should be valid JSON", parseJsonResponse(responseWriter.toString()));
    }

    /**
     * Tests {@link EmailConfigService#loadEmailDefinition} when {@code templateData} is empty —
     * verifies that the method returns {@code null} without throwing.
     */
    @Test
    public void testLoadEmailDefinition_emptyTemplates_returnsNull() {
        JSONObject result = emailConfigService.loadEmailDefinition(null, "any-doc-type",
                mock(Organization.class), new org.openbravo.erpCommon.utility.reporting.TemplateData[0]);
        assertNull("Should return null for empty template array", result);
    }

    /**
     * Tests {@link EmailConfigService#getTemplatesJson} with a non-empty array —
     * verifies that each template is serialized with 'id' and 'name'.
     */
    @Test
    public void testGetTemplatesJson_withTemplates() throws Exception {
        org.openbravo.erpCommon.utility.reporting.TemplateData tpl =
                new org.openbravo.erpCommon.utility.reporting.TemplateData();
        tpl.id   = "tpl-001";
        tpl.name = "Invoice Template";

        org.codehaus.jettison.json.JSONArray templates = emailConfigService.getTemplatesJson(
                new org.openbravo.erpCommon.utility.reporting.TemplateData[]{ tpl });

        assertEquals("Should have one template", 1, templates.length());
        assertEquals("tpl-001", templates.getJSONObject(0).getString("id"));
        assertEquals("Invoice Template", templates.getJSONObject(0).getString("name"));
    }

    /**
     * Tests that when {@code tab.getWindow()} throws, {@code getRecordSubject} returns
     * an empty string (exercises the catch branch in that private helper).
     */
    @Test
    public void testPopulateEmailConfig_windowThrows_subjectFallsBackToEmpty() throws Exception {
        Entity mockEntity = mock(Entity.class);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(false);

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);

        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("600");
        when(mockTab.getTable().getName()).thenReturn(TABLE_NAME_TEST);
        when(mockTab.getWindow()).thenThrow(new RuntimeException("no window configured"));

        Organization mockOrg = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, SENDER_ADDR, "rec-600");

        JSONObject result = new JSONObject();
        emailConfigService.populateEmailConfig(result, ctx);

        assertTrue(MSG_SUCCESS_TRUE, result.getBoolean(KEY_SUCCESS));
        assertEquals("subject should be empty when window lookup throws", "", result.getString("subject"));
    }

    @Test
    public void testGetFallbackErrorMessage_returnsExpected() {
        assertEquals("Failed to load email configuration.", emailConfigService.getFallbackErrorMessage());
    }

    /**
     * Covers lines 63-64 of EmailConfigService.executeEmailAction (the populateEmailConfig +
     * write path) by overriding validateEmailRequest to bypass the real DB lookup.
     */
    @Test
    public void testExecuteEmailAction_successPath_withOverriddenValidation() throws Exception {
        Tab mockTab = mock(Tab.class, RETURNS_DEEP_STUBS);
        when(mockTab.getTable().getId()).thenReturn("259");
        when(mockTab.getTable().getName()).thenReturn("C_Invoice");
        when(mockTab.getWindow()).thenThrow(new RuntimeException("no window"));

        BaseOBObject mockRecord = mock(BaseOBObject.class);
        Entity mockEntity = mock(Entity.class);
        when(mockRecord.getEntity()).thenReturn(mockEntity);
        when(mockEntity.hasProperty(any(String.class))).thenReturn(false);
        Organization mockOrg = mock(Organization.class);

        final EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(mockTab, mockRecord, mockOrg, "s@t.com", "rec-1");

        EmailConfigService testService = new EmailConfigService(mockRequest, mockResponse) {
            @Override
            protected ValidationContext validateEmailRequest(JSONObject result) {
                return ctx;
            }
        };

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("rec-1");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("tab-1");

        testService.process();
        JSONObject json = parseJsonResponse(responseWriter.toString());
        assertNotNull("Response should be valid JSON", json);
    }
}
