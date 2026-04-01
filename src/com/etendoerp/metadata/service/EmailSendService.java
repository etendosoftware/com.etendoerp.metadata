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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.poc.EmailInfo;
import org.openbravo.erpCommon.utility.poc.EmailManager;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Sends an email for an Order or Invoice record using EmailManager.
 */
public class EmailSendService extends EmailBaseService {

    private static final String ATTACH_PATH_PROP = "attach.path";
    private static final String SUBJECT          = "subject";
    private static final String NOTES            = "notes";
    private static final String TO               = "to";
    private static final String CC               = "cc";
    private static final String BCC              = "bcc";
    private static final String REPLY_TO         = "replyTo";
    private static final String ARCHIVE          = "archive";
    private static final String TEMPLATE_ID      = "templateId";
    private static final String RECORD_ID_PARAM  = "recordId";
    private static final String TAB_ID_PARAM     = "tabId";

    /**
     * Creates a new EmailSendService for the given request/response pair.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public EmailSendService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    /**
     * Overrides the base template method to add temp-file cleanup in the finally block.
     */
    @Override
    public void process() throws IOException, ServletException {
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

            Tab tab = OBDal.getInstance().get(Tab.class, params.get(TAB_ID_PARAM));
            if (tab == null) {
                respond(result, false, "Tab not found.");
                return;
            }

            BaseOBObject dataRecord = resolveRecord(tab, params.get(RECORD_ID_PARAM));
            if (dataRecord == null) {
                respond(result, false, "Record not found.");
                return;
            }

            Organization org = resolveOrganization(dataRecord);
            EmailServerConfiguration emailConfig = getSmtpConfig(org);

            if (emailConfig == null || StringUtils.isBlank(emailConfig.getSmtpServerSenderAddress())) {
                respond(result, false, "No sender defined. Please check Email Server configuration in Client settings.");
                return;
            }

            sendEmail(params, recordAttachmentIds, tempFiles, emailConfig);
            result.put(KEY_SUCCESS, true);
            write(result);

        } catch (Exception e) {
            handleServiceError(result, e, getFallbackErrorMessage());
        } finally {
            OBContext.restorePreviousMode();
            cleanupTempFiles(tempFiles, tempDir);
        }
    }

    @Override
    protected void executeEmailAction(JSONObject result) throws Exception {
        // Not used — process() is overridden directly for temp-file cleanup.
    }

    @Override
    protected String getFallbackErrorMessage() {
        return "Failed to send email.";
    }

    // ── Record resolution ──────────────────────────────────────────────────────

    protected BaseOBObject resolveRecord(Tab tab, String recordId) {
        if (tab.getTable() == null) return null;
        Entity entity = ModelProvider.getInstance()
                .getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) return null;
        return OBDal.getInstance().get(entity.getName(), recordId);
    }

    // ── Temp directory ─────────────────────────────────────────────────────────

    private Path createSecureTempDir() throws IOException {
        String attachPathStr = OBPropertiesProvider.getInstance()
                .getOpenbravoProperties().getProperty(ATTACH_PATH_PROP);
        Path rootTmp = attachPathStr != null
                ? new File(attachPathStr, "tmp").toPath()
                : new File(System.getProperty("user.home"), ".etendo/tmp").toPath();

        if (!Files.exists(rootTmp)) {
            Files.createDirectories(rootTmp);
            if (!restrictPermissions(rootTmp.toFile())) {
                logger.warn("Could not fully restrict permissions on root temp directory: {}", rootTmp);
            }
        }

        FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------"));
        try {
            return Files.createTempDirectory(rootTmp, "email_", attrs);
        } catch (UnsupportedOperationException e) {
            Path dir = Files.createTempDirectory(rootTmp, "email_");
            if (!restrictPermissions(dir.toFile())) {
                logger.warn("Could not fully restrict permissions on temporary directory: {}", dir);
            }
            return dir;
        }
    }

    private boolean restrictPermissions(File file) {
        boolean ok = file.setReadable(false, false);
        ok &= file.setWritable(false, false);
        ok &= file.setExecutable(false, false);
        ok &= file.setReadable(true, true);
        ok &= file.setWritable(true, true);
        ok &= file.setExecutable(true, true);
        return ok;
    }

    // ── Parameter handling ─────────────────────────────────────────────────────

    private void populateParamsFromRequest(Map<String, String> params, List<String> recordAttachmentIds) {
        String[] fieldNames = { RECORD_ID_PARAM, TAB_ID_PARAM, TO, SUBJECT, NOTES, ARCHIVE, CC, BCC, REPLY_TO, TEMPLATE_ID };
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

    private boolean validateParams(JSONObject result, Map<String, String> params) throws Exception {
        if (isNullOrEmpty(params.get(RECORD_ID_PARAM)) || isNullOrEmpty(params.get(TAB_ID_PARAM))
                || isNullOrEmpty(params.get(TO)) || isNullOrEmpty(params.get(SUBJECT))) {
            respond(result, false, "Missing required parameters: recordId, tabId, to, subject.");
            return false;
        }
        return true;
    }

    // ── Email sending ──────────────────────────────────────────────────────────

    private void sendEmail(Map<String, String> params, List<String> recordAttachmentIds,
            List<File> tempFiles, EmailServerConfiguration emailConfig) throws Exception {
        List<File> allAttachments = new ArrayList<>(tempFiles);
        allAttachments.addAll(getFilesFromAttachments(recordAttachmentIds));

        EmailInfo email = new EmailInfo.Builder()
                .setRecipientTO(params.get(TO))
                .setRecipientCC(params.getOrDefault(CC, ""))
                .setRecipientBCC(params.getOrDefault(BCC, ""))
                .setReplyTo(params.getOrDefault(REPLY_TO, ""))
                .setSubject(params.get(SUBJECT))
                .setContent(params.getOrDefault(NOTES, ""))
                .setContentType("text/plain; charset=utf-8")
                .setAttachments(allAttachments)
                .setSentDate(new Date())
                .build();

        EmailManager.sendEmail(emailConfig, email);
    }

    @SuppressWarnings("unchecked")
    private List<File> getFilesFromAttachments(List<String> recordAttachmentIds) {
        List<File> recordFiles = new ArrayList<>();
        if (recordAttachmentIds.isEmpty()) return recordFiles;
        try {
            String attachPath = OBPropertiesProvider.getInstance()
                    .getOpenbravoProperties().getProperty(ATTACH_PATH_PROP);
            if (attachPath == null) return recordFiles;

            for (String attId : recordAttachmentIds) {
                Object[] row = (Object[]) OBDal.getInstance().getSession()
                        .createNativeQuery(
                                "SELECT path, name FROM c_file WHERE c_file_id = :id AND isactive = 'Y'")
                        .setParameter("id", attId)
                        .uniqueResult();
                if (row != null && row[0] != null && row[1] != null) {
                    File attFile = new File(attachPath + File.separator + row[0] + File.separator + row[1]);
                    if (attFile.exists() && attFile.isFile()) {
                        recordFiles.add(attFile);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load record attachments: {}", e.getMessage());
        }
        return recordFiles;
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────

    private void cleanupTempFiles(List<File> tempFiles, Path tempDir) {
        for (File f : tempFiles) {
            try {
                Files.delete(f.toPath());
            } catch (Exception e) {
                logger.debug("Could not delete temp file: {}", f.getAbsolutePath());
            }
        }
        if (tempDir != null) {
            try {
                Files.delete(tempDir);
            } catch (Exception e) {
                logger.debug("Could not delete temp directory: {}", tempDir.toAbsolutePath());
            }
        }
    }

    // ── Multipart parsing ──────────────────────────────────────────────────────

    private boolean isMultipartRequest() {
        String ct = getRequest().getContentType();
        return ct != null && ct.toLowerCase().startsWith("multipart/");
    }

    private void extractMultipartParams(Map<String, String> params, List<String> recordAttachmentIds,
            List<File> tempFiles, Path tempDir) {
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
            logger.error("[EmailSend] Could not parse multipart request: {}", e.getMessage());
        }
    }

    private void processFormField(FileItem item, Map<String, String> params,
            List<String> recordAttachmentIds) throws Exception {
        if ("recordAttachmentId".equals(item.getFieldName())) {
            String val = item.getString("UTF-8");
            if (val != null && !val.trim().isEmpty()) recordAttachmentIds.add(val.trim());
        } else {
            params.put(item.getFieldName(), item.getString("UTF-8"));
        }
    }

    private void processUploadedFile(FileItem item, List<File> tempFiles, Path tempDir) throws Exception {
        String fileName = item.getName();
        if (item.getFieldName() != null && item.getFieldName().startsWith("attachment")
                && fileName != null && !fileName.trim().isEmpty()) {
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
