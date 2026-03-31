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

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
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
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Supplies all data needed to pre-populate the Send Email modal.
 *
 * GET /meta/email/config?recordId=xxx&tabId=xxx
 *
 * Response JSON:
 * {
 *   "success": true,
 *   "to": "customer@example.com",
 *   "toName": "Customer Corp",
 *   "bcc": "me@company.com",
 *   "replyTo": "salesrep@company.com",
 *   "senderAddress": "noreply@company.com",
 *   "subject": "Sales Order LA-29093",
 *   "body": "Dear ...",
 *   "reportFileName": "ReportOrder_LA-29093.pdf",
 *   "templates": [{"id": "...", "name": "Standard Order report template"}],
 *   "recordAttachments": [{"id": "...", "name": "file.pdf"}]
 * }
 *
 * All fields are best-effort; missing values return empty strings (never null).
 * Validation errors (SMTP not configured, no BP email, wrong status) return success=false.
 */
public class EmailConfigService extends MetadataService {

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
                String tabId    = getRequest().getParameter("tabId");

                if (recordId == null || tabId == null) {
                    result.put("success", false);
                    result.put("message", "Missing recordId or tabId parameter.");
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

                String entityName = ModelProvider.getInstance()
                        .getEntityByTableName(tab.getTable().getDBTableName()).getName();
                BaseOBObject record = OBDal.getInstance().get(entityName, recordId);

                // Resolve organization for SMTP lookup
                Organization org = OBContext.getOBContext().getCurrentOrganization();
                if (record != null) {
                    try {
                        Organization recordOrg = (Organization) record.get("organization");
                        if (recordOrg != null) {
                            org = recordOrg;
                        }
                    } catch (Exception e) { /* fall back to session org */ }
                }

                // SMTP validation — try record's org, then session org, then any active config
                EmailServerConfiguration emailServerConfig = EmailUtils.getEmailConfiguration(org);
                if (emailServerConfig == null) {
                    emailServerConfig = EmailUtils.getEmailConfiguration(
                            OBContext.getOBContext().getCurrentOrganization());
                }
                if (emailServerConfig == null) {
                    try {
                        OBCriteria<EmailServerConfiguration> crit =
                                OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
                        crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
                        crit.setMaxResults(1);
                        emailServerConfig = (EmailServerConfiguration) crit.uniqueResult();
                    } catch (Exception e) { /* ignore */ }
                }
                String senderAddress = (emailServerConfig != null && emailServerConfig.getSmtpServerSenderAddress() != null)
                        ? emailServerConfig.getSmtpServerSenderAddress().trim() : "";
                if (senderAddress.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "No sender defined. Please check Email Server configuration in Client settings.");
                    write(result);
                    return;
                }

                // TO address — Classic reads c_order.ad_user_id → ad_user.email (userContact)
                // Fallback cascade: userContact.email → businessPartner.email
                String toEmail = "";
                String toName  = "";
                if (record != null) {
                    try {
                        BaseOBObject contact = (BaseOBObject) record.get("userContact");
                        if (contact != null) {
                            toEmail = safeString(contact.get("email"));
                            toName  = safeString(contact.get("name"));
                        }
                    } catch (Exception e) { /* ignore */ }

                    if (toEmail.isEmpty()) {
                        try {
                            BaseOBObject bp = (BaseOBObject) record.get("businessPartner");
                            if (bp != null) {
                                toEmail = safeString(bp.get("email"));
                                toName  = safeString(bp.get("name"));
                            }
                        } catch (Exception e) { /* ignore */ }
                    }

                    // Status check
                    Object status = record.get("documentStatus");
                    if (status == null) {
                        try { status = record.get("docstatus"); } catch (Exception e) { /* ignore */ }
                    }
                    if (status != null && !status.equals("CO") && !status.equals("CL")) {
                        result.put("success", false);
                        result.put("message", "Only completed or closed documents can be sent via email.");
                        write(result);
                        return;
                    }
                }

                // BCC — current user's email and name
                String bcc = "";
                String bccName = "";
                try {
                    org.openbravo.model.ad.access.User currentUser = OBContext.getOBContext().getUser();
                    bcc = safeString(currentUser.getEmail());
                    bccName = safeString(currentUser.getName());
                } catch (Exception e) { /* ignore */ }

                // Reply-To — Classic reads c_order.salesrep_id → ad_user.email (salesRepresentative)
                String replyTo = "";
                if (record != null) {
                    try {
                        BaseOBObject salesRep = (BaseOBObject) record.get("salesRepresentative");
                        if (salesRep != null) {
                            replyTo = safeString(salesRep.get("email"));
                        }
                    } catch (Exception e) { /* ignore */ }
                }

                // Subject fallback — window name + documentNo
                String subject = "";
                try {
                    String windowName = tab.getWindow().getName();
                    String documentNo = record != null ? safeString(record.get("documentNo")) : "";
                    subject = (windowName + " " + documentNo).trim();
                } catch (Exception e) { /* ignore */ }

                // Report filename — display only
                String reportFileName = "";
                if (record != null) {
                    try {
                        String entitySimpleName = tab.getEntityName();
                        int dotIdx = entitySimpleName.lastIndexOf('.');
                        if (dotIdx >= 0) {
                            entitySimpleName = entitySimpleName.substring(dotIdx + 1);
                        }
                        String documentNo = safeString(record.get("documentNo"));
                        reportFileName = "Report" + entitySimpleName + "_" + documentNo + ".pdf";
                    } catch (Exception e) { /* ignore */ }
                }

                // Templates — query C_POC_DOCTYPE_TEMPLATE by the record's document type,
                // exactly as Classic's PrintController does.
                JSONArray templates = new JSONArray();
                String body = "";
                String docTypeId = null;

                if (record != null) {
                    try {
                        BaseOBObject docType = (BaseOBObject) record.get("documentType");
                        if (docType != null) {
                            docTypeId = docType.getId().toString();
                        }
                    } catch (Exception e) { /* ignore — record may not have documentType */ }
                }

                if (docTypeId != null) {
                    try {
                        ConnectionProvider conn = DalConnectionProvider.getReadOnlyConnectionProvider();
                        TemplateData[] templateData = TemplateData.getDocumentTemplates(
                                conn, docTypeId, org.getId());

                        for (TemplateData tpl : templateData) {
                            JSONObject tplJson = new JSONObject();
                            tplJson.put("id", tpl.id);
                            tplJson.put("name", tpl.name);
                            templates.put(tplJson);
                        }

                        // Load subject and body from the default template's email definition
                        if (templateData.length > 0) {
                            try {
                                String lang = OBContext.getOBContext().getLanguage().getLanguage();
                                TemplateInfo tplInfo = new TemplateInfo(
                                        conn, docTypeId, org.getId(), lang, templateData[0].id);
                                TemplateInfo.EmailDefinition emailDef = tplInfo.get_DefaultEmailDefinition();
                                if (emailDef != null) {
                                    String tplSubject = emailDef.getSubject();
                                    if (tplSubject != null && !tplSubject.trim().isEmpty()) {
                                        subject = tplSubject;
                                    }
                                    String tplBody = emailDef.getBody();
                                    if (tplBody != null) {
                                        body = tplBody;
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("Could not load email definition for template: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Could not load templates for docType " + docTypeId + ": " + e.getMessage());
                    }
                }

                // Record attachments — queried last to avoid corrupting the Hibernate session
                // before the critical SMTP/org lookups above
                String tableId = tab.getTable().getId();
                String normalizedRecordId = recordId.replace("-", "").toUpperCase();
                JSONArray recordAttachments = new JSONArray();
                try {
                    @SuppressWarnings("unchecked")
                    List<Object[]> attRows = OBDal.getInstance().getSession()
                        .createNativeQuery("SELECT c_file_id, name FROM c_file WHERE ad_table_id = :tId AND REPLACE(UPPER(ad_record_id), '-', '') = :rId AND isactive = 'Y' ORDER BY name")
                        .setParameter("tId", tableId)
                        .setParameter("rId", normalizedRecordId)
                        .list();
                    for (Object[] row : attRows) {
                        JSONObject att = new JSONObject();
                        att.put("id", row[0]);
                        att.put("name", row[1]);
                        recordAttachments.put(att);
                    }
                } catch (Exception e) {
                    logger.warn("Could not load record attachments: " + e.getMessage());
                }

                result.put("success", true);
                result.put("to", toEmail);
                result.put("toName", toName);
                result.put("bcc", bcc);
                result.put("bccName", bccName);
                result.put("replyTo", replyTo);
                result.put("senderAddress", senderAddress);
                result.put("subject", subject);
                result.put("body", body);
                result.put("reportFileName", reportFileName);
                result.put("templates", templates);
                result.put("recordAttachments", recordAttachments);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            logger.error("Error in EmailConfigService: " + e.getMessage(), e);
            try {
                result.put("success", false);
                result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to load email configuration.");
                write(result);
            } catch (Exception jsonEx) {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to load email configuration.\"}");
            }
        }
    }

    private String safeString(Object value) {
        if (value == null) return "";
        return value.toString().trim();
    }
}
