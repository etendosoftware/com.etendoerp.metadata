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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

/**
 * Returns the list of files attached to a record in AD_Attachment.
 * No email-specific validation (SMTP, document status, etc.) is performed.
 *
 * GET /meta/email/attachments?recordId=xxx&tabId=xxx
 *
 * Response: {"success": true, "attachments": [{"id": "...", "name": "file.pdf"}]}
 */
public class EmailAttachmentService extends MetadataService {

    public EmailAttachmentService(HttpServletRequest request, HttpServletResponse response) {
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
                String tableId = tab.getTable().getId();

                JSONArray attachments = new JSONArray();
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
                        att.put("id", row[0]);
                        att.put("name", row[1]);
                        attachments.put(att);
                    }
                } catch (Exception e) {
                    logger.warn("Could not query c_file: " + e.getMessage());
                }

                result.put("success", true);
                result.put("attachments", attachments);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            logger.error("Error in EmailAttachmentService: " + e.getMessage(), e);
            try {
                result.put("success", false);
                result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to load attachments.");
                write(result);
            } catch (Exception jsonEx) {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to load attachments.\"}");
            }
        }
    }
}
