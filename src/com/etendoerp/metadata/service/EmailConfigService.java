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

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.email.EmailUtils;
import org.openbravo.erpCommon.utility.reporting.TemplateData;
import org.openbravo.erpCommon.utility.reporting.TemplateInfo;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Supplies all data needed to pre-populate the Send Email modal.
 */
public class EmailConfigService extends MetadataService {

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";

    public EmailConfigService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                String recordId = getRequest().getParameter("recordId");
                String tabId = getRequest().getParameter("tabId");

                if (recordId == null || tabId == null) {
                    handleErrorResponse(result, "Missing recordId or tabId parameter.");
                    return;
                }

                Tab tab = OBDal.getInstance().get(Tab.class, tabId);
                if (tab == null) {
                    handleErrorResponse(result, "Tab not found.");
                    return;
                }

                if (tab.getTable() == null) {
                    handleErrorResponse(result, "Table not found for the given tab.");
                    return;
                }

                BaseOBObject record = getRecord(tab, recordId);
                Organization org = getRecordOrganization(record);
                EmailServerConfiguration emailServerConfig = getSmtpConfiguration(org);
                
                String senderAddress = (emailServerConfig != null && emailServerConfig.getSmtpServerSenderAddress() != null)
                        ? emailServerConfig.getSmtpServerSenderAddress().trim() : "";
                
                if (senderAddress.isEmpty()) {
                    handleErrorResponse(result, "No sender defined. Please check Email Server configuration in Client settings.");
                    return;
                }

                if (record != null && !checkDocumentStatus(record)) {
                    handleErrorResponse(result, "Only completed or closed documents can be sent via email.");
                    return;
                }

                populateEmailConfig(result, tab, record, org, senderAddress);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            handleProcessError(result, e);
        }
    }

    private BaseOBObject getRecord(Tab tab, String recordId) {
        Entity entity = ModelProvider.getInstance().getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) {
            return null;
        }
        return OBDal.getInstance().get(entity.getName(), recordId);
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

    private void populateEmailConfig(JSONObject result, Tab tab, BaseOBObject record, Organization org, String senderAddress) throws Exception {
        result.put(SUCCESS, true);
        result.put("to", getRecipientEmail(record));
        result.put("toName", getRecipientName(record));
        result.put("bcc", getCurrentUserEmail());
        result.put("bccName", getCurrentUserName());
        result.put("replyTo", getSalesRepEmail(record));
        result.put("senderAddress", senderAddress);
        
        String subject = getRecordSubject(tab, record);
        String body = "";
        
        result.put("reportFileName", getReportFileName(tab, record));

        String docTypeId = getDocumentTypeId(record);
        if (docTypeId != null) {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            TemplateData[] templateData = getTemplateData(docTypeId, org);
            result.put("templates", getTemplatesJson(templateData));
            
            JSONObject emailDef = loadEmailDefinition(conn, docTypeId, org, templateData);
            if (emailDef != null) {
                if (emailDef.has("subject")) subject = emailDef.getString("subject");
                if (emailDef.has("body")) body = emailDef.getString("body");
            }
        } else {
            result.put("templates", new JSONArray());
        }
        
        result.put("subject", subject);
        result.put("body", body);
        result.put("recordAttachments", getRecordAttachments(tab.getTable().getId(), record != null ? record.getId().toString() : null));
    }

    private Organization getRecordOrganization(BaseOBObject record) {
        Organization org = OBContext.getOBContext().getCurrentOrganization();
        if (record != null && record.getEntity().hasProperty("organization")) {
            try {
                Object orgObj = record.get("organization");
                if (orgObj instanceof Organization) {
                    org = (Organization) orgObj;
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve organization from record, using session organization: " + e.getMessage());
            }
        }
        return org;
    }

    private EmailServerConfiguration getSmtpConfiguration(Organization org) {
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
                logger.debug("Could not find any fallback Email Server Configuration: " + e.getMessage());
            }
        }
        return config;
    }

    private String getRecipientEmail(BaseOBObject record) {
        if (record == null) return "";
        String email = "";
        if (record.getEntity().hasProperty("userContact")) {
            try {
                BaseOBObject contact = (BaseOBObject) record.get("userContact");
                if (contact != null && contact.getEntity().hasProperty("email")) {
                    email = safeString(contact.get("email"));
                }
            } catch (Exception e) { 
                logger.debug("Could not retrieve email from userContact: " + e.getMessage());
            }
        }

        if (email.isEmpty() && record.getEntity().hasProperty("businessPartner")) {
            try {
                BaseOBObject bp = (BaseOBObject) record.get("businessPartner");
                if (bp != null && bp.getEntity().hasProperty("email")) {
                    email = safeString(bp.get("email"));
                }
            } catch (Exception e) { 
                 logger.debug("Could not retrieve email from businessPartner: " + e.getMessage());
            }
        }
        return email;
    }

    private String getRecipientName(BaseOBObject record) {
        if (record == null) return "";
        String name = "";
        if (record.getEntity().hasProperty("userContact")) {
            try {
                BaseOBObject contact = (BaseOBObject) record.get("userContact");
                if (contact != null && contact.getEntity().hasProperty("name")) {
                    name = safeString(contact.get("name"));
                }
            } catch (Exception e) { 
                logger.debug("Could not retrieve name from userContact: " + e.getMessage());
            }
        }

        if (name.isEmpty() && record.getEntity().hasProperty("businessPartner")) {
            try {
                BaseOBObject bp = (BaseOBObject) record.get("businessPartner");
                if (bp != null && bp.getEntity().hasProperty("name")) {
                    name = safeString(bp.get("name"));
                }
            } catch (Exception e) { 
                logger.debug("Could not retrieve name from businessPartner: " + e.getMessage());
            }
        }
        return name;
    }

    private boolean checkDocumentStatus(BaseOBObject record) {
        Object status = null;
        if (record != null) {
            if (record.getEntity().hasProperty("documentStatus")) {
                status = record.get("documentStatus");
            } else if (record.getEntity().hasProperty("docstatus")) {
                status = record.get("docstatus");
            }
        }
        if (status != null && !status.equals("CO") && !status.equals("CL")) {
            return false;
        }
        return true;
    }

    private String getCurrentUserEmail() {
        try {
            return safeString(OBContext.getOBContext().getUser().getEmail());
        } catch (Exception e) {
            logger.debug("Could not retrieve current user email: " + e.getMessage());
            return "";
        }
    }

    private String getCurrentUserName() {
        try {
            return safeString(OBContext.getOBContext().getUser().getName());
        } catch (Exception e) {
            logger.debug("Could not retrieve current user name: " + e.getMessage());
            return "";
        }
    }

    private String getSalesRepEmail(BaseOBObject record) {
        if (record == null) return "";
        if (record.getEntity().hasProperty("salesRepresentative")) {
            try {
                BaseOBObject salesRep = (BaseOBObject) record.get("salesRepresentative");
                if (salesRep != null && salesRep.getEntity().hasProperty("email")) {
                    return safeString(salesRep.get("email"));
                }
            } catch (Exception e) { 
                logger.debug("Could not retrieve email from salesRepresentative: " + e.getMessage());
            }
        }
        return "";
    }

    private String getRecordSubject(Tab tab, BaseOBObject record) {
        try {
            String windowName = tab.getWindow().getName();
            String documentNo = (record != null && record.getEntity().hasProperty("documentNo")) 
                    ? safeString(record.get("documentNo")) : "";
            return (windowName + " " + documentNo).trim();
        } catch (Exception e) {
            logger.debug("Could not determine record subject: " + e.getMessage());
            return "";
        }
    }

    private String getReportFileName(Tab tab, BaseOBObject record) {
        if (record == null) return "";
        try {
            String entitySimpleName = tab.getTable().getName();
            String documentNo = record.getEntity().hasProperty("documentNo") 
                    ? safeString(record.get("documentNo")) : "";
            return "Report" + entitySimpleName + "_" + documentNo + ".pdf";
        } catch (Exception e) {
            logger.debug("Could not determine report file name: " + e.getMessage());
            return "";
        }
    }

    private String getDocumentTypeId(BaseOBObject record) {
        if (record == null || !record.getEntity().hasProperty("documentType")) return null;
        try {
            BaseOBObject docType = (BaseOBObject) record.get("documentType");
            if (docType != null) return docType.getId().toString();
        } catch (Exception e) { 
            logger.debug("Could not retrieve documentType: " + e.getMessage());
        }
        return null;
    }

    private TemplateData[] getTemplateData(String docTypeId, Organization org) {
        try {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            return TemplateData.getDocumentTemplates(conn, docTypeId, org.getId());
        } catch (Exception e) {
            logger.warn("Could not load templates for docType " + docTypeId + ": " + e.getMessage());
            return new TemplateData[0];
        }
    }

    private JSONArray getTemplatesJson(TemplateData[] templateData) throws Exception {
        JSONArray templates = new JSONArray();
        for (TemplateData tpl : templateData) {
            JSONObject tplJson = new JSONObject();
            tplJson.put("id", tpl.id);
            tplJson.put("name", tpl.name);
            templates.put(tplJson);
        }
        return templates;
    }

    private JSONObject loadEmailDefinition(ConnectionProvider conn, String docTypeId, Organization org, TemplateData[] templateData) {
        if (templateData.length == 0) return null;
        try {
            String lang = OBContext.getOBContext().getLanguage().getLanguage();
            TemplateInfo tplInfo = new TemplateInfo(conn, docTypeId, org.getId(), lang, templateData[0].id);
            TemplateInfo.EmailDefinition emailDef = tplInfo.get_DefaultEmailDefinition();
            if (emailDef != null) {
                JSONObject res = new JSONObject();
                String subject = emailDef.getSubject();
                if (subject != null && !subject.trim().isEmpty()) res.put("subject", subject);
                String body = emailDef.getBody();
                if (body != null) res.put("body", body);
                return res;
            }
        } catch (Exception e) {
            logger.warn("Could not load email definition: " + e.getMessage());
        }
        return null;
    }

    private JSONArray getRecordAttachments(String tableId, String recordId) {
        JSONArray recordAttachments = new JSONArray();
        if (recordId == null) return recordAttachments;
        try {
            OBCriteria<Attachment> criteria = OBDal.getInstance().createCriteria(Attachment.class);
            criteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE, OBDal.getInstance().getProxy(org.openbravo.model.ad.datamodel.Table.class, tableId)));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, recordId));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_ACTIVE, true));
            criteria.addOrder(Order.asc(Attachment.PROPERTY_NAME));
            
            List<Attachment> attachmentsList = criteria.list();
            for (Attachment attachment : attachmentsList) {
                JSONObject att = new JSONObject();
                att.put("id", attachment.getId());
                att.put("name", attachment.getName());
                recordAttachments.put(att);
            }
        } catch (Exception e) {
            logger.warn("Could not load record attachments using DAL: " + e.getMessage());
        }
        return recordAttachments;
    }

    private void handleProcessError(JSONObject result, Exception e) throws IOException {
        logger.error("Error in EmailConfigService: " + e.getMessage(), e);
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, e.getMessage() != null ? e.getMessage() : "Failed to load email configuration.");
            write(result);
        } catch (Exception jsonEx) {
            try {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to load email configuration.\"}");
            } catch (IOException ioEx) {
                logger.error("Fatal error writing response: " + ioEx.getMessage(), ioEx);
            }
        }
    }

    private String safeString(Object value) {
        if (value == null) return "";
        return value.toString().trim();
    }
}
