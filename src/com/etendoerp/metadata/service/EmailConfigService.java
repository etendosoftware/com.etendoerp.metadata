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
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.reporting.TemplateData;
import org.openbravo.erpCommon.utility.reporting.TemplateInfo;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Supplies all data needed to pre-populate the Send Email modal.
 *
 * GET /meta/email/config?recordId=xxx&tabId=xxx
 */
public class EmailConfigService extends EmailBaseService {

    private static final String SUBJECT          = "subject";
    private static final String USER_CONTACT     = "userContact";
    private static final String EMAIL_PROP       = "email";
    private static final String BUSINESS_PARTNER = "businessPartner";
    private static final String DOCUMENT_NO      = "documentNo";
    private static final String BODY             = "body";
    private static final String NAME             = "name";

    /**
     * Creates a new EmailConfigService for the given request/response pair.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public EmailConfigService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                executeConfig(result);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception ex) {
            handleServiceError(result, ex, "Failed to load email configuration.");
        }
    }

    private void executeConfig(JSONObject result) throws Exception {
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
        BaseOBObject dataRecord = OBDal.getInstance().get(entityName, recordId);
        if (dataRecord == null) {
            respond(result, false, "Record not found.");
            return;
        }

        Organization org = resolveOrganization(dataRecord);
        String senderAddress = resolveSenderAddress(org);
        if (senderAddress.isEmpty()) {
            respond(result, false,
                    "No sender defined. Please check Email Server configuration in Client settings.");
            return;
        }

        Object docStatus = getDocumentStatus(dataRecord);
        if (docStatus != null && !docStatus.equals("CO") && !docStatus.equals("CL")) {
            respond(result, false,
                    "Only completed or closed documents can be sent via email.");
            return;
        }

        populateEmailConfig(result, tab, dataRecord, org, senderAddress, recordId);
        write(result);
    }

    private void populateEmailConfig(JSONObject result, Tab tab, BaseOBObject dataRecord,
            Organization org, String senderAddress, String recordId) throws Exception {

        result.put(KEY_SUCCESS,      true);
        result.put("to",             getRecipientEmail(dataRecord));
        result.put("toName",         getRecipientName(dataRecord));
        result.put("bcc",            getCurrentUserEmail());
        result.put("bccName",        getCurrentUserName());
        result.put("replyTo",        getSalesRepEmail(dataRecord));
        result.put("senderAddress",  senderAddress);
        result.put("reportFileName", getReportFileName(tab, dataRecord));

        String subject   = getRecordSubject(tab, dataRecord);
        String body      = "";
        String docTypeId = getDocumentTypeId(dataRecord);

        if (docTypeId != null) {
            ConnectionProvider conn     = DalConnectionProvider.getReadOnlyConnectionProvider();
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

    // ── Attachment query against c_file ──

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
        } catch (Exception ex) {
            logger.warn("Could not load record attachments: {}", ex.getMessage());
        }
        return attachments;
    }

    // ── Field helpers ──

    private String getRecipientEmail(BaseOBObject dataRecord) {
        String emailValue = getPropertyString(dataRecord, USER_CONTACT, EMAIL_PROP);
        return emailValue.isEmpty()
                ? getPropertyString(dataRecord, BUSINESS_PARTNER, EMAIL_PROP) : emailValue;
    }

    private String getRecipientName(BaseOBObject dataRecord) {
        String nameValue = getPropertyString(dataRecord, USER_CONTACT, NAME);
        return nameValue.isEmpty()
                ? getPropertyString(dataRecord, BUSINESS_PARTNER, NAME) : nameValue;
    }

    private String getSalesRepEmail(BaseOBObject dataRecord) {
        return getPropertyString(dataRecord, "salesRepresentative", EMAIL_PROP);
    }

    private String getCurrentUserEmail() {
        try { return safeStr(OBContext.getOBContext().getUser().getEmail()); }
        catch (Exception ex) { return ""; }
    }

    private String getCurrentUserName() {
        try { return safeStr(OBContext.getOBContext().getUser().getName()); }
        catch (Exception ex) { return ""; }
    }

    private String getRecordSubject(Tab tab, BaseOBObject dataRecord) {
        try {
            String windowName = tab.getWindow().getName();
            String documentNo = dataRecord.getEntity().hasProperty(DOCUMENT_NO)
                    ? safeStr(dataRecord.get(DOCUMENT_NO)) : "";
            return (windowName + " " + documentNo).trim();
        } catch (Exception ex) { return ""; }
    }

    private String getReportFileName(Tab tab, BaseOBObject dataRecord) {
        if (dataRecord == null) return "";
        try {
            String tableName  = tab.getTable().getName();
            String documentNo = dataRecord.getEntity().hasProperty(DOCUMENT_NO)
                    ? safeStr(dataRecord.get(DOCUMENT_NO)) : "";
            return "Report" + tableName + "_" + documentNo + ".pdf";
        } catch (Exception ex) { return ""; }
    }

    private String getDocumentTypeId(BaseOBObject dataRecord) {
        if (dataRecord == null || !dataRecord.getEntity().hasProperty("documentType")) return null;
        try {
            Object docType = dataRecord.get("documentType");
            if (docType instanceof BaseOBObject) return ((BaseOBObject) docType).getId().toString();
        } catch (Exception ex) { /* ignore */ }
        return null;
    }

    private TemplateData[] getTemplateData(String docTypeId, Organization org) {
        try {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            return TemplateData.getDocumentTemplates(conn, docTypeId, org.getId());
        } catch (Exception ex) {
            logger.warn("Could not load templates for docType {}: {}", docTypeId, ex.getMessage());
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
                JSONObject emailDefJson = new JSONObject();
                String emailSubject = emailDef.getSubject();
                if (emailSubject != null && !emailSubject.trim().isEmpty()) {
                    emailDefJson.put(SUBJECT, emailSubject);
                }
                String emailBody = emailDef.getBody();
                if (emailBody != null) {
                    emailDefJson.put(BODY, emailBody);
                }
                return emailDefJson;
            }
        } catch (Exception ex) {
            logger.warn("Could not load email definition: {}", ex.getMessage());
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
        } catch (Exception ex) { /* ignore */ }
        return "";
    }

    private String safeStr(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
