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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.email.EmailUtils;
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

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String RECORD_ID = "recordId";
    private static final String TAB_ID = "tabId";
    private static final String SUBJECT = "subject";
    private static final String ATTACH_PATH_PROP = "attach.path";

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
        try {
            OBContext.setAdminMode(true);
            Map<String, String> params = new HashMap<>();
            List<String> recordAttachmentIds = new ArrayList<>();

            if (isMultipartRequest()) {
                extractMultipartParams(params, recordAttachmentIds, tempFiles);
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

            if (emailConfig == null) {
                handleErrorResponse(result, "No sender defined. Please check Email Server configuration in Client settings.");
                return;
            }

            sendEmail(params, recordAttachmentIds, tempFiles, emailConfig);
            result.put(SUCCESS, true);
            write(result);

        } catch (Exception e) {
            handleProcessError(result, e);
        } finally {
            OBContext.restorePreviousMode();
            cleanupTempFiles(tempFiles);
        }
    }

    private void populateParamsFromRequest(Map<String, String> params, List<String> recordAttachmentIds) {
        String[] fieldNames = { RECORD_ID, TAB_ID, "to", SUBJECT, "notes", "archive", "cc", "bcc", "replyTo", "templateId" };
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
                || isNullOrEmpty(params.get("to")) || isNullOrEmpty(params.get(SUBJECT))) {
            handleErrorResponse(result, "Missing required parameters: recordId, tabId, to, subject.");
            return false;
        }
        return true;
    }

    private BaseOBObject getRecord(Tab tab, String recordId) {
        if (tab.getTable() == null) return null;
        Entity entity = ModelProvider.getInstance().getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) return null;
        return OBDal.getInstance().get(entity.getName(), recordId);
    }

    private Organization getRecordOrganization(BaseOBObject dataRecord) {
        Organization org = OBContext.getOBContext().getCurrentOrganization();
        if (dataRecord != null && dataRecord.getEntity().hasProperty("organization")) {
            try {
                Object orgObj = dataRecord.get("organization");
                if (orgObj instanceof Organization) org = (Organization) orgObj;
            } catch (Exception e) {
                logger.debug("Could not retrieve organization from record: " + e.getMessage());
            }
        }
        return org;
    }

    private EmailServerConfiguration getEmailConfiguration(Organization org) {
        EmailServerConfiguration config = EmailUtils.getEmailConfiguration(org);
        if (config == null) {
            config = EmailUtils.getEmailConfiguration(OBContext.getOBContext().getCurrentOrganization());
        }
        if (config == null) {
            try {
                OBCriteria<EmailServerConfiguration> crit = OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
                crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
                crit.setMaxResults(1);
                config = (EmailServerConfiguration) crit.uniqueResult();
            } catch (Exception e) {
                logger.debug("Could not retrieve fallback email configuration: " + e.getMessage());
            }
        }
        return (config != null && config.getSmtpServerSenderAddress() != null && !config.getSmtpServerSenderAddress().trim().isEmpty()) ? config : null;
    }

    private void sendEmail(Map<String, String> params, List<String> recordAttachmentIds, List<File> tempFiles, EmailServerConfiguration emailConfig) throws Exception {
        List<File> allAttachments = new ArrayList<>(tempFiles);
        allAttachments.addAll(getRecordAttachments(recordAttachmentIds));

        EmailInfo email = new EmailInfo.Builder()
                .setRecipientTO(params.get("to"))
                .setRecipientCC(params.getOrDefault("cc", ""))
                .setRecipientBCC(params.getOrDefault("bcc", ""))
                .setReplyTo(params.getOrDefault("replyTo", ""))
                .setSubject(params.get(SUBJECT))
                .setContent(params.getOrDefault("notes", ""))
                .setContentType("text/plain; charset=utf-8")
                .setAttachments(allAttachments)
                .setSentDate(new Date())
                .build();

        EmailManager.sendEmail(emailConfig, email);
    }

    private List<File> getRecordAttachments(List<String> recordAttachmentIds) {
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

    private void cleanupTempFiles(List<File> tempFiles) {
        for (File f : tempFiles) {
            try {
                Files.delete(f.toPath());
            } catch (Exception e) {
                logger.debug("Could not delete temp file: " + f.getAbsolutePath());
            }
        }
    }

    private void handleErrorResponse(JSONObject result, String message) throws IOException {
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, message);
            write(result);
        } catch (Exception e) {
            logger.error("Error writing error response: " + e.getMessage(), e);
        }
    }

    private void handleProcessError(JSONObject result, Exception e) throws IOException {
        logger.error("Error in EmailSendService: " + e.getMessage(), e);
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, e.getMessage() != null ? e.getMessage() : "Failed to send email.");
            write(result);
        } catch (Exception jsonEx) {
            try {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to send email.\"}");
            } catch (IOException ioEx) {
                logger.error("Fatal error writing response: " + ioEx.getMessage(), ioEx);
            }
        }
    }

    private boolean isMultipartRequest() {
        String ct = getRequest().getContentType();
        return ct != null && ct.toLowerCase().startsWith("multipart/");
    }

    private void extractMultipartParams(Map<String, String> params, List<String> recordAttachmentIds, List<File> tempFiles) {
        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            List<FileItem> items = upload.parseRequest(getRequest());
            if (items == null) return;

            for (FileItem item : items) {
                if (item.isFormField()) {
                    if ("recordAttachmentId".equals(item.getFieldName())) {
                        String val = item.getString("UTF-8");
                        if (val != null && !val.trim().isEmpty()) recordAttachmentIds.add(val.trim());
                    } else {
                        params.put(item.getFieldName(), item.getString("UTF-8"));
                    }
                } else {
                    processUploadedFile(item, tempFiles);
                }
            }
        } catch (Exception e) {
            logger.error("[EmailSend] Could not parse multipart request: " + e.getMessage(), e);
        }
    }

    private void processUploadedFile(FileItem item, List<File> tempFiles) throws Exception {
        String fileName = item.getName();
        if (item.getFieldName() != null && item.getFieldName().startsWith("attachment") && fileName != null && !fileName.trim().isEmpty()) {
            File tempFile = File.createTempFile("email_attach_", "_" + fileName);
            tempFile.deleteOnExit();
            item.write(tempFile);
            tempFiles.add(tempFile);
        }
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
