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
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.email.EmailUtils;
import org.openbravo.erpCommon.utility.reporting.TemplateData;
import org.openbravo.erpCommon.utility.reporting.TemplateInfo;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Supplies all data needed to pre-populate the Send Email modal.
 *
 * GET /meta/email/config?recordId=xxx&tabId=xxx
 */
public class EmailConfigService extends MetadataService {

    private static final String SUBJECT       = "subject";
    private static final String USER_CONTACT  = "userContact";
    private static final String EMAIL_PROP    = "email";
    private static final String BUSINESS_PARTNER = "businessPartner";
    private static final String DOCUMENT_NO   = "documentNo";
    private static final String BODY          = "body";
    private static final String NAME          = "name";

    public EmailConfigService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                String recordId = getRequest().getParameter("recordId");
                String tabId    = getRequest().getParameter("tabId");

                if (recordId == null || tabId == null) {
                    respond(result, false, "Missing recordId or tabId parameter.");
                    return;
                }

                Tab tab = OBDal.getInstance().get(Tab.class, tabId);
                if (tab == null || tab.getTable() == null) {
                    respond(result, false, "Tab not found.");
                    return;
                }

                String entityName = ModelProvider.getInstance()
                        .getEntityByTableName(tab.getTable().getDBTableName()).getName();
                BaseOBObject record = OBDal.getInstance().get(entityName, recordId);
                if (record == null) {
                    respond(result, false, "Record not found.");
                    return;
                }

                Organization org = OBContext.getOBContext().getCurrentOrganization();
                try {
                    Object orgObj = record.get("organization");
                    if (orgObj instanceof Organization) org = (Organization) orgObj;
                } catch (Exception e) { /* fall back to session org */ }

                EmailServerConfiguration emailConfig = getSmtpConfig(org);
                String senderAddress = (emailConfig != null && emailConfig.getSmtpServerSenderAddress() != null)
                        ? emailConfig.getSmtpServerSenderAddress().trim() : "";
                if (senderAddress.isEmpty()) {
                    respond(result, false,
                            "No sender defined. Please check Email Server configuration in Client settings.");
                    return;
                }

                Object status = null;
                try { status = record.get("documentStatus"); } catch (Exception e) { /* ignore */ }
                if (status == null) {
                    try { status = record.get("docstatus"); } catch (Exception e) { /* ignore */ }
                }
                if (status != null && !status.equals("CO") && !status.equals("CL")) {
                    respond(result, false,
                            "Only completed or closed documents can be sent via email.");
                    return;
                }

                populateEmailConfig(result, tab, record, org, senderAddress, recordId);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            logger.error("Error in EmailConfigService: {}", e.getMessage(), e);
            try {
                respond(result, false,
                        e.getMessage() != null ? e.getMessage() : "Failed to load email configuration.");
            } catch (Exception ignored) {
                getResponse().getWriter().write(
                        "{\"success\":false,\"message\":\"Failed to load email configuration.\"}");
            }
        }
    }

    private void populateEmailConfig(JSONObject result, Tab tab, BaseOBObject record,
            Organization org, String senderAddress, String recordId) throws Exception {

        result.put("success", true);
        result.put("to",          getRecipientEmail(record));
        result.put("toName",      getRecipientName(record));
        result.put("bcc",         getCurrentUserEmail());
        result.put("bccName",     getCurrentUserName());
        result.put("replyTo",     getSalesRepEmail(record));
        result.put("senderAddress", senderAddress);
        result.put("reportFileName", getReportFileName(tab, record));

        String subject  = getRecordSubject(tab, record);
        String body     = "";
        String docTypeId = getDocumentTypeId(record);

        if (docTypeId != null) {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            TemplateData[] templateData = getTemplateData(docTypeId, org);
            result.put("templates", getTemplatesJson(templateData));
            JSONObject emailDef = loadEmailDefinition(conn, docTypeId, org, templateData);
            if (emailDef != null) {
                if (emailDef.has(SUBJECT)) subject = emailDef.getString(SUBJECT);
                if (emailDef.has(BODY))    body    = emailDef.getString(BODY);
            }
        } else {
            result.put("templates", new JSONArray());
        }

        result.put(SUBJECT, subject);
        result.put(BODY,    body);
        result.put("recordAttachments", queryRecordAttachments(tab.getTable().getId(), recordId));
    }

    // ── Attachment query against c_file (this Etendo instance uses c_file, not ad_attachment) ──

    private JSONArray queryRecordAttachments(String tableId, String recordId) {
        JSONArray attachments = new JSONArray();
        if (tableId == null || recordId == null) return attachments;
        try {
            String normalizedId = recordId.replace("-", "").toUpperCase();
            @SuppressWarnings("unchecked")
            List<Object[]> rows = OBDal.getInstance().getSession()
                .createNativeQuery(
                    "SELECT c_file_id, name FROM c_file " +
                    "WHERE ad_table_id = :tId " +
                    "AND REPLACE(UPPER(ad_record_id), '-', '') = :rId " +
                    "AND isactive = 'Y' ORDER BY name")
                .setParameter("tId", tableId)
                .setParameter("rId", normalizedId)
                .list();
            for (Object[] row : rows) {
                JSONObject att = new JSONObject();
                att.put("id",   row[0]);
                att.put("name", row[1]);
                attachments.put(att);
            }
        } catch (Exception e) {
            logger.warn("Could not load record attachments: {}", e.getMessage());
        }
        return attachments;
    }

    // ── SMTP config ──

    private EmailServerConfiguration getSmtpConfig(Organization org) {
        EmailServerConfiguration config = EmailUtils.getEmailConfiguration(org);
        if (config == null) {
            config = EmailUtils.getEmailConfiguration(OBContext.getOBContext().getCurrentOrganization());
        }
        if (config == null) {
            try {
                OBCriteria<EmailServerConfiguration> crit =
                        OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
                crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
                crit.setMaxResults(1);
                config = (EmailServerConfiguration) crit.uniqueResult();
            } catch (Exception e) { /* ignore */ }
        }
        return config;
    }

    // ── Field helpers ──

    private String getRecipientEmail(BaseOBObject record) {
        String email = getPropertyString(record, USER_CONTACT, EMAIL_PROP);
        return email.isEmpty() ? getPropertyString(record, BUSINESS_PARTNER, EMAIL_PROP) : email;
    }

    private String getRecipientName(BaseOBObject record) {
        String name = getPropertyString(record, USER_CONTACT, NAME);
        return name.isEmpty() ? getPropertyString(record, BUSINESS_PARTNER, NAME) : name;
    }

    private String getSalesRepEmail(BaseOBObject record) {
        return getPropertyString(record, "salesRepresentative", EMAIL_PROP);
    }

    private String getCurrentUserEmail() {
        try { return safeStr(OBContext.getOBContext().getUser().getEmail()); }
        catch (Exception e) { return ""; }
    }

    private String getCurrentUserName() {
        try { return safeStr(OBContext.getOBContext().getUser().getName()); }
        catch (Exception e) { return ""; }
    }

    private String getRecordSubject(Tab tab, BaseOBObject record) {
        try {
            String windowName  = tab.getWindow().getName();
            String documentNo  = record.getEntity().hasProperty(DOCUMENT_NO)
                    ? safeStr(record.get(DOCUMENT_NO)) : "";
            return (windowName + " " + documentNo).trim();
        } catch (Exception e) { return ""; }
    }

    private String getReportFileName(Tab tab, BaseOBObject record) {
        if (record == null) return "";
        try {
            String tableName  = tab.getTable().getName();
            String documentNo = record.getEntity().hasProperty(DOCUMENT_NO)
                    ? safeStr(record.get(DOCUMENT_NO)) : "";
            return "Report" + tableName + "_" + documentNo + ".pdf";
        } catch (Exception e) { return ""; }
    }

    private String getDocumentTypeId(BaseOBObject record) {
        if (record == null || !record.getEntity().hasProperty("documentType")) return null;
        try {
            Object docType = record.get("documentType");
            if (docType instanceof BaseOBObject) return ((BaseOBObject) docType).getId().toString();
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private TemplateData[] getTemplateData(String docTypeId, Organization org) {
        try {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            return TemplateData.getDocumentTemplates(conn, docTypeId, org.getId());
        } catch (Exception e) {
            logger.warn("Could not load templates for docType {}: {}", docTypeId, e.getMessage());
            return new TemplateData[0];
        }
    }

    private JSONArray getTemplatesJson(TemplateData[] templateData) throws Exception {
        JSONArray templates = new JSONArray();
        for (TemplateData tpl : templateData) {
            JSONObject tplJson = new JSONObject();
            tplJson.put("id",   tpl.id);
            tplJson.put("name", tpl.name);
            templates.put(tplJson);
        }
        return templates;
    }

    private JSONObject loadEmailDefinition(ConnectionProvider conn, String docTypeId,
            Organization org, TemplateData[] templateData) {
        if (templateData.length == 0) return null;
        try {
            String lang = OBContext.getOBContext().getLanguage().getLanguage();
            TemplateInfo tplInfo = new TemplateInfo(
                    conn, docTypeId, org.getId(), lang, templateData[0].id);
            TemplateInfo.EmailDefinition emailDef = tplInfo.get_DefaultEmailDefinition();
            if (emailDef != null) {
                JSONObject res = new JSONObject();
                String s = emailDef.getSubject();
                if (s != null && !s.trim().isEmpty()) res.put(SUBJECT, s);
                String b = emailDef.getBody();
                if (b != null) res.put(BODY, b);
                return res;
            }
        } catch (Exception e) {
            logger.warn("Could not load email definition: {}", e.getMessage());
        }
        return null;
    }

    private String getPropertyString(BaseOBObject source, String property, String subProperty) {
        if (!source.getEntity().hasProperty(property)) return "";
        try {
            Object obj = source.get(property);
            if (obj instanceof BaseOBObject) {
                BaseOBObject bob = (BaseOBObject) obj;
                if (bob.getEntity().hasProperty(subProperty)) {
                    return safeStr(bob.get(subProperty));
                }
            }
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    private void respond(JSONObject result, boolean success, String message) throws Exception {
        result.put("success", success);
        if (message != null) result.put("message", message);
        write(result);
    }

    private String safeStr(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
