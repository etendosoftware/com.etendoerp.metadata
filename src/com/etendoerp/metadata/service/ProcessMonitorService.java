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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.model.ad.ui.ProcessRun;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.etendoerp.metadata.utils.Constants;

/**
 * Process Monitor for background processes, built on the Process Scheduling data
 * (AD_PROCESS_REQUEST / AD_PROCESS_RUN).
 *
 * <ul>
 * <li>GET /meta/process-monitor : lists executions. Query params: {@code status}
 * (comma separated RUNNING, COMPLETED, FAILED, OTHER), {@code since} (cursor returned by the
 * previous call, epoch millis or ISO-8601), {@code limit} (default 50, max 200), {@code background}
 * (default true: only processes flagged as background), {@code processId}.</li>
 * <li>GET /meta/process-monitor/{runId} : execution detail including log / error message.</li>
 * </ul>
 *
 * Polling is cheap through {@code since}: only executions updated after the cursor are returned.
 * Access is restricted to roles with access to the Process Monitor or Process Request windows.
 */
public class ProcessMonitorService extends MetadataService {

    static final String PROCESS_MONITOR_WINDOW_ID = "EF3E837705944F4DBF398D683D36ACE0";
    static final String PROCESS_REQUEST_WINDOW_ID = "48E7EDE7D1104A59B46FC7449D9FB267";
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    static final String EXECUTIONS = "executions";
    static final String CURSOR = "cursor";
    static final String SERVER_TIME = "serverTime";
    static final String HAS_MORE = "hasMore";
    static final String RUNNING_COUNT = "runningCount";

    /** Normalized execution status exposed to the client. */
    enum MonitorStatus {
        RUNNING, COMPLETED, FAILED, OTHER;

        static MonitorStatus fromCode(String code) {
            if (org.openbravo.scheduling.Process.PROCESSING.equals(code)) {
                return RUNNING;
            }
            if (org.openbravo.scheduling.Process.SUCCESS.equals(code)) {
                return COMPLETED;
            }
            if (org.openbravo.scheduling.Process.ERROR.equals(code)) {
                return FAILED;
            }
            return OTHER;
        }

        /** Raw AD_Process_Run status codes that map to this status. */
        List<String> codes() {
            switch (this) {
                case RUNNING:
                    return Collections.singletonList(org.openbravo.scheduling.Process.PROCESSING);
                case COMPLETED:
                    return Collections.singletonList(org.openbravo.scheduling.Process.SUCCESS);
                case FAILED:
                    return Collections.singletonList(org.openbravo.scheduling.Process.ERROR);
                default:
                    return List.of();
            }
        }
    }

    /**
     * Creates a new ProcessMonitorService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public ProcessMonitorService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        if (!"GET".equalsIgnoreCase(getRequest().getMethod())) {
            throw new MethodNotAllowedException();
        }
        try {
            checkAccess();
            String runId = extractRunId(getRequest().getPathInfo());
            write(runId == null ? list() : detail(runId));
        } catch (ForbiddenException | NotFoundException | MethodNotAllowedException
                | UnprocessableContentException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    /** Returns the run id of a detail path, or null for the list path. */
    static String extractRunId(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        int idx = pathInfo.indexOf(Constants.PROCESS_MONITOR_DETAIL_PATH);
        if (idx < 0) {
            return null;
        }
        String id = pathInfo.substring(idx + Constants.PROCESS_MONITOR_DETAIL_PATH.length());
        while (id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        return id.isEmpty() ? null : id;
    }

    /** The role must have (active) access to the Process Monitor or the Process Request window. */
    private void checkAccess() {
        boolean allowed = false;
        try {
            OBContext.setAdminMode(true);
            Role role = OBContext.getOBContext().getRole();
            if (role != null) {
                OBCriteria<WindowAccess> criteria = OBDal.getReadOnlyInstance().createCriteria(WindowAccess.class);
                criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
                criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));
                criteria.add(Restrictions.in(WindowAccess.PROPERTY_WINDOW + ".id",
                        List.of(PROCESS_MONITOR_WINDOW_ID, PROCESS_REQUEST_WINDOW_ID)));
                criteria.setMaxResults(1);
                allowed = !criteria.list().isEmpty();
            }
        } finally {
            OBContext.restorePreviousMode();
        }
        if (!allowed) {
            throw new ForbiddenException("The current role has no access to the Process Monitor");
        }
    }

    private JSONObject list() throws JSONException {
        HttpServletRequest req = getRequest();
        Set<MonitorStatus> statuses = parseStatuses(req.getParameter("status"));
        Date since = parseSince(req.getParameter("since"));
        int limit = parseLimit(req.getParameter("limit"));
        boolean background = !"false".equalsIgnoreCase(req.getParameter("background"));
        String processId = req.getParameter("processId");
        long serverTime = System.currentTimeMillis();

        OBCriteria<ProcessRun> criteria = baseCriteria(background, processId);
        applyStatusFilter(criteria, statuses);
        boolean delta = since != null;
        if (delta) {
            criteria.add(Restrictions.gt(ProcessRun.PROPERTY_UPDATED, since));
        }
        // Initial load: newest first. Delta polling: oldest first, so the cursor never skips rows.
        criteria.addOrder(delta ? Order.asc(ProcessRun.PROPERTY_UPDATED) : Order.desc(ProcessRun.PROPERTY_UPDATED));
        criteria.setMaxResults(limit + 1);
        List<ProcessRun> runs = new ArrayList<>(criteria.list());

        boolean hasMore = runs.size() > limit;
        if (hasMore) {
            runs = runs.subList(0, limit);
        }

        JSONArray executions = new JSONArray();
        long newest = 0L;
        for (ProcessRun run : runs) {
            executions.put(toSummary(run, serverTime));
            if (run.getUpdated() != null) {
                newest = Math.max(newest, run.getUpdated().getTime());
            }
        }
        // The cursor is the newest update seen. With no rows it stays unchanged (delta) or is the server time (first load).
        long cursor = delta ? Math.max(since.getTime(), newest) : (runs.isEmpty() ? serverTime : newest);

        return new JSONObject()
                .put(EXECUTIONS, executions)
                .put(CURSOR, cursor)
                .put(HAS_MORE, hasMore)
                .put(SERVER_TIME, serverTime)
                .put(RUNNING_COUNT, countRunning(background, processId));
    }

    private OBCriteria<ProcessRun> baseCriteria(boolean background, String processId) {
        OBCriteria<ProcessRun> criteria = OBDal.getReadOnlyInstance().createCriteria(ProcessRun.class);
        if (background || (processId != null && !processId.isEmpty())) {
            criteria.createAlias(ProcessRun.PROPERTY_PROCESSREQUEST, "pr");
            criteria.createAlias("pr." + ProcessRequest.PROPERTY_PROCESS, "p");
            if (background) {
                criteria.add(Restrictions.eq("p." + Process.PROPERTY_BACKGROUND, true));
            }
            if (processId != null && !processId.isEmpty()) {
                criteria.add(Restrictions.eq("p.id", processId));
            }
        }
        return criteria;
    }

    private static void applyStatusFilter(OBCriteria<ProcessRun> criteria, Set<MonitorStatus> statuses) {
        if (statuses.isEmpty()) {
            return;
        }
        List<String> codes = new ArrayList<>();
        for (MonitorStatus status : statuses) {
            codes.addAll(status.codes());
        }
        List<String> known = new ArrayList<>();
        for (MonitorStatus status : EnumSet.complementOf(EnumSet.of(MonitorStatus.OTHER))) {
            known.addAll(status.codes());
        }
        if (statuses.contains(MonitorStatus.OTHER)) {
            // anything not RUNNING/COMPLETED/FAILED, or those explicitly requested
            criteria.add(Restrictions.or(Restrictions.in(ProcessRun.PROPERTY_STATUS, codes),
                    Restrictions.not(Restrictions.in(ProcessRun.PROPERTY_STATUS, known))));
        } else {
            criteria.add(Restrictions.in(ProcessRun.PROPERTY_STATUS, codes));
        }
    }

    private int countRunning(boolean background, String processId) {
        OBCriteria<ProcessRun> criteria = baseCriteria(background, processId);
        criteria.add(Restrictions.eq(ProcessRun.PROPERTY_STATUS, org.openbravo.scheduling.Process.PROCESSING));
        return criteria.count();
    }

    private JSONObject detail(String runId) throws JSONException {
        ProcessRun run = OBDal.getReadOnlyInstance().get(ProcessRun.class, runId);
        if (run == null) {
            throw new NotFoundException("Process execution not found: " + runId);
        }
        long now = System.currentTimeMillis();
        JSONObject json = toSummary(run, now);
        String log = run.getLog();
        json.put("log", log == null ? JSONObject.NULL : log);
        json.put("errorMessage", MonitorStatus.fromCode(run.getStatus()) == MonitorStatus.FAILED && log != null
                ? log : JSONObject.NULL);
        json.put("report", run.getReport() == null ? JSONObject.NULL : run.getReport());
        ProcessRequest request = run.getProcessRequest();
        if (request != null) {
            json.put("request", new JSONObject()
                    .put("id", request.getId())
                    .put("description", nullable(request.getDescription()))
                    .put("scheduleStatus", nullable(request.getStatus()))
                    .put("timing", nullable(request.getTiming()))
                    .put("channel", nullable(request.getChannel()))
                    .put("nextExecution", iso(request.getNextExecution()))
                    .put("previousExecution", iso(request.getPreviousExecution()))
                    .put("user", request.getUserContact() == null ? JSONObject.NULL
                            : request.getUserContact().getName()));
        }
        return json;
    }

    /** Builds the execution summary shared by the list and the detail responses. */
    static JSONObject toSummary(ProcessRun run, long now) throws JSONException {
        MonitorStatus status = MonitorStatus.fromCode(run.getStatus());
        ProcessRequest request = run.getProcessRequest();
        Process process = request != null ? request.getProcess() : null;
        long end = run.getEndTime() != null ? run.getEndTime().getTime() : now;
        Object durationMs = run.getStartTime() != null ? (Object) Math.max(0L, end - run.getStartTime().getTime())
                : JSONObject.NULL;

        return new JSONObject()
                .put("id", run.getId())
                .put("processRequestId", request == null ? JSONObject.NULL : request.getId())
                .put("processId", process == null ? JSONObject.NULL : process.getId())
                .put("processName", process == null ? JSONObject.NULL : nullable(process.getName()))
                .put("background", process != null && Boolean.TRUE.equals(process.isBackground()))
                .put("status", status.name())
                .put("statusCode", nullable(run.getStatus()))
                .put("finished", status != MonitorStatus.RUNNING)
                .put("startTime", iso(run.getStartTime()))
                .put("endTime", iso(run.getEndTime()))
                .put("durationMs", durationMs)
                .put("duration", nullable(run.getDuration()))
                .put("updated", iso(run.getUpdated()))
                .put("updatedMs", run.getUpdated() == null ? JSONObject.NULL : (Object) run.getUpdated().getTime());
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static Object iso(Date date) {
        return date == null ? JSONObject.NULL : Instant.ofEpochMilli(date.getTime()).toString();
    }

    /** Parses the comma separated status filter; empty set means no filter. */
    static Set<MonitorStatus> parseStatuses(String value) {
        Set<MonitorStatus> result = EnumSet.noneOf(MonitorStatus.class);
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String token : value.split(",")) {
            String name = token.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            try {
                result.add(MonitorStatus.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new UnprocessableContentException("Invalid status: " + token.trim());
            }
        }
        return result;
    }

    /** Parses the since cursor: epoch millis or ISO-8601 instant. Null when absent. */
    static Date parseSince(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return new Date(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            try {
                return Date.from(Instant.parse(trimmed));
            } catch (DateTimeParseException ex) {
                throw new UnprocessableContentException("Invalid since value: " + trimmed);
            }
        }
    }

    /** Parses the limit: default 50, clamped to [1, 200]. */
    static int parseLimit(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_LIMIT;
        }
        try {
            return Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            throw new UnprocessableContentException("Invalid limit: " + value.trim());
        }
    }
}
