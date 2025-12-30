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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.utils.ProcessUtils;
import org.codehaus.jettison.json.JSONException;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.builders.ReportAndProcessBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Service to return report and process metadata including parameters and configuration.
 * This service handles requests to /meta/report-and-process/{processId} and returns a JSON response
 * with all the process metadata needed to display the parameter form in the UI.
 * @author Futit Services S.L.
 */
public class ReportAndProcessService extends MetadataService {
    public ReportAndProcessService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);

            // Extract process ID from path
            String pathInfo = getRequest().getPathInfo();
            String processId = ProcessUtils.extractProcessId(pathInfo, "report-and-process");

            // Fetch process definition from database
            Process process = OBDal.getInstance().get(Process.class, processId);

            if (process == null) {
                throw new NotFoundException("Report and Process not found with id: " + processId);
            }

            // Build and return JSON response
            write(new ReportAndProcessBuilder(process).toJSON());

        } catch (JSONException e) {
            logger.error("Error building process metadata JSON: " + e.getMessage(), e);
            throw new InternalServerException("Failed to build process metadata: " + e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
