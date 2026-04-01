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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
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
    protected void executeEmailAction(JSONObject result) throws Exception {
        ValidationContext ctx = validateEmailRequest(result);
        if (ctx == null) return;
        populateEmailConfig(result, ctx);
        write(result);
    }

    @Override
    protected String getFallbackErrorMessage() {
        return "Failed to load email configuration.";
    }

    private void populateEmailConfig(JSONObject result, ValidationContext ctx) throws Exception {
        Tab tab             = ctx.getTab();
        BaseOBObject dataRecord = ctx.getDataRecord();
        Organization org    = ctx.getOrg();
        String senderAddress = ctx.getSenderAddress();
        String recordId     = ctx.getRecordId();

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
        result.put("recordAttachments", queryAttachments(tab.getTable().getId(), recordId));
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
