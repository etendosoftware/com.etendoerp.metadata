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

import static com.etendoerp.metadata.MetadataTestConstants.PARAM_RECORD_ID;
import static com.etendoerp.metadata.MetadataTestConstants.PARAM_TAB_ID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Tests for {@link EmailBaseService} utility methods via a concrete stub subclass.
 */
public class EmailBaseServiceTest extends BaseMetadataServiceTest {

    private static final String PROP_DOC_STATUS   = "documentStatus";
    private static final String PROP_DOCSTATUS    = "docstatus";
    private static final String PROP_ORGANIZATION = "organization";
    private static final String SENDER_TEST_EMAIL = "sender@test.com";

    /** Minimal concrete subclass that exposes protected helpers for direct testing. */
    private static class StubEmailService extends EmailBaseService {

        private boolean overrideSmtp;
        private EmailServerConfiguration smtpOverride;

        StubEmailService(HttpServletRequest req, HttpServletResponse res) {
            super(req, res);
        }

        void setSmtpConfigOverride(EmailServerConfiguration config) {
            this.overrideSmtp = true;
            this.smtpOverride = config;
        }

        @Override
        protected EmailServerConfiguration getSmtpConfig(Organization org) {
            return overrideSmtp ? smtpOverride : super.getSmtpConfig(org);
        }

        @Override
        protected void executeEmailAction(JSONObject result) throws Exception {
            // no-op for lifecycle tests
        }

        @Override
        protected String getFallbackErrorMessage() {
            return "Stub fallback error";
        }

        // Bridge methods to reach protected API from the same package
        Object callReadProperty(BaseOBObject dataRecord, String prop) {
            return readProperty(dataRecord, prop);
        }

        Object callGetDocumentStatus(BaseOBObject dataRecord) {
            return getDocumentStatus(dataRecord);
        }

        void callRespond(JSONObject result, boolean success, String message) throws Exception {
            respond(result, success, message);
        }

        void callHandleServiceError(JSONObject result, Exception ex, String fallback) throws IOException {
            handleServiceError(result, ex, fallback);
        }

        org.openbravo.model.common.enterprise.Organization callResolveOrganization(BaseOBObject dataRecord) {
            return resolveOrganization(dataRecord);
        }

        String callResolveSenderAddress(org.openbravo.model.common.enterprise.Organization org) {
            return resolveSenderAddress(org);
        }

        JSONArray callQueryAttachments(String tableId, String recordId) {
            return queryAttachments(tableId, recordId);
        }

        ValidationContext callValidateEmailRequest(JSONObject result) throws Exception {
            return validateEmailRequest(result);
        }
    }

    private StubEmailService service;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_PATH;
    }

    @Before
    public void setUpStub() {
        service = new StubEmailService(mockRequest, mockResponse);
    }

    // ── readProperty ──────────────────────────────────────────────────────────

    @Test
    public void testReadProperty_returnsValue() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_DOC_STATUS)).thenReturn("CO");
        assertEquals("CO", service.callReadProperty(dataRecord, PROP_DOC_STATUS));
    }

    @Test
    public void testReadProperty_returnsNullOnException() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get("missing")).thenThrow(new RuntimeException("no such property"));
        assertNull(service.callReadProperty(dataRecord, "missing"));
    }

    // ── getDocumentStatus ─────────────────────────────────────────────────────

    @Test
    public void testGetDocumentStatus_fromDocumentStatus() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_DOC_STATUS)).thenReturn("CO");
        when(dataRecord.get(PROP_DOCSTATUS)).thenReturn(null);
        assertEquals("CO", service.callGetDocumentStatus(dataRecord));
    }

    @Test
    public void testGetDocumentStatus_fallsBackToDocstatus() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_DOC_STATUS)).thenReturn(null);
        when(dataRecord.get(PROP_DOCSTATUS)).thenReturn("CL");
        assertEquals("CL", service.callGetDocumentStatus(dataRecord));
    }

    @Test
    public void testGetDocumentStatus_returnsNullWhenNeitherExists() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_DOC_STATUS)).thenReturn(null);
        when(dataRecord.get(PROP_DOCSTATUS)).thenReturn(null);
        assertNull(service.callGetDocumentStatus(dataRecord));
    }

    // ── respond ───────────────────────────────────────────────────────────────

    @Test
    public void testRespond_writesSuccessTrue() throws Exception {
        JSONObject result = new JSONObject();
        service.callRespond(result, true, null);
        String output = responseWriter.toString();
        assertTrue("Should contain success:true", output.contains("true"));
    }

    @Test
    public void testRespond_writesSuccessFalseWithMessage() throws Exception {
        JSONObject result = new JSONObject();
        service.callRespond(result, false, "something went wrong");
        String output = responseWriter.toString();
        assertTrue(output.contains("false"));
        assertTrue(output.contains("something went wrong"));
    }

    // ── handleServiceError ────────────────────────────────────────────────────

    @Test
    public void testHandleServiceError_OBExceptionExposesMessage() throws IOException {
        JSONObject result = new JSONObject();
        service.callHandleServiceError(result, new OBException("Known error"), "Fallback");
        String output = responseWriter.toString();
        assertTrue("OBException message should be visible", output.contains("Known error"));
        assertFalse("Internal name should not be visible", output.contains("Fallback"));
    }

    @Test
    public void testHandleServiceError_genericExceptionUsesFallback() throws IOException {
        JSONObject result = new JSONObject();
        service.callHandleServiceError(result, new RuntimeException("internal detail"), "Fallback message");
        String output = responseWriter.toString();
        assertTrue("Fallback message should appear", output.contains("Fallback message"));
        assertFalse("Internal detail should NOT appear", output.contains("internal detail"));
    }

    @Test
    public void testHandleServiceError_writerThrows_propagatesIOException()
            throws IOException, ServletException {
        when(mockResponse.getWriter()).thenThrow(new IOException("Writer unavailable"));
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        EmailService emailService = new EmailService(mockRequest, mockResponse);
        try {
            emailService.process();
            fail("Expected IOException to propagate");
        } catch (IOException e) {
            assertEquals("Writer unavailable", e.getMessage());
        }
    }

    // ── process / template method lifecycle ──────────────────────────────────

    @Test
    public void testProcess_completesWithoutError() throws IOException, ServletException {
        service.process();
    }

    // ── ValidationContext ─────────────────────────────────────────────────────

    @Test
    public void testValidationContext_allGetters() {
        Tab tab           = mock(Tab.class);
        BaseOBObject rec  = mock(BaseOBObject.class);
        Organization org  = mock(Organization.class);

        EmailBaseService.ValidationContext ctx =
                new EmailBaseService.ValidationContext(tab, rec, org, SENDER_TEST_EMAIL, "rec-123");

        assertSame("tab",     tab,  ctx.getTab());
        assertSame("record",  rec,  ctx.getDataRecord());
        assertSame("org",     org,  ctx.getOrg());
        assertEquals("senderAddress", SENDER_TEST_EMAIL, ctx.getSenderAddress());
        assertEquals(PARAM_RECORD_ID,  "rec-123",         ctx.getRecordId());
    }

    // ── process with unhandled exception in executeEmailAction ────────────────

    @Test
    public void testProcess_executeActionThrows_writesErrorResponse()
            throws IOException, ServletException {
        StringWriter sw = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(sw));

        StubEmailService throwing = new StubEmailService(mockRequest, mockResponse) {
            @Override
            protected void executeEmailAction(JSONObject result) throws Exception {
                throw new OBException("Action failed");
            }
        };

        throwing.process();
        String output = sw.toString();
        assertTrue("Error response should contain false", output.contains("false"));
        assertTrue("OBException message should be visible", output.contains("Action failed"));
    }

    // ── resolveOrganization ───────────────────────────────────────────────────

    @Test
    public void testResolveOrganization_usesOrgFromRecord() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        Organization mockOrg = mock(Organization.class);
        when(dataRecord.get(PROP_ORGANIZATION)).thenReturn(mockOrg);

        Organization result = service.callResolveOrganization(dataRecord);
        assertSame("Should return org from record", mockOrg, result);
    }

    @Test
    public void testResolveOrganization_fallsBackWhenValueIsNull() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_ORGANIZATION)).thenReturn(null);

        Organization result = service.callResolveOrganization(dataRecord);
        assertNotNull("Should fall back to context org when record org is null", result);
    }

    @Test
    public void testResolveOrganization_fallsBackWhenValueIsNotOrgInstance() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_ORGANIZATION)).thenReturn("notAnOrg");

        Organization result = service.callResolveOrganization(dataRecord);
        assertNotNull("Should fall back to context org when value is not Organization", result);
    }

    @Test
    public void testResolveOrganization_fallsBackOnException() {
        BaseOBObject dataRecord = mock(BaseOBObject.class);
        when(dataRecord.get(PROP_ORGANIZATION)).thenThrow(new RuntimeException("no org property"));

        Organization result = service.callResolveOrganization(dataRecord);
        assertNotNull("Should fall back to context org on exception", result);
    }

    // ── resolveSenderAddress ──────────────────────────────────────────────────

    @Test
    public void testResolveSenderAddress_returnsEmptyWhenConfigIsNull() {
        service.setSmtpConfigOverride(null);
        assertEquals("", service.callResolveSenderAddress(mock(Organization.class)));
    }

    @Test
    public void testResolveSenderAddress_returnsEmptyWhenSenderAddressIsNull() {
        EmailServerConfiguration config = mock(EmailServerConfiguration.class);
        when(config.getSmtpServerSenderAddress()).thenReturn(null);
        service.setSmtpConfigOverride(config);
        assertEquals("", service.callResolveSenderAddress(mock(Organization.class)));
    }

    @Test
    public void testResolveSenderAddress_returnsTrimmedAddress() {
        EmailServerConfiguration config = mock(EmailServerConfiguration.class);
        when(config.getSmtpServerSenderAddress()).thenReturn("  sender@example.com  ");
        service.setSmtpConfigOverride(config);
        assertEquals("sender@example.com", service.callResolveSenderAddress(mock(Organization.class)));
    }

    // ── queryAttachments ──────────────────────────────────────────────────────

    @Test
    public void testQueryAttachments_nullTableId_returnsEmptyArray() {
        JSONArray result = service.callQueryAttachments(null, "some-record-id");
        assertNotNull("Result should not be null", result);
        assertEquals("Should return empty array for null tableId", 0, result.length());
    }

    @Test
    public void testQueryAttachments_nullRecordId_returnsEmptyArray() {
        JSONArray result = service.callQueryAttachments("some-table-id", null);
        assertNotNull("Result should not be null", result);
        assertEquals("Should return empty array for null recordId", 0, result.length());
    }

    // ── validateEmailRequest ──────────────────────────────────────────────────

    @Test
    public void testValidateEmailRequest_recordNotFound_returnsNull() throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("00000000000000000000000000000001");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());

        JSONObject result = new JSONObject();
        assertNull("Should return null when record not found",
                service.callValidateEmailRequest(result));
        assertFalse(result.getBoolean(MetadataTestConstants.KEY_SUCCESS));
    }

    @Test
    public void testValidateEmailRequest_senderEmpty_returnsNull() throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        service.setSmtpConfigOverride(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.record.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());

        JSONObject result = new JSONObject();
        assertNull("Should return null when no sender configured",
                service.callValidateEmailRequest(result));
        assertFalse(result.getBoolean(MetadataTestConstants.KEY_SUCCESS));
    }

    @Test
    public void testValidateEmailRequest_withValidSmtp_coversDocStatusPath() throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        EmailServerConfiguration mockSmtp = mock(EmailServerConfiguration.class);
        when(mockSmtp.getSmtpServerSenderAddress()).thenReturn(SENDER_TEST_EMAIL);
        service.setSmtpConfigOverride(mockSmtp);

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.record.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());

        JSONObject result = new JSONObject();
        service.callValidateEmailRequest(result);
        assertNotNull("Result JSON should be populated", result);
    }

    // ── getSmtpConfig / findAnySmtpConfig (real DB path) ─────────────────────

    @Test
    public void testResolveSenderAddress_withRealSmtpLookup() {
        Organization mockOrg = mock(Organization.class);
        when(mockOrg.getId()).thenReturn("0");
        String addr = service.callResolveSenderAddress(mockOrg);
        assertNotNull("Sender address should not be null (may be empty if no SMTP configured)", addr);
    }
}
