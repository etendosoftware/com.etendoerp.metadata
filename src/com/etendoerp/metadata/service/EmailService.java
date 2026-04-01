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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Service to validate email configuration and record status before sending.
 */
public class EmailService extends MetadataService {

    /**
     * Constructor for EmailService.
     * @param request The HttpServletRequest object.
     * @param response The HttpServletResponse object.
     */
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
                handleErrorResponse(result, "Missing recordId or tabId parameter.");
                return;
            }

            Tab tab = OBDal.getInstance().get(Tab.class, tabId);
            if (tab == null) {
                handleErrorResponse(result, "Tab not found.");
                return;
            }

            Entity entity = ModelProvider.getInstance().getEntityByTableName(tab.getTable().getDBTableName());
            if (entity == null) {
                handleErrorResponse(result, "Entity not found for table " + tab.getTable().getDBTableName());
                return;
            }

            BaseOBObject dataRecord = OBDal.getInstance().get(entity.getName(), recordId);

            Organization org = getRecordOrganization(dataRecord);
            EmailServerConfiguration emailConfig = getEmailConfiguration(org);
            String senderAddress = (emailConfig != null && emailConfig.getSmtpServerSenderAddress() != null)
                    ? emailConfig.getSmtpServerSenderAddress().trim() : "";

            if (senderAddress.isEmpty()) {
                handleErrorResponse(result, "No sender defined. Please check Email Server configuration in Client settings.");
                return;
            }

            if (dataRecord != null && !checkDocumentStatus(dataRecord)) {
                handleErrorResponse(result, "Only completed or closed documents can be sent via email.");
                return;
            }

            result.put(SUCCESS, true);
            write(result);

        } catch (Exception e) {
            handleProcessError("EmailService", result, e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private boolean checkDocumentStatus(BaseOBObject dataRecord) {
        Object status = getDocumentStatus(dataRecord);
        return status == null || status.equals("CO") || status.equals("CL");
    }

    private Object getDocumentStatus(BaseOBObject dataRecord) {
        Object status = null;
        if (dataRecord != null) {
            if (dataRecord.getEntity().hasProperty("documentStatus")) {
                status = dataRecord.get("documentStatus");
            } else if (dataRecord.getEntity().hasProperty("docstatus")) {
                status = dataRecord.get("docstatus");
            }
        }
        return status;
    }
}
