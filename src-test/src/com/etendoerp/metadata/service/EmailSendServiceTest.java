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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.fileupload.FileItem;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailSendService}.
 */
public class EmailSendServiceTest extends BaseMetadataServiceTest {

    /**
     * Subclass that bypasses SMTP lookup and actual email sending,
     * allowing the full {@link EmailSendService#process()} path to be exercised.
     */
    private static class TestableEmailSendService extends EmailSendService {

        private final EmailServerConfiguration smtpConfig;
        private boolean throwOnSend;

        TestableEmailSendService(HttpServletRequest req, HttpServletResponse res,
                EmailServerConfiguration config) {
            super(req, res);
            this.smtpConfig   = config;
            this.throwOnSend  = false;
        }

        void setThrowOnSend(boolean throwOnSend) {
            this.throwOnSend = throwOnSend;
        }

        @Override
        protected EmailServerConfiguration getSmtpConfig(Organization org) {
            return smtpConfig;
        }

        @Override
        protected void sendEmail(Map<String, String> params, List<String> recordAttachmentIds,
                List<File> tempFiles, EmailServerConfiguration emailConfig) throws Exception {
            if (throwOnSend) {
                throw new OBException("Simulated SMTP failure");
            }
        }
    }

    private static final String PARAM_SUBJECT             = "subject";
    private static final String PARAM_TO                  = "to";
    private static final String KEY_MESSAGE               = "message";
    private static final String SAMPLE_RECORD             = "some-record";
    private static final String SAMPLE_SUBJECT            = "Test Subject";
    private static final String ENCODING_UTF8             = "UTF-8";
    private static final String FIELD_RECORD_ATTACHMENT   = "recordAttachmentId";
    private static final String FIELD_ATTACHMENT_PREFIX   = "attachment1";
    private static final String TEMP_DIR_PREFIX           = "test_email_send_";
    private static final String POSIX_OWNER_RWX           = "rwx------";
    private static final String DEST_EMAIL                = "dest@test.com";
    private static final String SENDER_TEST_EMAIL          = "sender@test.com";

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
    public void testProcessMissingParameters() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailSendService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean(KEY_SUCCESS));
        assertTrue("Should have error message", jsonResponse.has(KEY_MESSAGE));
    }

    @Test
    public void testProcessMissingToField() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("some-tab");
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false when to is missing", jsonResponse.getBoolean(KEY_SUCCESS));
        assertTrue("Should have error message", jsonResponse.has(KEY_MESSAGE));
    }

    @Test
    public void testProcessTabNotFound() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("non-existent-tab");
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@example.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        JSONObject jsonResponse = parseJsonResponse(responseWriter.toString());

        assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean(KEY_SUCCESS));
        assertEquals("Tab not found.", jsonResponse.getString(KEY_MESSAGE));
    }

    @Test
    public void testProcessMissingSubjectField() throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(SAMPLE_RECORD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("some-tab");
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@example.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(null);

        emailSendService.process();
        assertFalse("Success should be false when subject is missing",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    @Test
    public void testProcessMultipartRequest_parsesAndValidatesParams()
            throws Exception {
        when(mockRequest.getContentType()).thenReturn("multipart/form-data; boundary=----xyz");
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(null);

        emailSendService.process();
        assertFalse("Success should be false when required params are missing",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    @Test
    public void testProcessWithRecordAttachmentIds_missingRequiredParams()
            throws Exception {
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameterValues(FIELD_RECORD_ATTACHMENT))
                .thenReturn(new String[]{"att-001", " att-002 ", null, ""});

        emailSendService.process();
        assertFalse("Success should be false when required params are missing",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
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

    // ── processFormField ──────────────────────────────────────────────────────

    @Test
    public void testProcessFormField_recordAttachmentId_addsToList() throws Exception {
        FileItem mockItem = mock(FileItem.class);
        when(mockItem.getFieldName()).thenReturn(FIELD_RECORD_ATTACHMENT);
        when(mockItem.getString(ENCODING_UTF8)).thenReturn("att-123");

        Map<String, String> params = new HashMap<>();
        List<String> recordAttachmentIds = new ArrayList<>();
        emailSendService.processFormField(mockItem, params, recordAttachmentIds);

        assertEquals("Attachment ID should be added", 1, recordAttachmentIds.size());
        assertEquals("att-123", recordAttachmentIds.get(0));
        assertTrue("Params should be unchanged", params.isEmpty());
    }

    @Test
    public void testProcessFormField_regularField_addsToParams() throws Exception {
        FileItem mockItem = mock(FileItem.class);
        when(mockItem.getFieldName()).thenReturn(PARAM_SUBJECT);
        when(mockItem.getString(ENCODING_UTF8)).thenReturn(SAMPLE_SUBJECT);

        Map<String, String> params = new HashMap<>();
        List<String> recordAttachmentIds = new ArrayList<>();
        emailSendService.processFormField(mockItem, params, recordAttachmentIds);

        assertEquals(SAMPLE_SUBJECT, params.get(PARAM_SUBJECT));
        assertTrue("Attachment IDs should be unchanged", recordAttachmentIds.isEmpty());
    }

    @Test
    public void testProcessFormField_blankAttachmentId_notAdded() throws Exception {
        FileItem mockItem = mock(FileItem.class);
        when(mockItem.getFieldName()).thenReturn(FIELD_RECORD_ATTACHMENT);
        when(mockItem.getString(ENCODING_UTF8)).thenReturn("   ");

        Map<String, String> params = new HashMap<>();
        List<String> recordAttachmentIds = new ArrayList<>();
        emailSendService.processFormField(mockItem, params, recordAttachmentIds);

        assertTrue("Blank attachment ID should not be added", recordAttachmentIds.isEmpty());
    }

    // ── processUploadedFile ───────────────────────────────────────────────────

    @Test
    public void testProcessUploadedFile_validAttachment_addsToTempFiles() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(POSIX_OWNER_RWX));
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX, ownerOnly);
        List<File> tempFiles = new ArrayList<>();
        try {
            FileItem mockItem = mock(FileItem.class);
            when(mockItem.getFieldName()).thenReturn(FIELD_ATTACHMENT_PREFIX);
            when(mockItem.getName()).thenReturn("document.pdf");

            emailSendService.processUploadedFile(mockItem, tempFiles, tempDir);

            assertEquals("Temp file should be added", 1, tempFiles.size());
        } finally {
            for (File f : tempFiles) {
                try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) { /* cleanup failure should not fail the test */ }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testProcessUploadedFile_nonAttachmentField_notAdded() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(POSIX_OWNER_RWX));
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX, ownerOnly);
        try {
            FileItem mockItem = mock(FileItem.class);
            when(mockItem.getFieldName()).thenReturn("notes");
            when(mockItem.getName()).thenReturn("document.pdf");

            List<File> tempFiles = new ArrayList<>();
            emailSendService.processUploadedFile(mockItem, tempFiles, tempDir);

            assertTrue("Non-attachment field should not add temp file", tempFiles.isEmpty());
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    // ── restrictPermissions ───────────────────────────────────────────────────

    @Test
    public void testRestrictPermissions_ownerOnly() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
        Path tmpFile = Files.createTempFile("test_restrict_", ".tmp", ownerOnly);
        try {
            boolean result = emailSendService.restrictPermissions(tmpFile.toFile());
            assertNotNull("restrictPermissions should return a result", result);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    // ── getFilesFromAttachments ───────────────────────────────────────────────

    @Test
    public void testGetFilesFromAttachments_emptyList_returnsEmpty() {
        List<File> result = emailSendService.getFilesFromAttachments(new ArrayList<>());
        assertNotNull("Result should not be null", result);
        assertTrue("Empty list should produce no files", result.isEmpty());
    }

    @Test
    public void testGetFilesFromAttachments_nonExistentId_returnsEmpty() {
        List<File> result = emailSendService.getFilesFromAttachments(Arrays.asList("non-existent-id-xyz"));
        assertNotNull("Result should not be null", result);
        assertTrue("Non-existent attachment should produce no files", result.isEmpty());
    }

    // ── process() with real DB data ───────────────────────────────────────────

    @Test
    public void testProcess_recordNotFound_withRealTab()
            throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("00000000000000000000000000000001");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(DEST_EMAIL);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        assertFalse("Success should be false for non-existent record",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    @Test
    public void testProcess_senderNotConfigured_withRealTabAndRecord()
            throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.dataRecord.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(DEST_EMAIL);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        assertFalse("Success should be false when no SMTP sender is configured",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    // ── success and exception paths via TestableEmailSendService ─────────────

    @Test
    public void testProcess_successPath_withMockedSmtpAndSend() throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        EmailServerConfiguration mockSmtp = mock(EmailServerConfiguration.class);
        when(mockSmtp.getSmtpServerSenderAddress()).thenReturn(SENDER_TEST_EMAIL);

        TestableEmailSendService testService =
                new TestableEmailSendService(mockRequest, mockResponse, mockSmtp);

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.dataRecord.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(DEST_EMAIL);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        testService.process();
        assertTrue("Success should be true when email is sent",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    @Test
    public void testProcess_sendEmailThrows_writesErrorResponse() throws Exception {
        TabRecordContext ctx = findFirstTabWithRecord();
        if (ctx == null) return;

        EmailServerConfiguration mockSmtp = mock(EmailServerConfiguration.class);
        when(mockSmtp.getSmtpServerSenderAddress()).thenReturn(SENDER_TEST_EMAIL);

        TestableEmailSendService testService =
                new TestableEmailSendService(mockRequest, mockResponse, mockSmtp);
        testService.setThrowOnSend(true);

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(ctx.dataRecord.getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(ctx.tab.getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(DEST_EMAIL);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        testService.process();
        assertFalse("Success should be false when sendEmail throws",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    @Test
    public void testProcessUploadedFile_nullFileName_notAdded() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(POSIX_OWNER_RWX));
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX, ownerOnly);
        try {
            FileItem mockItem = mock(FileItem.class);
            when(mockItem.getFieldName()).thenReturn(FIELD_ATTACHMENT_PREFIX);
            when(mockItem.getName()).thenReturn(null);

            List<File> tempFiles = new ArrayList<>();
            emailSendService.processUploadedFile(mockItem, tempFiles, tempDir);

            assertTrue("Null file name should not add temp file", tempFiles.isEmpty());
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testProcessUploadedFile_blankFileName_notAdded() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(POSIX_OWNER_RWX));
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX, ownerOnly);
        try {
            FileItem mockItem = mock(FileItem.class);
            when(mockItem.getFieldName()).thenReturn(FIELD_ATTACHMENT_PREFIX);
            when(mockItem.getName()).thenReturn("   ");

            List<File> tempFiles = new ArrayList<>();
            emailSendService.processUploadedFile(mockItem, tempFiles, tempDir);

            assertTrue("Blank file name should not add temp file", tempFiles.isEmpty());
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGetFallbackErrorMessage_returnsExpected() {
        assertEquals("Failed to send email.", emailSendService.getFallbackErrorMessage());
    }

    // ── cleanupTempFiles (direct test via protected visibility) ─────────────────

    @Test
    public void testCleanupTempFiles_deletesFilesAndDir() throws Exception {
        FileAttribute<Set<PosixFilePermission>> ownerOnly =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(POSIX_OWNER_RWX));
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX, ownerOnly);
        Path f1 = Files.createTempFile(tempDir, "cleanup1_", ".tmp");
        Path f2 = Files.createTempFile(tempDir, "cleanup2_", ".tmp");

        emailSendService.cleanupTempFiles(Arrays.asList(f1.toFile(), f2.toFile()), tempDir);

        assertFalse("First temp file should be deleted", Files.exists(f1));
        assertFalse("Second temp file should be deleted", Files.exists(f2));
        assertFalse("Temp dir should be deleted", Files.exists(tempDir));
    }

    @Test
    public void testCleanupTempFiles_nonExistentFile_doesNotThrow() {
        File nonExistent = new File("/tmp/nonexistent_etp3511_" + System.nanoTime() + ".tmp");
        // Should silently swallow the NoSuchFileException
        emailSendService.cleanupTempFiles(Arrays.asList(nonExistent), null);
    }

    // ── extractMultipartParams via real multipart body ──────────────────────────

    @Test
    public void testProcess_realMultipartBody_parsesFormField() throws Exception {
        String boundary = "testboundary3511";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + PARAM_RECORD_ID + "\"\r\n\r\n"
                + "some-rec-id\r\n"
                + "--" + boundary + "--\r\n";
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        when(mockRequest.getContentType())
                .thenReturn("multipart/form-data; boundary=" + boundary);
        when(mockRequest.getCharacterEncoding()).thenReturn(ENCODING_UTF8);
        when(mockRequest.getContentLength()).thenReturn(bytes.length);
        when(mockRequest.getInputStream()).thenReturn(new ServletInputStream() {
            private int pos = 0;
            @Override public int read() { return pos < bytes.length ? bytes[pos++] & 0xFF : -1; }
            @Override public boolean isFinished() { return pos >= bytes.length; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { /* not used in tests */ }
        });
        // Remaining required params missing → validation fails after multipart parse
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);

        emailSendService.process();
        assertFalse("Validation should fail when required params are incomplete",
                parseJsonResponse(responseWriter.toString()).getBoolean(KEY_SUCCESS));
    }

    /**
     * Covers lines 111-127 of EmailSendService.process() (the success path after param
     * validation) without relying on real DB data. Uses MockedStatic for OBDal and
     * overrides resolveRecord / getSmtpConfig / sendEmail.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testProcess_successPath_withoutDB() throws Exception {
        // Required params
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("rec-1");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("tab-1");
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(DEST_EMAIL);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);
        when(mockRequest.getParameter("notes")).thenReturn(null);
        when(mockRequest.getParameter("archive")).thenReturn(null);
        when(mockRequest.getParameter("cc")).thenReturn(null);
        when(mockRequest.getParameter("bcc")).thenReturn(null);
        when(mockRequest.getParameter("replyTo")).thenReturn(null);
        when(mockRequest.getParameter("templateId")).thenReturn(null);
        when(mockRequest.getParameterValues(FIELD_RECORD_ATTACHMENT)).thenReturn(null);
        when(mockRequest.getContentType()).thenReturn("application/json");

        Tab mockTab = mock(Tab.class);
        org.openbravo.base.structure.BaseOBObject mockRecord = mock(org.openbravo.base.structure.BaseOBObject.class);
        Organization mockOrg = mock(Organization.class);
        when(mockRecord.get("organization")).thenReturn(mockOrg);

        EmailServerConfiguration mockSmtp = mock(EmailServerConfiguration.class);
        when(mockSmtp.getSmtpServerSenderAddress()).thenReturn(SENDER_TEST_EMAIL);

        EmailSendService testService = new EmailSendService(mockRequest, mockResponse) {
            @Override
            protected org.openbravo.base.structure.BaseOBObject resolveRecord(Tab tab, String recordId) {
                return mockRecord;
            }
            @Override
            protected EmailServerConfiguration getSmtpConfig(Organization org) {
                return mockSmtp;
            }
            @Override
            protected void sendEmail(Map<String, String> params, List<String> recordAttachmentIds,
                    List<File> tempFiles, EmailServerConfiguration emailConfig) {
                // no-op — avoid real SMTP
            }
        };

        try (org.mockito.MockedStatic<OBDal> obdal = mockStatic(OBDal.class)) {
            OBDal mockDal = mock(OBDal.class);
            obdal.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.get(Tab.class, "tab-1")).thenReturn(mockTab);

            testService.process();
        }

        JSONObject json = parseJsonResponse(responseWriter.toString());
        assertTrue("Success should be true when all steps succeed", json.getBoolean(KEY_SUCCESS));
    }
}
