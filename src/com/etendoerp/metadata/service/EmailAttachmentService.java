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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

/**
 * Returns the list of files attached to a record.
 *
 * GET /meta/email/attachments?recordId=xxx&tabId=xxx
 */
public class EmailAttachmentService extends MetadataService {

    private static final String KEY_SUCCESS     = "success";
    private static final String KEY_MESSAGE     = "message";
    private static final String KEY_ATTACHMENTS = "attachments";

    /**
     * Creates a new EmailAttachmentService for the given request/response pair.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    public EmailAttachmentService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                executeAttachmentLookup(result);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception ex) {
            handleError(result, ex);
        }
    }

    private void executeAttachmentLookup(JSONObject result) throws Exception {
        String recordId = getRequest().getParameter("recordId");
        String tabId    = getRequest().getParameter("tabId");

        if (recordId == null || tabId == null) {
            result.put(KEY_SUCCESS, false);
            result.put(KEY_MESSAGE, "Missing recordId or tabId parameter.");
            write(result);
            return;
        }

        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null || tab.getTable() == null) {
            result.put(KEY_SUCCESS, false);
            result.put(KEY_MESSAGE, "Tab not found.");
            write(result);
            return;
        }

        JSONArray attachments = queryAttachments(tab.getTable().getId(), recordId);
        result.put(KEY_SUCCESS,     true);
        result.put(KEY_ATTACHMENTS, attachments);
        write(result);
    }

    private JSONArray queryAttachments(String tableId, String recordId) {
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
                att.put("id",   row[0]);
                att.put("name", row[1]);
                attachments.put(att);
            }
        } catch (Exception ex) {
            logger.warn("Could not load record attachments: {}", ex.getMessage());
        }
        return attachments;
    }

    private void handleError(JSONObject result, Exception ex) throws IOException {
        logger.error("Error in EmailAttachmentService: {}", ex.getMessage(), ex);
        try {
            result.put(KEY_SUCCESS, false);
            result.put(KEY_MESSAGE, ex.getMessage() != null ? ex.getMessage() : "Failed to load attachments.");
            write(result);
        } catch (Exception ignored) {
            getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to load attachments.\"}");
        }
    }
}
