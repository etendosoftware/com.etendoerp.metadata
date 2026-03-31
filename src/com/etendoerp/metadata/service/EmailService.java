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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.email.EmailUtils;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.base.structure.BaseOBObject;

/**
 * Service to validate email configuration and record status before sending.
 */
public class EmailService extends MetadataService {

    public EmailService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            
            String recordId = getRequest().getParameter("recordId");
            String tabId = getRequest().getParameter("tabId");
            
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

            // 1. SMTP check — use EmailUtils which queries EmailServerConfiguration
            // walking up the org tree, exactly as Classic does.
            Organization org = OBContext.getOBContext().getCurrentOrganization();
            if (record != null) {
                try {
                    Organization recordOrg = (Organization) record.get("organization");
                    if (recordOrg != null) {
                        org = recordOrg;
                    }
                } catch (Exception e) { /* fall back to session org */ }
            }

            EmailServerConfiguration emailConfig = EmailUtils.getEmailConfiguration(org);
            String senderAddress = emailConfig != null ? emailConfig.getSmtpServerSenderAddress() : null;
            if (emailConfig == null || senderAddress == null || senderAddress.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "No sender defined. Please check Email Server configuration in Client settings.");
                write(result);
                return;
            }

            if (record != null) {
                // 2.1 Recipient check — Classic reads userContact.email (ad_user_id → ad_user.email)
                String email = "";
                try {
                    BaseOBObject contact = (BaseOBObject) record.get("userContact");
                    if (contact != null) {
                        email = safeString(contact.get("email"));
                    }
                } catch (Exception e) { /* ignore */ }
                if (email.isEmpty()) {
                    try {
                        BaseOBObject bp = (BaseOBObject) record.get("businessPartner");
                        if (bp != null) {
                            email = safeString(bp.get("email"));
                        }
                    } catch (Exception e) { /* ignore */ }
                }
                // Note: empty email is allowed — user can fill it in the form

                // 2.2 Status check
                Object status = null;
                try { status = record.get("documentStatus"); } catch (Exception e) { /* ignore */ }
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

            result.put("success", true);
            write(result);

        } catch (Exception e) {
            logger.error("Error in EmailService: " + e.getMessage(), e);
            try {
                result.put("success", false);
                result.put("message", e.getMessage());
                write(result);
            } catch (Exception jsonEx) {
                // Last resort
                getResponse().getWriter().write("{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
            }
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private String safeString(Object value) {
        if (value == null) return "";
        return value.toString().trim();
    }
}
