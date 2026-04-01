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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.email.EmailUtils;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Validates email configuration and record status before opening the Send Email modal.
 *
 * GET /meta/email?recordId=xxx&tabId=xxx
 */
public class EmailService extends MetadataService {

    public EmailService(HttpServletRequest request, HttpServletResponse response) {
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

                respond(result, true, null);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            logger.error("Error in EmailService: {}", e.getMessage(), e);
            try {
                respond(result, false,
                        e.getMessage() != null ? e.getMessage() : "Failed to validate email configuration.");
            } catch (Exception ignored) {
                getResponse().getWriter().write(
                        "{\"success\":false,\"message\":\"Failed to validate email configuration.\"}");
            }
        }
    }

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

    private void respond(JSONObject result, boolean success, String message) throws Exception {
        result.put("success", success);
        if (message != null) result.put("message", message);
        write(result);
    }
}
