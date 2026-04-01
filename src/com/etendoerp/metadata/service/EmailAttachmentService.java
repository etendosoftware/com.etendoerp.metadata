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
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;

/**
 * Returns the list of files attached to a record in AD_Attachment.
 */
public class EmailAttachmentService extends MetadataService {

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";

    /**
     * Constructor for EmailAttachmentService.
     * @param request The HttpServletRequest object.
     * @param response The HttpServletResponse object.
     */
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
                    handleErrorResponse(result, "Missing recordId or tabId parameter.");
                    return;
                }

                Tab tab = OBDal.getInstance().get(Tab.class, tabId);
                if (tab == null) {
                    handleErrorResponse(result, "Tab not found.");
                    return;
                }

                if (tab.getTable() == null) {
                    handleErrorResponse(result, "Table not found for the given tab.");
                    return;
                }

                JSONArray attachments = getAttachments(tab, recordId);

                result.put(SUCCESS, true);
                result.put("attachments", attachments);
                write(result);

            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            handleProcessError(result, e);
        }
    }

    private void handleErrorResponse(JSONObject result, String message) throws IOException {
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, message);
            write(result);
        } catch (Exception e) {
            logger.error("Error writing error response: " + e.getMessage(), e);
        }
    }

    private JSONArray getAttachments(Tab tab, String recordId) {
        JSONArray attachments = new JSONArray();
        try {
            OBCriteria<Attachment> criteria = OBDal.getInstance().createCriteria(Attachment.class);
            criteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE, OBDal.getInstance().getProxy(org.openbravo.model.ad.datamodel.Table.class, tab.getTable().getId())));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, recordId));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_ACTIVE, true));
            criteria.addOrder(Order.asc(Attachment.PROPERTY_NAME));
            
            List<Attachment> attachmentsList = criteria.list();
            for (Attachment attachment : attachmentsList) {
                JSONObject att = new JSONObject();
                att.put("id", attachment.getId());
                att.put("name", attachment.getName());
                attachments.put(att);
            }
        } catch (Exception e) {
            logger.warn("Could not query attachments using DAL: " + e.getMessage());
        }
        return attachments;
    }

    private void handleProcessError(JSONObject result, Exception e) throws IOException {
        logger.error("Error in EmailAttachmentService: " + e.getMessage(), e);
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, e.getMessage() != null ? e.getMessage() : "Failed to load attachments.");
            write(result);
        } catch (Exception jsonEx) {
            try {
                getResponse().getWriter().write("{\"success\":false,\"message\":\"Failed to load attachments.\"}");
            } catch (IOException ioEx) {
                logger.error("Fatal error writing response: " + ioEx.getMessage(), ioEx);
            }
        }
    }
}
