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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.email.EmailUtils;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Base class for email-related metadata services.
 * Provides shared utilities for SMTP config resolution, record validation,
 * and standard JSON response handling.
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
     * Returns the sender address from the SMTP configuration for the given organization,
     * or an empty string if none is configured.
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
     * @return the document status value, or null if not present
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

    // ── SMTP helpers ──────────────────────────────────────────────────────────

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
