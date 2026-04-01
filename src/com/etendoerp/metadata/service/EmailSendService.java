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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.poc.EmailInfo;
import org.openbravo.erpCommon.utility.poc.EmailManager;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Sends an email for an Order or Invoice record using EmailManager.
 */
public class EmailSendService extends MetadataService {

    private static final String ATTACH_PATH_PROP = "attach.path";
    private static final String SUBJECT = "subject";
    private static final String NOTES = "notes";
    private static final String TO = "to";

    /**
     * Constructor for EmailSendService.
     * @param request The HttpServletRequest object.
     * @param response The HttpServletResponse object.
     */
    public EmailSendService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        JSONObject result = new JSONObject();
        List<File> tempFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            OBContext.setAdminMode(true);
            Map<String, String> params = new HashMap<>();
            List<String> recordAttachmentIds = new ArrayList<>();

            if (isMultipartRequest()) {
                tempDir = createSecureTempDir();
                extractMultipartParams(params, recordAttachmentIds, tempFiles, tempDir);
            }
            populateParamsFromRequest(params, recordAttachmentIds);

            if (!validateParams(result, params)) {
                return;
            }

            Tab tab = OBDal.getInstance().get(Tab.class, params.get(TAB_ID));
            if (tab == null) {
                handleErrorResponse(result, "Tab not found.");
                return;
            }

            BaseOBObject dataRecord = getRecord(tab, params.get(RECORD_ID));
            Organization org = getRecordOrganization(dataRecord);
            EmailServerConfiguration emailConfig = getEmailConfiguration(org);

            if (emailConfig == null || emailConfig.getSmtpServerSenderAddress() == null || emailConfig.getSmtpServerSenderAddress().trim().isEmpty()) {
                handleErrorResponse(result, "No sender defined. Please check Email Server configuration in Client settings.");
                return;
            }

            sendEmail(params, recordAttachmentIds, tempFiles, emailConfig);
            result.put(SUCCESS, true);
            write(result);

        } catch (Exception e) {
            handleProcessError("EmailSendService", result, e);
        } finally {
            OBContext.restorePreviousMode();
            cleanupTempFiles(tempFiles, tempDir);
        }
    }

    private Path createSecureTempDir() throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")
            );
            return Files.createTempDirectory("etendo_email_attachments_", attrs);
        } catch (UnsupportedOperationException e) {
            return Files.createTempDirectory("etendo_email_attachments_");
        }
    }

    @Override
    protected void execute(JSONObject result) throws Exception {
        // Not used as EmailSendService overrides process() for custom cleanup
    }

    private void populateParamsFromRequest(Map<String, String> params, List<String> recordAttachmentIds) {
        String[] fieldNames = { RECORD_ID, TAB_ID, TO, SUBJECT, NOTES, "archive", "cc", "bcc", "replyTo", "templateId" };
        for (String field : fieldNames) {
            if (isNullOrEmpty(params.get(field))) {
                String value = getRequest().getParameter(field);
                if (value != null) params.put(field, value);
            }
        }
        extractAttachmentIdsFromRequest(recordAttachmentIds);
    }

    private void extractAttachmentIdsFromRequest(List<String> recordAttachmentIds) {
        if (recordAttachmentIds.isEmpty()) {
            String[] rawIds = getRequest().getParameterValues("recordAttachmentId");
            if (rawIds != null) {
                for (String id : rawIds) {
                    if (id != null && !id.trim().isEmpty()) {
                        recordAttachmentIds.add(id.trim());
                    }
                }
            }
        }
    }

    private boolean validateParams(JSONObject result, Map<String, String> params) throws IOException {
        if (isNullOrEmpty(params.get(RECORD_ID)) || isNullOrEmpty(params.get(TAB_ID))
                || isNullOrEmpty(params.get(TO)) || isNullOrEmpty(params.get(SUBJECT))) {
            handleErrorResponse(result, "Missing required parameters: recordId, tabId, to, subject.");
            return false;
        }
        return true;
    }

    private void sendEmail(Map<String, String> params, List<String> recordAttachmentIds, List<File> tempFiles, EmailServerConfiguration emailConfig) throws Exception {
        List<File> allAttachments = new ArrayList<>(tempFiles);
        allAttachments.addAll(getFilesFromAttachments(recordAttachmentIds));

        EmailInfo email = new EmailInfo.Builder()
                .setRecipientTO(params.get(TO))
                .setRecipientCC(params.getOrDefault("cc", ""))
                .setRecipientBCC(params.getOrDefault("bcc", ""))
                .setReplyTo(params.getOrDefault("replyTo", ""))
                .setSubject(params.get(SUBJECT))
                .setContent(params.getOrDefault(NOTES, ""))
                .setContentType("text/plain; charset=utf-8")
                .setAttachments(allAttachments)
                .setSentDate(new Date())
                .build();

        EmailManager.sendEmail(emailConfig, email);
    }

    private List<File> getFilesFromAttachments(List<String> recordAttachmentIds) {
        List<File> recordFiles = new ArrayList<>();
        if (recordAttachmentIds.isEmpty()) return recordFiles;
        try {
            String attachPath = OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty(ATTACH_PATH_PROP);
            if (attachPath == null) return recordFiles;

            for (String attId : recordAttachmentIds) {
                Attachment att = OBDal.getInstance().get(Attachment.class, attId);
                if (att != null && att.getPath() != null && att.getName() != null) {
                    File attFile = new File(attachPath + File.separator + att.getPath() + File.separator + att.getName());
                    if (attFile.exists() && attFile.isFile()) {
                        recordFiles.add(attFile);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load record attachments: " + e.getMessage());
        }
        return recordFiles;
    }

    private void cleanupTempFiles(List<File> tempFiles, Path tempDir) {
        for (File f : tempFiles) {
            try {
                Files.delete(f.toPath());
            } catch (Exception e) {
                logger.debug("Could not delete temp file: " + f.getAbsolutePath());
            }
        }
        if (tempDir != null) {
            try {
                Files.delete(tempDir);
            } catch (Exception e) {
                logger.debug("Could not delete temp directory: " + tempDir.toAbsolutePath());
            }
        }
    }

    private boolean isMultipartRequest() {
        String ct = getRequest().getContentType();
        return ct != null && ct.toLowerCase().startsWith("multipart/");
    }

    private void extractMultipartParams(Map<String, String> params, List<String> recordAttachmentIds, List<File> tempFiles, Path tempDir) {
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            List<FileItem> items = upload.parseRequest(getRequest());
            if (items == null) return;

            for (FileItem item : items) {
                if (item.isFormField()) {
                    processFormField(item, params, recordAttachmentIds);
                } else {
                    processUploadedFile(item, tempFiles, tempDir);
                }
            }
        } catch (Exception e) {
            logger.error("[EmailSend] Could not parse multipart request: " + e.getMessage(), e);
        }
    }

    private void processFormField(FileItem item, Map<String, String> params, List<String> recordAttachmentIds) throws Exception {
        if ("recordAttachmentId".equals(item.getFieldName())) {
            String val = item.getString("UTF-8");
            if (val != null && !val.trim().isEmpty()) recordAttachmentIds.add(val.trim());
        } else {
            params.put(item.getFieldName(), item.getString("UTF-8"));
        }
    }

    private void processUploadedFile(FileItem item, List<File> tempFiles, Path tempDir) throws Exception {
        String fileName = item.getName();
        if (item.getFieldName() != null && item.getFieldName().startsWith("attachment") && fileName != null && !fileName.trim().isEmpty()) {
            Path tempPath = Files.createTempFile(tempDir, "attach_", "_" + fileName);
            File tempFile = tempPath.toFile();
            tempFile.deleteOnExit();
            item.write(tempFile);
            tempFiles.add(tempFile);
        }
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
