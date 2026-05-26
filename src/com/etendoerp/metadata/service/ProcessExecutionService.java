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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.CallAsyncProcess;
import com.etendoerp.metadata.utils.Constants;
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
    private static final String PARAMETERS_STRING = "parameters";
    private static final String P_INSTANCE_ID_KEY = "pInstanceId";
    private static final String PROCESS_ID_KEY = "processId";
    private static final String STATUS_KEY = "status";
    private static final int DEFAULT_HOURS = 24;

    /**
     * Constructs a ProcessExecutionService with the specified HTTP request and response.
     *
     * @param request The HTTP servlet request containing process execution parameters
     * @param response The HTTP servlet response for writing execution results
     */
    public ProcessExecutionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException, ServletException {
        String method = getRequest().getMethod();
        String pathInfo = getRequest().getPathInfo();

        if ("POST".equalsIgnoreCase(method)) {
            handleExecute();
        } else if ("GET".equalsIgnoreCase(method) && pathInfo != null && pathInfo.endsWith(Constants.PROCESS_EXECUTION_LIST_SUFFIX)) {
            handleList();
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
            
            String processId = root.has(PROCESS_ID_KEY) ? root.get(PROCESS_ID_KEY).asText() : null;
            String recordId = root.has("recordId") ? root.get("recordId").asText() : null;

            if (processId == null) {
                throw new InternalServerException("Missing processId");
            }

            Process process = OBDal.getInstance().get(Process.class, processId);
            if (process == null) {
                throw new NotFoundException("Process not found: " + processId);
            }

            Map<String, String> parameters = new HashMap<>();
            if (root.has(PARAMETERS_STRING) && root.get(PARAMETERS_STRING).isObject()) {
                root.get(PARAMETERS_STRING).fields().forEachRemaining(entry ->
                    parameters.put(entry.getKey(), entry.getValue().asText())
                );
            }

            ProcessInstance pInstance = ProcessExecutionUtils.callProcessAsync(process, recordId, parameters);
            
            JSONObject result = new JSONObject();
            result.put(P_INSTANCE_ID_KEY, pInstance.getId());
            result.put(STATUS_KEY, "STARTED");
            write(result);

        } catch (JSONException e) {
            throw new InternalServerException("Error building response", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private void handleList() throws IOException {
        try {
            OBContext.setAdminMode(true);
            String hoursParam = getRequest().getParameter("hours");
            String statusFilter = getRequest().getParameter(STATUS_KEY);
            int hours = parseHours(hoursParam);

            String currentUserId = OBContext.getOBContext().getUser().getId();
            Date cutoff = Date.from(Instant.now().minus(hours, ChronoUnit.HOURS));

            String hql = "e.process.background = true " +
                         "AND e.creationDate >= :cutoff " +
                         "AND e.createdBy.id = :userId " +
                         "ORDER BY e.creationDate DESC";

            OBQuery<ProcessInstance> query = OBDal.getInstance().createQuery(ProcessInstance.class, hql);
            query.setNamedParameter("cutoff", cutoff);
            query.setNamedParameter("userId", currentUserId);
            query.setMaxResult(200);

            List<ProcessInstance> instances = query.list();

            JSONArray items = new JSONArray();
            for (ProcessInstance pi : instances) {
                String status = deriveStatus(pi.getErrorMsg(), pi.getResult());
                if (statusFilter != null && !"ALL".equalsIgnoreCase(statusFilter) && !status.equals(statusFilter)) {
                    continue;
                }
                items.put(buildInstanceItem(pi, status));
            }

            JSONObject result = new JSONObject();
            result.put("items", items);
            result.put("totalCount", items.length());
            write(result);

        } catch (JSONException e) {
            throw new InternalServerException("Error building process list response", e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject buildInstanceItem(ProcessInstance pi, String status) throws JSONException {
        String errorMsg = pi.getErrorMsg();
        String displayError = CallAsyncProcess.PROCESSING_MSG.equals(errorMsg) ? null : errorMsg;
        if (displayError != null) {
            displayError = OBMessageUtils.parseTranslation(displayError);
        }

        Process proc = pi.getProcess();
        JSONObject item = new JSONObject();
        item.put(P_INSTANCE_ID_KEY, pi.getId());
        item.put(PROCESS_ID_KEY, proc != null ? proc.getId() : JSONObject.NULL);
        item.put("processName", proc != null ? proc.getName() : JSONObject.NULL);
        item.put(STATUS_KEY, status);
        item.put("startTime", pi.getCreationDate().toInstant().toString());
        item.put("updatedTime", pi.getUpdated().toInstant().toString());
        item.put("errorMsg", displayError != null ? displayError : JSONObject.NULL);
        item.put("userId", pi.getCreatedBy() != null ? pi.getCreatedBy().getId() : JSONObject.NULL);
        return item;
    }

    private int parseHours(String hoursParam) {
        if (hoursParam == null) {
            return DEFAULT_HOURS;
        }
        try {
            return Integer.parseInt(hoursParam);
        } catch (NumberFormatException e) {
            return DEFAULT_HOURS;
        }
    }

    private String deriveStatus(String errorMsg, Object result) {
        if (CallAsyncProcess.PROCESSING_MSG.equals(errorMsg)) {
            return "RUNNING";
        }
        if (result != null) {
            long val;
            if (result instanceof Number) {
                val = ((Number) result).longValue();
            } else {
                try { val = Long.parseLong(result.toString()); } catch (NumberFormatException e) { val = 0; }
            }
            if (val == 1L) return "COMPLETED";
        }
        return "FAILED";
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
            result.put(P_INSTANCE_ID_KEY, pInstance.getId());
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
