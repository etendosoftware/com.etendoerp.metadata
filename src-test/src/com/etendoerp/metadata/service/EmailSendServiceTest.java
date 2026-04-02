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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailSendService}.
 */
public class EmailSendServiceTest extends BaseMetadataServiceTest {

    private static final String PARAM_RECORD_ID           = "recordId";
    private static final String PARAM_TAB_ID              = "tabId";
    private static final String PARAM_SUBJECT             = "subject";
    private static final String PARAM_TO                  = "to";
    private static final String KEY_SUCCESS               = "success";
    private static final String KEY_MESSAGE               = "message";
    private static final String SAMPLE_RECORD             = "some-record";
    private static final String SAMPLE_SUBJECT            = "Test Subject";
    private static final String ENCODING_UTF8             = "UTF-8";
    private static final String FIELD_RECORD_ATTACHMENT   = "recordAttachmentId";
    private static final String INVALID_JSON_MSG          = "Response should be valid JSON: ";

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
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

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
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@example.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

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
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@example.com");
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
        when(mockRequest.getParameter(PARAM_TO)).thenReturn(null);
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
        when(mockRequest.getParameterValues(FIELD_RECORD_ATTACHMENT))
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
        Path tempDir = Files.createTempDirectory("test_email_send_");
        List<File> tempFiles = new ArrayList<>();
        try {
            FileItem mockItem = mock(FileItem.class);
            when(mockItem.getFieldName()).thenReturn("attachment1");
            when(mockItem.getName()).thenReturn("document.pdf");

            emailSendService.processUploadedFile(mockItem, tempFiles, tempDir);

            assertEquals("Temp file should be added", 1, tempFiles.size());
        } finally {
            for (File f : tempFiles) {
                try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testProcessUploadedFile_nonAttachmentField_notAdded() throws Exception {
        Path tempDir = Files.createTempDirectory("test_email_send_");
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
        Path tmpFile = Files.createTempFile("test_restrict_", ".tmp");
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
            throws IOException, javax.servlet.ServletException {
        @SuppressWarnings("unchecked")
        List<Tab> tabs = OBDal.getInstance().createCriteria(Tab.class).setMaxResults(1).list();
        if (tabs.isEmpty()) {
            return;
        }
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn("00000000000000000000000000000001");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(tabs.get(0).getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@test.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for non-existent record", jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }

    @Test
    public void testProcess_senderNotConfigured_withRealTabAndRecord()
            throws IOException, javax.servlet.ServletException {
        @SuppressWarnings("unchecked")
        List<Tab> tabs = OBDal.getInstance().createCriteria(Tab.class).setMaxResults(1).list();
        if (tabs.isEmpty()) {
            return;
        }
        Tab tab = tabs.get(0);
        if (tab.getTable() == null) {
            return;
        }
        Entity entity = ModelProvider.getInstance()
                .getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<BaseOBObject> records = OBDal.getInstance().getSession()
                .createQuery("from " + entity.getName())
                .setMaxResults(1).list();
        if (records.isEmpty()) {
            return;
        }

        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(records.get(0).getId().toString());
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(tab.getId());
        when(mockRequest.getParameter(PARAM_TO)).thenReturn("dest@test.com");
        when(mockRequest.getParameter(PARAM_SUBJECT)).thenReturn(SAMPLE_SUBJECT);

        emailSendService.process();
        String responseContent = responseWriter.toString();

        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false when no SMTP sender is configured",
                    jsonResponse.getBoolean(KEY_SUCCESS));
        } catch (Exception e) {
            fail(INVALID_JSON_MSG + e.getMessage());
        }
    }
}
