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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.reporting.TemplateData;
import org.openbravo.erpCommon.utility.reporting.TemplateInfo;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Supplies all data needed to pre-populate the Send Email modal.
 */
public class EmailConfigService extends MetadataService {

    private static final String SUBJECT = "subject";
    private static final String USER_CONTACT = "userContact";
    private static final String EMAIL = "email";
    private static final String BUSINESS_PARTNER = "businessPartner";
    private static final String DOCUMENT_NO = "documentNo";
    private static final String BODY = "body";
    private static final String NAME = "name";

    /**
     * Constructor for EmailConfigService.
     * @param request The HttpServletRequest object.
     * @param response The HttpServletResponse object.
     */
    public EmailConfigService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    protected void execute(JSONObject result) throws ServletException, IOException, JSONException {
        MetadataContext ctx = validateAndGetContext(result);
        if (ctx == null) return;

        populateEmailConfig(result, ctx.getTab(), ctx.getDataRecord(), ctx.getOrg(), ctx.getSenderAddress());
        write(result);
    }

    private void populateEmailConfig(JSONObject result, Tab tab, BaseOBObject dataRecord, Organization org, String senderAddress) throws JSONException, IOException {
        result.put(SUCCESS, true);
        result.put("to", getRecipientEmail(dataRecord));
        result.put("toName", getRecipientName(dataRecord));
        result.put("bcc", getCurrentUserEmail());
        result.put("bccName", getCurrentUserName());
        result.put("replyTo", getSalesRepEmail(dataRecord));
        result.put("senderAddress", senderAddress);
        
        String subject = getRecordSubject(tab, dataRecord);
        String body = "";
        
        result.put("reportFileName", getReportFileName(tab, dataRecord));

        String docTypeId = getDocumentTypeId(dataRecord);
        if (docTypeId != null) {
            ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
            TemplateData[] templateData = getTemplateData(docTypeId, org);
            result.put("templates", getTemplatesJson(templateData));
            
            JSONObject emailDef = loadEmailDefinition(conn, docTypeId, org, templateData);
            if (emailDef != null) {
                if (emailDef.has(SUBJECT)) subject = emailDef.getString(SUBJECT);
                if (emailDef.has(BODY)) body = emailDef.getString(BODY);
            }
        } else {
            result.put("templates", new JSONArray());
        }
        
        result.put(SUBJECT, subject);
        result.put(BODY, body);
        String recordId = dataRecord != null ? dataRecord.getId().toString() : null;
        result.put("recordAttachments", getRecordAttachments(tab.getTable().getId(), recordId));
    }

    private String getRecipientEmail(BaseOBObject dataRecord) {
        if (dataRecord == null) return "";
        String email = getPropertyString(dataRecord, USER_CONTACT, EMAIL);
        if (email.isEmpty()) {
            email = getPropertyString(dataRecord, BUSINESS_PARTNER, EMAIL);
        }
        return email;
    }

    private String getRecipientName(BaseOBObject dataRecord) {
        if (dataRecord == null) return "";
        String name = getPropertyString(dataRecord, USER_CONTACT, NAME);
        if (name.isEmpty()) {
            name = getPropertyString(dataRecord, BUSINESS_PARTNER, NAME);
        }
        return name;
    }

    private String getPropertyString(BaseOBObject sourceRecord, String property, String subProperty) {
        if (sourceRecord.getEntity().hasProperty(property)) {
            try {
                Object obj = sourceRecord.get(property);
                if (obj instanceof BaseOBObject) {
                    BaseOBObject bob = (BaseOBObject) obj;
                    if (bob.getEntity().hasProperty(subProperty)) {
                        return safeString(bob.get(subProperty));
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve {} from {}: {}", subProperty, property, e.getMessage());
            }
        }
        return "";
    }

    private String getCurrentUserEmail() {
        try {
            return safeString(org.openbravo.dal.core.OBContext.getOBContext().getUser().getEmail());
        } catch (Exception e) {
            logger.debug("Could not retrieve current user email: {}", e.getMessage());
            return "";
        }
    }

    private String getCurrentUserName() {
        try {
            return safeString(org.openbravo.dal.core.OBContext.getOBContext().getUser().getName());
        } catch (Exception e) {
            logger.debug("Could not retrieve current user name: {}", e.getMessage());
            return "";
        }
    }

    private String getSalesRepEmail(BaseOBObject dataRecord) {
        if (dataRecord == null) return "";
        return getPropertyString(dataRecord, "salesRepresentative", EMAIL);
    }

    private String getRecordSubject(Tab tab, BaseOBObject dataRecord) {
        try {
            String windowName = tab.getWindow().getName();
            String documentNoResource = (dataRecord != null && dataRecord.getEntity().hasProperty(DOCUMENT_NO)) 
                    ? safeString(dataRecord.get(DOCUMENT_NO)) : "";
            return (windowName + " " + documentNoResource).trim();
        } catch (Exception e) {
            logger.debug("Could not determine record subject: {}", e.getMessage());
            return "";
        }
    }

    private String getReportFileName(Tab tab, BaseOBObject dataRecord) {
        if (dataRecord == null) return "";
        try {
            String entitySimpleName = tab.getTable().getName();
            String documentNoResource = dataRecord.getEntity().hasProperty(DOCUMENT_NO) 
                    ? safeString(dataRecord.get(DOCUMENT_NO)) : "";
            return "Report" + entitySimpleName + "_" + documentNoResource + ".pdf";
        } catch (Exception e) {
            logger.debug("Could not determine report file name: {}", e.getMessage());
            return "";
        }
    }

    private String getDocumentTypeId(BaseOBObject dataRecord) {
        if (dataRecord == null || !dataRecord.getEntity().hasProperty("documentType")) return null;
        try {
            Object docTypeObj = dataRecord.get("documentType");
            if (docTypeObj instanceof BaseOBObject) {
                BaseOBObject docType = (BaseOBObject) docTypeObj;
                return docType.getId().toString();
            }
        } catch (Exception e) { 
            logger.debug("Could not retrieve documentType: {}", e.getMessage());
        }
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

    private JSONArray getTemplatesJson(TemplateData[] templateData) throws JSONException {
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
            String lang = org.openbravo.dal.core.OBContext.getOBContext().getLanguage().getLanguage();
            TemplateInfo tplInfo = new TemplateInfo(conn, docTypeId, org.getId(), lang, templateData[0].id);
            TemplateInfo.EmailDefinition emailDef = tplInfo.get_DefaultEmailDefinition();
            if (emailDef != null) {
                JSONObject res = new JSONObject();
                String emailSubject = emailDef.getSubject();
                if (emailSubject != null && !emailSubject.trim().isEmpty()) res.put(SUBJECT, emailSubject);
                String emailBody = emailDef.getBody();
                if (emailBody != null) res.put(BODY, emailBody);
                return res;
            }
        } catch (Exception e) {
            logger.warn("Could not load email definition: {}", e.getMessage());
        }
        return null;
    }
}
