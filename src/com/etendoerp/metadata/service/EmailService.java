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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;

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
    protected void executeEmailAction(JSONObject result) throws Exception {
        ValidationContext ctx = validateEmailRequest(result);
        if (ctx == null) return;
        respond(result, true, null);
    }

    @Override
    protected String getFallbackErrorMessage() {
        return "Failed to validate email configuration.";
    }
}
