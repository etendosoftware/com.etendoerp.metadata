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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.email.EmailUtils;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Base class for metadata services providing common utility methods.
 */
public abstract class MetadataService {
    private static final ThreadLocal<HttpServletRequest> requestThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<HttpServletResponse> responseThreadLocal = new ThreadLocal<>();
    protected final Logger logger = LogManager.getLogger(this.getClass());

    protected static final String SUCCESS = "success";
    protected static final String MESSAGE = "message";
    protected static final String RECORD_ID = "recordId";
    protected static final String TAB_ID = "tabId";

    public MetadataService(HttpServletRequest request, HttpServletResponse response) {
        requestThreadLocal.set(request);
        responseThreadLocal.set(response);
    }

    public static void clear() {
        requestThreadLocal.remove();
        responseThreadLocal.remove();
    }

    protected HttpServletRequest getRequest() {
        return requestThreadLocal.get();
    }

    protected HttpServletResponse getResponse() {
        return responseThreadLocal.get();
    }

    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                execute(result);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof ServletException) throw (ServletException) e;
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Core logic to be implemented by subclasses.
     * @param result The JSONObject to be populated with the result.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException if an I/O error occurs.
     * @throws JSONException if a JSON-related error occurs.
     */
    protected void execute(JSONObject result) throws ServletException, IOException, JSONException {
    }

    /**
     * Validates and retrieves the context for metadata services.
     * @param result The JSONObject to store error messages if validation fails.
     * @return The MetadataContext if validation succeeds, null otherwise.
     * @throws IOException if an I/O error occurs.
     * @throws JSONException if a JSON error occurs.
     */
    protected MetadataContext validateAndGetContext(JSONObject result) throws IOException, JSONException {
        String recordId = getRequestedRecordId(result);
        if (recordId == null) return null;
        
        Tab tab = getRequestedTab(result);
        if (tab == null) return null;

        BaseOBObject dataRecord = getRecord(tab, recordId);
        if (dataRecord == null) {
            handleErrorResponse(result, "Record not found.");
            return null;
        }

        Organization org = getRecordOrganization(dataRecord);
        EmailServerConfiguration emailConfig = getEmailConfiguration(org);
        
        String senderAddress = (emailConfig != null && emailConfig.getSmtpServerSenderAddress() != null)
                ? emailConfig.getSmtpServerSenderAddress().trim() : "";

        if (senderAddress.isEmpty()) {
            handleErrorResponse(result, "No sender defined. Please check Email Server configuration in Client settings.");
            return null;
        }

        if (!checkDocumentStatus(dataRecord)) {
            handleErrorResponse(result, "Only completed or closed documents can be sent via email.");
            return null;
        }

        return new MetadataContext(tab, dataRecord, org, emailConfig, senderAddress);
    }

    /**
     * Writes the given JSONObject to the response.
     * @param data The JSONObject to write.
     * @throws IOException if an I/O error occurs.
     */
    protected void write(JSONObject data) throws IOException {
        HttpServletResponse response = getResponse();
        if (response == null || response.isCommitted()) {
            return;
        }
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (Writer writer = response.getWriter()) {
            writer.write(data.toString());
        } catch (IOException e) {
            logger.warn("Error writing response: ", e);
            throw e;
        }
    }

    protected void handleErrorResponse(JSONObject result, String message) throws IOException {
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, message);
        } catch (JSONException e) {
            logger.error("Error populating error response: ", e);
        }
    }

    protected void handleProcessError(String serviceName, JSONObject result, Exception e) throws IOException {
        logger.error("Error in {}: ", serviceName, e);
        try {
            result.put(SUCCESS, false);
            result.put(MESSAGE, e.getMessage() != null ? e.getMessage() : "An unexpected error occurred.");
        } catch (JSONException jsonEx) {
            logger.error("Fatal error populating JSON error: ", jsonEx);
            try {
                HttpServletResponse response = getResponse();
                if (response != null && !response.isCommitted()) {
                    response.getWriter().write("{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
                }
            } catch (IOException ioEx) {
                logger.error("Fatal error writing fallback response: ", ioEx);
            }
        }
    }

    protected Tab getRequestedTab(JSONObject result) throws IOException {
        String tabId = getRequest().getParameter(TAB_ID);
        if (tabId == null) {
            handleErrorResponse(result, "Missing " + TAB_ID + " parameter.");
            return null;
        }
        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null) {
            handleErrorResponse(result, "Tab not found.");
            return null;
        }
        if (tab.getTable() == null) {
            handleErrorResponse(result, "Table not found for the given tab.");
            return null;
        }
        return tab;
    }

    protected String getRequestedRecordId(JSONObject result) throws IOException {
        String recordId = getRequest().getParameter(RECORD_ID);
        if (recordId == null) {
            handleErrorResponse(result, "Missing " + RECORD_ID + " parameter.");
            return null;
        }
        return recordId;
    }

    protected BaseOBObject getRecord(Tab tab, String recordId) {
        if (tab == null || tab.getTable() == null) {
            return null;
        }
        Entity entity = ModelProvider.getInstance().getEntityByTableName(tab.getTable().getDBTableName());
        if (entity == null) {
            return null;
        }
        return OBDal.getInstance().get(entity.getName(), recordId);
    }

    protected Organization getRecordOrganization(BaseOBObject dataRecord) {
        Organization org = OBContext.getOBContext().getCurrentOrganization();
        if (dataRecord != null && dataRecord.getEntity().hasProperty("organization")) {
            try {
                Object orgObj = dataRecord.get("organization");
                if (orgObj instanceof Organization) {
                    org = (Organization) orgObj;
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve organization from record: {}", e.getMessage());
            }
        }
        return org;
    }

    protected EmailServerConfiguration getEmailConfiguration(Organization org) {
        EmailServerConfiguration config = EmailUtils.getEmailConfiguration(org);
        if (config == null) {
            config = EmailUtils.getEmailConfiguration(OBContext.getOBContext().getCurrentOrganization());
        }
        if (config == null) {
            try {
                OBCriteria<EmailServerConfiguration> crit = OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
                crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
                crit.setMaxResults(1);
                config = (EmailServerConfiguration) crit.uniqueResult();
            } catch (Exception e) {
                logger.debug("Could not find any fallback Email Server Configuration: {}", e.getMessage());
            }
        }
        return config;
    }

    protected JSONArray getRecordAttachments(String tableId, String recordId) {
        JSONArray recordAttachments = new JSONArray();
        if (recordId == null || tableId == null) {
            return recordAttachments;
        }
        try {
            OBCriteria<Attachment> criteria = OBDal.getInstance().createCriteria(Attachment.class);
            criteria.add(Restrictions.eq(Attachment.PROPERTY_TABLE, OBDal.getInstance().getProxy(org.openbravo.model.ad.datamodel.Table.class, tableId)));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_RECORD, recordId));
            criteria.add(Restrictions.eq(Attachment.PROPERTY_ACTIVE, true));
            criteria.addOrder(Order.asc(Attachment.PROPERTY_NAME));

            List<Attachment> attachmentsList = criteria.list();
            for (Attachment attachment : attachmentsList) {
                JSONObject att = new JSONObject();
                att.put("id", attachment.getId());
                att.put("name", attachment.getName());
                recordAttachments.put(att);
            }
        } catch (Exception e) {
            logger.warn("Could not load record attachments: {}", e.getMessage());
        }
        return recordAttachments;
    }

    protected boolean checkDocumentStatus(BaseOBObject dataRecord) {
        if (dataRecord == null) {
            return true;
        }
        Object status = null;
        if (dataRecord.getEntity().hasProperty("documentStatus")) {
            status = dataRecord.get("documentStatus");
        } else if (dataRecord.getEntity().hasProperty("docstatus")) {
            status = dataRecord.get("docstatus");
        }
        return status == null || status.equals("CO") || status.equals("CL");
    }

    protected String safeString(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    /**
     * DTO to store the context of a metadata request.
     */
    protected static class MetadataContext {
        private final Tab tab;
        private final BaseOBObject dataRecord;
        private final Organization org;
        private final EmailServerConfiguration emailConfig;
        private final String senderAddress;

        public MetadataContext(Tab tab, BaseOBObject dataRecord, Organization org,
                               EmailServerConfiguration emailConfig, String senderAddress) {
            this.tab = tab;
            this.dataRecord = dataRecord;
            this.org = org;
            this.emailConfig = emailConfig;
            this.senderAddress = senderAddress;
        }

        public Tab getTab() { return tab; }
        public BaseOBObject getDataRecord() { return dataRecord; }
        public Organization getOrg() { return org; }
        public EmailServerConfiguration getEmailConfig() { return emailConfig; }
        public String getSenderAddress() { return senderAddress; }
    }
}
