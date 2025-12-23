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

import org.codehaus.jettison.json.JSONException;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.builders.ProcessDefinitionBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * Service to return process definition metadata including parameters and configuration.
 * This service handles requests to /meta/process/{processId} and returns a JSON response
 * with all the process metadata needed to display the parameter form in the UI.
 */
public class ProcessMetadataService extends MetadataService {
    public ProcessMetadataService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);

            // Extract process ID from path
            String pathInfo = getRequest().getPathInfo();
            String processId = extractProcessId(pathInfo);

            // Fetch process definition from database
            Process process = OBDal.getInstance().get(Process.class, processId);

            if (process == null) {
                throw new NotFoundException("Process not found with id: " + processId);
            }

            // Build and return JSON response
            write(new ProcessDefinitionBuilder(process).toJSON());

        } catch (JSONException e) {
            logger.error("Error building process metadata JSON: " + e.getMessage(), e);
            throw new InternalServerException("Failed to build process metadata: " + e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Extracts the process ID from the request path.
     * Expected path format: /meta/process/{processId}
     *
     * @param pathInfo the request path info
     * @return the process ID
     * @throws NotFoundException if the path format is invalid
     */
    private String extractProcessId(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            throw new NotFoundException("Process ID is required");
        }

        // Remove leading slash and "com.etendoerp.metadata.meta/" prefix if present
        String cleanPath = pathInfo.replace("/com.etendoerp.metadata.meta/", "/");

        // Expected format: /process/{processId}
        String[] parts = cleanPath.split("/");

        if (parts.length < 3 || !"process".equals(parts[1])) {
            throw new NotFoundException("Invalid process path format: " + pathInfo);
        }

        return parts[2];
    }
}
