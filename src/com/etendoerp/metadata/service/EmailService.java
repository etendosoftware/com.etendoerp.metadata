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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

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
    protected void execute(JSONObject result) throws ServletException, IOException, JSONException {
        if (validateAndGetContext(result) == null) return;
        result.put(SUCCESS, true);
        write(result);
    }
}
