/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.File;
import java.io.IOException;
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
import org.openbravo.dal.core.OBContext;
import org.openbravo.email.EmailUtils;
import org.openbravo.erpCommon.utility.poc.EmailInfo;
import org.openbravo.erpCommon.utility.poc.EmailManager;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.hibernate.criterion.Restrictions;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.base.structure.BaseOBObject;

/**
 * Sends an email for an Order or Invoice record using EmailManager directly.
 *
 * Accepts multipart/form-data (with optional file attachments) or
 * application/x-www-form-urlencoded.
 *
 * Fields: recordId, tabId, to, subject, notes (body), archive (Y/N),
 *         cc, bcc, replyTo, templateId.
 * File parts: attachment_0, attachment_1, ...
 */
public class EmailSendService extends MetadataService {

    public EmailSendService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        JSONObject result = new JSONObject();
        List<File> tempFiles = new ArrayList<>();
        try {
            OBContext.setAdminMode(true);
            try {
                Map<String, String> params = new HashMap<>();
                List<String> recordAttachmentIds = new ArrayList<>();

                if (isMultipartRequest()) {
                    extractMultipartParams(params, recordAttachmentIds, tempFiles);
                }

                // Fallback to getParameter for missing values (in case container already parsed multipart)
                String[] fieldNames = {
                        "recordId", "tabId", "to", "subject", "notes",
                        "archive", "cc", "bcc", "replyTo", "templateId"
                };
                for (String field : fieldNames) {
                    if (isNullOrEmpty(params.get(field))) {
                        String value = getRequest().getParameter(field);
                        if (value != null) {
                            params.put(field, value);
                        }
                    }
                }

                // Fallback for recordAttachmentId list
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

                String recordId = params.get("recordId");
                String tabId    = params.get("tabId");
                String to       = params.get("to");
                String subject  = params.get("subject");
                String body     = params.get("notes");

                if (isNullOrEmpty(recordId) || isNullOrEmpty(tabId)
                        || isNullOrEmpty(to) || isNullOrEmpty(subject)) {
                    result.put("success", false);
                    result.put("message", "Missing required parameters: recordId, tabId, to, subject.");
                    write(result);
                    return;
                }

                Tab tab = OBDal.getInstance().get(Tab.class, tabId);
                if (tab == null) {
                    result.put("success", false);
                    result.put("message", "Tab not found.");
                    write(result);
                    return;
                }

                Organization org = OBContext.getOBContext().getCurrentOrganization();
                String entityName = ModelProvider.getInstance()
                        .getEntityByTableName(tab.getTable().getDBTableName()).getName();
                BaseOBObject record = OBDal.getInstance().get(entityName, recordId);
                if (record != null) {
                    try {
                        Organization recordOrg = (Organization) record.get("organization");
                        if (recordOrg != null) org = recordOrg;
                    } catch (Exception e) { /* fall back to session org */ }
                }

                EmailServerConfiguration emailConfig = EmailUtils.getEmailConfiguration(org);
                if (emailConfig == null) {
                    emailConfig = EmailUtils.getEmailConfiguration(
                            OBContext.getOBContext().getCurrentOrganization());
                }
                if (emailConfig == null) {
                    try {
                        OBCriteria<EmailServerConfiguration> crit =
                                OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
                        crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
                        crit.setMaxResults(1);
                        emailConfig = (EmailServerConfiguration) crit.uniqueResult();
                    } catch (Exception e) { /* ignore */ }
                }
                if (emailConfig == null || emailConfig.getSmtpServerSenderAddress() == null
                        || emailConfig.getSmtpServerSenderAddress().trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "No sender defined. Please check Email Server configuration in Client settings.");
                    write(result);
                    return;
                }

                String cc      = params.getOrDefault("cc", "");
                String bcc     = params.getOrDefault("bcc", "");
                String replyTo = params.getOrDefault("replyTo", "");

                List<File> recordFiles = new ArrayList<>();
                if (!recordAttachmentIds.isEmpty()) {
                    try {
                        String attachPath = OBPropertiesProvider.getInstance()
                            .getOpenbravoProperties().getProperty("attach.path");
                        for (String attId : recordAttachmentIds) {
                            try {
                                @SuppressWarnings("unchecked")
                                Object[] attData = (Object[]) OBDal.getInstance().getSession()
                                    .createNativeQuery("SELECT path, name FROM c_file WHERE c_file_id = :id AND isactive = 'Y'")
                                    .setParameter("id", attId)
                                    .uniqueResult();
                                if (attData != null && attachPath != null) {
                                    String relPath  = (String) attData[0];
                                    String fileName = (String) attData[1];
                                    File attFile = new File(attachPath + File.separator + relPath + File.separator + fileName);
                                    if (attFile.exists() && attFile.isFile()) {
                                        recordFiles.add(attFile);
                                    } else {
                                        logger.warn("Record attachment file not found: " + attFile.getAbsolutePath());
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("Could not load record attachment " + attId + ": " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Could not load record attachments: " + e.getMessage());
                    }
                }

                List<File> allAttachments = new ArrayList<>(tempFiles);
                allAttachments.addAll(recordFiles);

                EmailInfo email = new EmailInfo.Builder()
                        .setRecipientTO(to)
                        .setRecipientCC(cc != null ? cc : "")
                        .setRecipientBCC(bcc != null ? bcc : "")
                        .setReplyTo(replyTo != null ? replyTo : "")
                        .setSubject(subject)
                        .setContent(body != null ? body : "")
                        .setContentType("text/plain; charset=utf-8")
                        .setAttachments(allAttachments)
                        .setSentDate(new Date())
                        .build();

                EmailManager.sendEmail(emailConfig, email);

                result.put("success", true);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            logger.error("Error in EmailSendService: " + e.getMessage(), e);
            try {
                result.put("success", false);
                result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to send email.");
                write(result);
            } catch (Exception jsonEx) {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to send email.\"}");
            }
        } finally {
            for (File f : tempFiles) {
                try { f.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean isMultipartRequest() {
        String ct = getRequest().getContentType();
        return ct != null && ct.toLowerCase().startsWith("multipart/");
    }

    /**
     * Parses a multipart/form-data request using Apache Commons FileUpload.
     * Does NOT require @MultipartConfig or <multipart-config> in web.xml.
     */
    private void extractMultipartParams(Map<String, String> params, List<String> recordAttachmentIds, List<File> tempFiles) {
        try {
            logger.info("[EmailSend] Extracting multipart params. Content-Type: " + getRequest().getContentType());
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            List<FileItem> items = upload.parseRequest(getRequest());
            logger.info("[EmailSend] Found " + (items != null ? items.size() : "null") + " items in multipart request.");
            if (items != null) {
                for (FileItem item : items) {
                    if (item.isFormField()) {
                        String fieldName = item.getFieldName();
                        String value = item.getString("UTF-8");
                        logger.info("[EmailSend] Field: " + fieldName + " = " + (value != null ? (value.length() > 50 ? value.substring(0, 50) + "..." : value) : "null"));
                        if ("recordAttachmentId".equals(fieldName)) {
                            String val = value != null ? value.trim() : "";
                            if (!val.isEmpty()) recordAttachmentIds.add(val);
                        } else {
                            params.put(fieldName, value);
                        }
                    } else {
                        String fieldName = item.getFieldName();
                        String fileName  = item.getName();
                        logger.info("[EmailSend] File: " + fieldName + " (" + fileName + ")");
                        if (fieldName != null && fieldName.startsWith("attachment")
                                && fileName != null && !fileName.trim().isEmpty()) {
                            File tempFile = File.createTempFile("email_attach_", "_" + fileName);
                            tempFile.deleteOnExit();
                            item.write(tempFile);
                            tempFiles.add(tempFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[EmailSend] Could not parse multipart request: " + e.getMessage(), e);
        }
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
