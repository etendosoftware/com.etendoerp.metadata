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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.CallAsyncProcess;
import com.etendoerp.metadata.utils.ProcessExecutionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to execute processes and check their status.
 * Handles:
 * - POST /meta/process/execute : Executes a process asynchronously.
 * - GET /meta/process/status/{pInstanceId} : Checks the status of a process instance.
 */
public class ProcessExecutionService extends MetadataService {

    private static final ObjectMapper mapper = new ObjectMapper();

    public ProcessExecutionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        String method = getRequest().getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            handleExecute();
        } else if ("GET".equalsIgnoreCase(method)) {
            handleStatus();
        } else {
            throw new NotFoundException("Process execution endpoint not found");
        }
    }

    private void handleExecute() throws IOException {
        try {
            OBContext.setAdminMode(true);
            JsonNode root = mapper.readTree(getRequest().getInputStream());
            
            String processId = root.has("processId") ? root.get("processId").asText() : null;
            String recordId = root.has("recordId") ? root.get("recordId").asText() : null;
            
            if (processId == null) {
                throw new InternalServerException("Missing processId");
            }

            Process process = OBDal.getInstance().get(Process.class, processId);
            if (process == null) {
                throw new NotFoundException("Process not found: " + processId);
            }

            Map<String, String> parameters = new HashMap<>();
            if (root.has("parameters") && root.get("parameters").isObject()) {
                root.get("parameters").fields().forEachRemaining(entry -> {
                    parameters.put(entry.getKey(), entry.getValue().asText());
                });
            }

            ProcessInstance pInstance = ProcessExecutionUtils.callProcessAsync(process, recordId, parameters);
            
            JSONObject result = new JSONObject();
            result.put("pInstanceId", pInstance.getId());
            result.put("status", "STARTED");
            write(result);

        } catch (JSONException e) {
            throw new InternalServerException("Error building response", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleStatus() throws IOException {
        try {
            OBContext.setAdminMode(true);
            String pathInfo = getRequest().getPathInfo();
            if (pathInfo == null || !pathInfo.contains("/status/")) {
                throw new NotFoundException("Invalid status path");
            }
            
            String[] parts = pathInfo.split("/");
            String pInstanceId = parts[parts.length - 1];

            ProcessInstance pInstance = OBDal.getInstance().get(ProcessInstance.class, pInstanceId);
            if (pInstance == null) {
                throw new NotFoundException("Process Instance not found: " + pInstanceId);
            }

            // Refresh to get latest status from DB
            OBDal.getInstance().getSession().refresh(pInstance);

            JSONObject result = new JSONObject();
            result.put("pInstanceId", pInstance.getId());
            result.put("result", pInstance.getResult()); // 1 = Success, 0 = Error/Processing
            
            String errorMsg = pInstance.getErrorMsg();
            if (errorMsg != null) {
                errorMsg = OBMessageUtils.parseTranslation(errorMsg);
            }
            result.put("errorMsg", errorMsg);
            
            // Logic to determine if it's still processing
            // In CallAsyncProcess we set errorMsg to PROCESSING_MSG
            boolean isProcessing = pInstance.getResult() == 0 && CallAsyncProcess.PROCESSING_MSG.equals(pInstance.getErrorMsg());
            result.put("isProcessing", isProcessing);
            
            write(result);

        } catch (JSONException e) {
            throw new InternalServerException("Error building response", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
