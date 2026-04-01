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
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Validates email configuration and record status before opening the Send Email modal.
 *
 * GET /meta/email?recordId=xxx&tabId=xxx
 */
public class EmailService extends EmailBaseService {

    /**
     * Creates a new EmailService for the given request/response pair.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public EmailService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                executeValidation(result);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception ex) {
            handleServiceError(result, ex, "Failed to validate email configuration.");
        }
    }

    private void executeValidation(JSONObject result) throws Exception {
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

        respond(result, true, null);
    }
}
