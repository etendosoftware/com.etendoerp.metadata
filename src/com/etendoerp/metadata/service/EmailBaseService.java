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
 * Base class for email-related metadata services.
 * Provides shared utilities for SMTP config resolution, record validation,
 * attachment queries, and standard JSON response handling.
 */
public abstract class EmailBaseService extends MetadataService {

    protected static final String KEY_SUCCESS = "success";
    protected static final String KEY_MESSAGE = "message";

    /**
     * @param request  the incoming HTTP request
     * @param response the HTTP response to write to
     */
    protected EmailBaseService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    // ── Template method ───────────────────────────────────────────────────────

    /**
     * Handles OBContext lifecycle and delegates to {@link #executeEmailAction(JSONObject)}.
     * Subclasses implement only the business logic.
     */
    @Override
    public void process() throws IOException, ServletException {
        JSONObject result = new JSONObject();
        try {
            OBContext.setAdminMode(true);
            try {
                executeEmailAction(result);
            } finally {
                OBContext.restorePreviousMode();
            }
        } catch (Exception ex) {
            handleServiceError(result, ex, getFallbackErrorMessage());
        }
    }

    /**
     * Business logic to be implemented by each email service.
     *
     * @param result the JSON object to populate with the response
     * @throws Exception on any processing error
     */
    protected abstract void executeEmailAction(JSONObject result) throws Exception;

    /**
     * Returns the user-facing message used when an unexpected exception has no message.
     *
     * @return fallback error message
     */
    protected abstract String getFallbackErrorMessage();

    // ── Full email request validation ─────────────────────────────────────────

    /**
     * Validates the incoming email request: resolves the tab, record, SMTP config
     * and document status. Writes an error response and returns {@code null} on
     * any validation failure so the caller can return early.
     *
     * @param result the JSON object used to write error responses
     * @return a {@link ValidationContext} on success, or {@code null} on failure
     */
    protected ValidationContext validateEmailRequest(JSONObject result) throws Exception {
        String recordId = getRequest().getParameter("recordId");
        String tabId    = getRequest().getParameter("tabId");

        if (recordId == null || tabId == null) {
            respond(result, false, "Missing recordId or tabId parameter.");
            return null;
        }

        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        if (tab == null || tab.getTable() == null) {
            respond(result, false, "Tab not found.");
            return null;
        }

        String entityName = ModelProvider.getInstance()
                .getEntityByTableName(tab.getTable().getDBTableName()).getName();
        BaseOBObject dataRecord = OBDal.getInstance().get(entityName, recordId);
        if (dataRecord == null) {
            respond(result, false, "Record not found.");
            return null;
        }

        Organization org = resolveOrganization(dataRecord);
        String senderAddress = resolveSenderAddress(org);
        if (senderAddress.isEmpty()) {
            respond(result, false,
                    "No sender defined. Please check Email Server configuration in Client settings.");
            return null;
        }

        Object docStatus = getDocumentStatus(dataRecord);
        if (docStatus != null && !docStatus.equals("CO") && !docStatus.equals("CL")) {
            respond(result, false,
                    "Only completed or closed documents can be sent via email.");
            return null;
        }

        return new ValidationContext(tab, dataRecord, org, senderAddress, recordId);
    }

    /**
     * Carries the validated request context returned by {@link #validateEmailRequest(JSONObject)}.
     */
    protected static class ValidationContext {
        private final Tab tab;
        private final BaseOBObject dataRecord;
        private final Organization org;
        private final String senderAddress;
        private final String recordId;

        ValidationContext(Tab tab, BaseOBObject dataRecord, Organization org,
                String senderAddress, String recordId) {
            this.tab           = tab;
            this.dataRecord    = dataRecord;
            this.org           = org;
            this.senderAddress = senderAddress;
            this.recordId      = recordId;
        }

        /** @return the resolved {@link Tab} */
        public Tab getTab() { return tab; }
        /** @return the ERP record */
        public BaseOBObject getDataRecord() { return dataRecord; }
        /** @return the record's organization */
        public Organization getOrg() { return org; }
        /** @return the SMTP sender address */
        public String getSenderAddress() { return senderAddress; }
        /** @return the raw record ID from the request */
        public String getRecordId() { return recordId; }
    }

    // ── Attachment query against c_file ───────────────────────────────────────

    /**
     * Queries {@code c_file} for files attached to the given table/record combination.
     *
     * @param tableId  the {@code ad_table_id} value (numeric string, e.g. "259")
     * @param recordId the record ID (with or without dashes)
     * @return JSON array of {@code {id, name}} objects
     */
    protected JSONArray queryAttachments(String tableId, String recordId) {
        JSONArray attachments = new JSONArray();
        if (tableId == null || recordId == null) return attachments;
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

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Resolves the organization for the given record, falling back to the session organization.
     *
     * @param dataRecord the ERP record
     * @return the resolved organization
     */
    protected Organization resolveOrganization(BaseOBObject dataRecord) {
        Organization org = OBContext.getOBContext().getCurrentOrganization();
        try {
            Object orgObj = dataRecord.get("organization");
            if (orgObj instanceof Organization) {
                org = (Organization) orgObj;
            }
        } catch (Exception ex) {
            logger.debug("Could not retrieve organization from record: {}", ex.getMessage());
        }
        return org;
    }

    /**
     * Returns the sender address from the SMTP configuration, or empty string if none found.
     *
     * @param org the organization to look up SMTP config for
     * @return the sender address, or empty string
     */
    protected String resolveSenderAddress(Organization org) {
        EmailServerConfiguration emailConfig = getSmtpConfig(org);
        if (emailConfig == null || emailConfig.getSmtpServerSenderAddress() == null) {
            return "";
        }
        return emailConfig.getSmtpServerSenderAddress().trim();
    }

    /**
     * Returns the document status of the given record, checking both
     * {@code documentStatus} and {@code docstatus} properties.
     *
     * @param dataRecord the ERP record
     * @return the status value, or null if not present
     */
    protected Object getDocumentStatus(BaseOBObject dataRecord) {
        Object status = readProperty(dataRecord, "documentStatus");
        if (status == null) {
            status = readProperty(dataRecord, "docstatus");
        }
        return status;
    }

    /**
     * Safely reads a property from a record, returning null on any exception.
     *
     * @param dataRecord the ERP record
     * @param property   the property name
     * @return the property value, or null
     */
    protected Object readProperty(BaseOBObject dataRecord, String property) {
        try {
            return dataRecord.get(property);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Writes a JSON response with the given success flag and optional message.
     *
     * @param result  the JSON object to populate
     * @param success whether the operation succeeded
     * @param message optional message; ignored if null
     */
    protected void respond(JSONObject result, boolean success, String message) throws Exception {
        result.put(KEY_SUCCESS, success);
        if (message != null) {
            result.put(KEY_MESSAGE, message);
        }
        write(result);
    }

    /**
     * Logs an error and writes a failure response, with a plain-text fallback
     * if JSON serialization itself fails.
     *
     * @param result          the JSON result object
     * @param ex              the caught exception
     * @param fallbackMessage message to use when the exception has no message
     */
    protected void handleServiceError(JSONObject result, Exception ex, String fallbackMessage)
            throws IOException {
        logger.error("Error in {}: {}", getClass().getSimpleName(), ex.getMessage(), ex);
        try {
            respond(result, false,
                    ex.getMessage() != null ? ex.getMessage() : fallbackMessage);
        } catch (Exception ignored) {
            getResponse().getWriter()
                    .write("{\"success\":false,\"message\":\"" + fallbackMessage + "\"}");
        }
    }

    // ── Private SMTP helpers ──────────────────────────────────────────────────

    private EmailServerConfiguration getSmtpConfig(Organization org) {
        EmailServerConfiguration config = EmailUtils.getEmailConfiguration(org);
        if (config == null) {
            config = EmailUtils.getEmailConfiguration(OBContext.getOBContext().getCurrentOrganization());
        }
        if (config == null) {
            config = findAnySmtpConfig();
        }
        return config;
    }

    private EmailServerConfiguration findAnySmtpConfig() {
        try {
            OBCriteria<EmailServerConfiguration> crit =
                    OBDal.getInstance().createCriteria(EmailServerConfiguration.class);
            crit.add(Restrictions.isNotNull("smtpServerSenderAddress"));
            crit.setMaxResults(1);
            return (EmailServerConfiguration) crit.uniqueResult();
        } catch (Exception ex) {
            return null;
        }
    }
}
