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
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.email.EmailUtils;
import org.openbravo.erpCommon.utility.poc.EmailInfo;
import org.openbravo.erpCommon.utility.poc.EmailManager;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.base.structure.BaseOBObject;

/**
 * Sends an email for an Order or Invoice record using EmailManager directly,
 * bypassing Classic's session-dependent HTML servlet flow.
 *
 * Expected POST params: recordId, tabId, to, subject, notes (body), archive (Y/N)
 */
public class EmailSendService extends MetadataService {

    public EmailSendService(HttpServletRequest request, HttpServletResponse response) {
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
                String to       = getRequest().getParameter("to");
                String subject  = getRequest().getParameter("subject");
                String body     = getRequest().getParameter("notes");
                String archive  = getRequest().getParameter("archive");

                if (recordId == null || tabId == null || to == null || subject == null) {
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

                // Resolve the record's organization for email config lookup
                Organization org = OBContext.getOBContext().getCurrentOrganization();
                String entityName = ModelProvider.getInstance()
                        .getEntityByTableName(tab.getTable().getDBTableName()).getName();
                BaseOBObject record = OBDal.getInstance().get(entityName, recordId);
                if (record != null) {
                    try {
                        Organization recordOrg = (Organization) record.get("organization");
                        if (recordOrg != null) {
                            org = recordOrg;
                        }
                    } catch (Exception e) { /* fall back to session org */ }
                }

                EmailServerConfiguration emailConfig = EmailUtils.getEmailConfiguration(org);
                if (emailConfig == null || emailConfig.getSmtpServerSenderAddress() == null
                        || emailConfig.getSmtpServerSenderAddress().trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "No sender defined. Please check Email Server configuration in Client settings.");
                    write(result);
                    return;
                }

                String cc      = getRequest().getParameter("cc");
                String bcc     = getRequest().getParameter("bcc");
                String replyTo = getRequest().getParameter("replyTo");

                EmailInfo email = new EmailInfo.Builder()
                        .setRecipientTO(to)
                        .setRecipientCC(cc != null ? cc : "")
                        .setRecipientBCC(bcc != null ? bcc : "")
                        .setReplyTo(replyTo != null ? replyTo : "")
                        .setSubject(subject)
                        .setContent(body != null ? body : "")
                        .setContentType("text/plain; charset=utf-8")
                        .setAttachments(new ArrayList<>())
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
        }
    }
}
