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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.openbravo.base.exception.OBSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessRequest;
import org.openbravo.model.ad.ui.ProcessRun;

import com.etendoerp.metadata.exceptions.ForbiddenException;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.etendoerp.metadata.service.ProcessMonitorService.MonitorStatus;

/**
 * Unit tests for {@link ProcessMonitorService}: parameter parsing, status mapping, access control
 * and the list / detail responses built from AD_Process_Run.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessMonitorServiceTest extends AbstractMockedContextTest {

    private static final String RUN_ID = "RUN1";
    private static final long UPDATED = 1_700_000_000_000L;

    @Mock private Role role;

    @BeforeEach
    void setUp() {
        lenient().when(obContext.getRole()).thenReturn(role);
        lenient().when(request.getMethod()).thenReturn("GET");
    }

    @ParameterizedTest
    @CsvSource({"PRC,RUNNING", "SUC,COMPLETED", "ERR,FAILED", "KIL,OTHER", "MIS,OTHER", "SCH,OTHER"})
    void mapsStatusCodes(String code, MonitorStatus expected) {
        assertEquals(expected, MonitorStatus.fromCode(code));
    }

    @Test
    void mapsNullStatusToOther() {
        assertEquals(MonitorStatus.OTHER, MonitorStatus.fromCode(null));
    }

    @Test
    void parsesStatusFilter() {
        assertTrue(ProcessMonitorService.parseStatuses(null).isEmpty());
        assertTrue(ProcessMonitorService.parseStatuses(" ").isEmpty());
        Set<MonitorStatus> set = ProcessMonitorService.parseStatuses("failed, Running");
        assertEquals(Set.of(MonitorStatus.FAILED, MonitorStatus.RUNNING), set);
        assertThrows(UnprocessableContentException.class, () -> ProcessMonitorService.parseStatuses("bogus"));
    }

    @Test
    void parsesSinceAsMillisOrIso() {
        assertNull(ProcessMonitorService.parseSince(null));
        assertEquals(1000L, ProcessMonitorService.parseSince("1000").getTime());
        assertEquals(0L, ProcessMonitorService.parseSince("1970-01-01T00:00:00Z").getTime());
        assertThrows(UnprocessableContentException.class, () -> ProcessMonitorService.parseSince("yesterday"));
    }

    @Test
    void clampsLimit() {
        assertEquals(ProcessMonitorService.DEFAULT_LIMIT, ProcessMonitorService.parseLimit(null));
        assertEquals(1, ProcessMonitorService.parseLimit("0"));
        assertEquals(ProcessMonitorService.MAX_LIMIT, ProcessMonitorService.parseLimit("9999"));
        assertEquals(10, ProcessMonitorService.parseLimit("10"));
        assertThrows(UnprocessableContentException.class, () -> ProcessMonitorService.parseLimit("x"));
    }

    @Test
    void extractsRunId() {
        assertNull(ProcessMonitorService.extractRunId(null));
        assertNull(ProcessMonitorService.extractRunId("/process-monitor"));
        assertEquals(RUN_ID, ProcessMonitorService.extractRunId("/process-monitor/RUN1"));
        assertEquals(RUN_ID, ProcessMonitorService.extractRunId("/process-monitor/RUN1/"));
    }

    @Test
    void rejectsNonGetMethods() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        runWithMockedContext(() -> assertThrows(MethodNotAllowedException.class,
                () -> new ProcessMonitorService(request, response).process()));
    }

    @Test
    void rejectsRolesWithoutWindowAccess() throws Exception {
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, Collections.emptyList());
            assertThrows(ForbiddenException.class, () -> new ProcessMonitorService(request, response).process());
        });
    }

    @Test
    void listsExecutionsWithCursor() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            stubCriteria(ProcessRun.class, Arrays.asList(run("A", "ERR", UPDATED + 5), run("B", "PRC", UPDATED)));

            new ProcessMonitorService(request, response).process();

            JSONObject body = new JSONObject(responseCapture.toString());
            JSONArray executions = body.getJSONArray("executions");
            assertEquals(2, executions.length());
            assertEquals("FAILED", executions.getJSONObject(0).getString("status"));
            assertTrue(executions.getJSONObject(0).getBoolean("finished"));
            assertEquals("RUNNING", executions.getJSONObject(1).getString("status"));
            assertFalse(executions.getJSONObject(1).getBoolean("finished"));
            assertEquals("Alert Check Process", executions.getJSONObject(0).getString("processName"));
            assertEquals(UPDATED + 5, body.getLong("cursor"));
            assertFalse(body.getBoolean("hasMore"));
        });
    }

    @Test
    void keepsCursorWhenNothingChanged() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("since")).thenReturn(String.valueOf(UPDATED));
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            stubCriteria(ProcessRun.class, Collections.emptyList());

            new ProcessMonitorService(request, response).process();

            JSONObject body = new JSONObject(responseCapture.toString());
            assertEquals(0, body.getJSONArray("executions").length());
            assertEquals(UPDATED, body.getLong("cursor"));
        });
    }

    @Test
    void flagsHasMoreWhenLimitExceeded() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("limit")).thenReturn("1");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            stubCriteria(ProcessRun.class, Arrays.asList(run("A", "SUC", UPDATED + 1), run("B", "SUC", UPDATED)));

            new ProcessMonitorService(request, response).process();

            JSONObject body = new JSONObject(responseCapture.toString());
            assertEquals(1, body.getJSONArray("executions").length());
            assertTrue(body.getBoolean("hasMore"));
        });
    }

    @Test
    void detailExposesLogAsErrorMessageForFailedRuns() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor/RUN1");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            ProcessRun run = run(RUN_ID, "ERR", UPDATED);
            when(run.getLog()).thenReturn("java.lang.IllegalStateException: boom");
            OBCriteria<ProcessRun> c = stubCriteria(ProcessRun.class, Collections.emptyList());
            when(c.uniqueResult()).thenReturn(run);

            new ProcessMonitorService(request, response).process();

            JSONObject body = new JSONObject(responseCapture.toString());
            assertEquals("FAILED", body.getString("status"));
            assertEquals("java.lang.IllegalStateException: boom", body.getString("errorMessage"));
            assertEquals("java.lang.IllegalStateException: boom", body.getString("log"));
        });
    }

    @Test
    void detailOfUnknownRunIsNotFound() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor/NOPE");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            OBCriteria<ProcessRun> c = stubCriteria(ProcessRun.class, Collections.emptyList());
            when(c.uniqueResult()).thenReturn(null);
            assertThrows(NotFoundException.class, () -> new ProcessMonitorService(request, response).process());
        });
    }

    /** Stubs OBDal.getReadOnlyInstance() so createCriteria(type) returns a self-returning criteria listing rows. */
    private <T extends org.openbravo.base.structure.BaseOBObject> OBCriteria<T> stubCriteria(Class<T> type,
            List<T> rows) {
        @SuppressWarnings("unchecked")
        OBCriteria<T> criteria = mock(OBCriteria.class, Mockito.RETURNS_SELF);
        when(criteria.list()).thenReturn(rows);
        when(obDal.createCriteria(type)).thenReturn(criteria);
        dalStatic.when(org.openbravo.dal.service.OBDal::getReadOnlyInstance).thenReturn(obDal);
        return criteria;
    }

    /** Runs process() for a list call and returns the criteria used for the runs. */
    private OBCriteria<ProcessRun> runList(List<ProcessRun> rows, int runningCount) throws Exception {
        stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
        OBCriteria<ProcessRun> criteria = stubCriteria(ProcessRun.class, rows);
        when(criteria.count()).thenReturn(runningCount);
        new ProcessMonitorService(request, response).process();
        return criteria;
    }

    private static List<String> addedRestrictions(OBCriteria<ProcessRun> criteria) {
        ArgumentCaptor<Criterion> captor = ArgumentCaptor.forClass(Criterion.class);
        verify(criteria, atLeastOnce()).add(captor.capture());
        List<String> result = new ArrayList<>();
        captor.getAllValues().forEach(c -> result.add(c.toString()));
        return result;
    }

    private static boolean anyContains(List<String> values, String fragment) {
        return values.stream().anyMatch(v -> v.contains(fragment));
    }

    @Test
    void listAppliesBackgroundFilterOrderAndLimit() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        runWithMockedContext(() -> {
            OBCriteria<ProcessRun> c = runList(Collections.emptyList(), 7);
            List<String> added = addedRestrictions(c);
            assertTrue(anyContains(added, "p.background=true"), added.toString());
            assertFalse(anyContains(added, "updated>"), added.toString());
            ArgumentCaptor<Order> orders = ArgumentCaptor.forClass(Order.class);
            verify(c, times(2)).addOrder(orders.capture());
            assertEquals("updated desc", orders.getAllValues().get(0).toString());
            assertEquals("id desc", orders.getAllValues().get(1).toString());
            verify(c).setMaxResults(ProcessMonitorService.DEFAULT_LIMIT + 1);
            assertEquals(7, new JSONObject(responseCapture.toString()).getInt("runningCount"));
        });
    }

    @Test
    void deltaUsesStrictSinceAndAscendingOrder() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("since")).thenReturn("1000");
        when(request.getParameter("limit")).thenReturn("5");
        runWithMockedContext(() -> {
            OBCriteria<ProcessRun> c = runList(Collections.emptyList(), 0);
            assertTrue(anyContains(addedRestrictions(c), "updated>"));
            ArgumentCaptor<Order> orders = ArgumentCaptor.forClass(Order.class);
            verify(c, times(2)).addOrder(orders.capture());
            assertEquals("updated asc", orders.getAllValues().get(0).toString());
            assertEquals("id asc", orders.getAllValues().get(1).toString());
            verify(c).setMaxResults(6);
        });
    }

    @Test
    void backgroundFalseSkipsBackgroundFilterAndProcessIdFilters() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("background")).thenReturn("false");
        when(request.getParameter("processId")).thenReturn("PID9");
        runWithMockedContext(() -> {
            List<String> added = addedRestrictions(runList(Collections.emptyList(), 0));
            assertFalse(anyContains(added, "background"), added.toString());
            assertTrue(anyContains(added, "p.id=PID9"), added.toString());
        });
    }

    @Test
    void backgroundFalseWithoutProcessIdHasNoJoin() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("background")).thenReturn("false");
        runWithMockedContext(() -> {
            OBCriteria<ProcessRun> c = runList(Collections.emptyList(), 0);
            verify(c, never()).createAlias(anyString(), anyString());
        });
    }

    @Test
    void statusFilterRestrictsCodes() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("status")).thenReturn("failed");
        runWithMockedContext(() -> {
            List<String> added = addedRestrictions(runList(Collections.emptyList(), 0));
            assertTrue(anyContains(added, "status in (ERR)"), added.toString());
        });
    }

    @Test
    void otherAloneDoesNotEmitEmptyInAndMatchesNull() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("status")).thenReturn("OTHER");
        runWithMockedContext(() -> {
            List<String> added = addedRestrictions(runList(Collections.emptyList(), 0));
            assertFalse(anyContains(added, "in ()"), added.toString());
            assertTrue(anyContains(added, "status is null"), added.toString());
            assertTrue(anyContains(added, "not status in (PRC, SUC, ERR)"), added.toString());
        });
    }

    @Test
    void otherCombinedWithRunningKeepsBoth() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("status")).thenReturn("RUNNING,OTHER");
        runWithMockedContext(() -> {
            List<String> added = addedRestrictions(runList(Collections.emptyList(), 0));
            assertTrue(anyContains(added, "status in (PRC)"), added.toString());
            assertTrue(anyContains(added, "status is null"), added.toString());
        });
    }

    @Test
    void invalidSinceThroughProcessIsUnprocessable() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        when(request.getParameter("since")).thenReturn("yesterday");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            assertThrows(UnprocessableContentException.class,
                    () -> new ProcessMonitorService(request, response).process());
        });
    }

    @Test
    void roleMissingIsForbidden() throws Exception {
        when(obContext.getRole()).thenReturn(null);
        runWithMockedContext(() -> assertThrows(ForbiddenException.class,
                () -> new ProcessMonitorService(request, response).process()));
    }

    @Test
    void entityAccessDeniedIsForbidden() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor");
        runWithMockedContext(() -> {
            OBCriteria<WindowAccess> c = stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            OBCriteria<ProcessRun> runs = stubCriteria(ProcessRun.class, Collections.emptyList());
            when(runs.list()).thenThrow(new OBSecurityException("no read access"));
            assertThrows(ForbiddenException.class, () -> new ProcessMonitorService(request, response).process());
        });
    }

    @Test
    void trailingSlashListPathIsTheList() throws Exception {
        when(request.getPathInfo()).thenReturn("/com.etendoerp.metadata.meta/process-monitor/");
        assertNull(ProcessMonitorService.extractRunId("/com.etendoerp.metadata.meta/process-monitor/"));
        assertEquals(RUN_ID,
                ProcessMonitorService.extractRunId("/com.etendoerp.metadata.meta/process-monitor/RUN1"));
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            stubCriteria(ProcessRun.class, Collections.emptyList());
            new ProcessMonitorService(request, response).process();
            assertTrue(new JSONObject(responseCapture.toString()).has("executions"));
        });
    }

    @Test
    void nullProcessRequestAndCompletedDetail() throws Exception {
        when(request.getPathInfo()).thenReturn("/process-monitor/RUN1");
        runWithMockedContext(() -> {
            stubCriteria(WindowAccess.class, List.of(mock(WindowAccess.class)));
            ProcessRun run = run(RUN_ID, "SUC", UPDATED);
            when(run.getProcessRequest()).thenReturn(null);
            when(run.getLog()).thenReturn("done");
            OBCriteria<ProcessRun> c = stubCriteria(ProcessRun.class, Collections.emptyList());
            when(c.uniqueResult()).thenReturn(run);

            new ProcessMonitorService(request, response).process();

            JSONObject body = new JSONObject(responseCapture.toString());
            assertEquals("COMPLETED", body.getString("status"));
            assertTrue(body.isNull("errorMessage"));
            assertTrue(body.isNull("processId"));
            assertFalse(body.has("request"));
            assertEquals("done", body.getString("log"));
        });
    }

    private static ProcessRun run(String id, String status, long updated) {
        ProcessRun run = mock(ProcessRun.class);
        ProcessRequest req = mock(ProcessRequest.class);
        Process process = mock(Process.class);
        when(process.getId()).thenReturn("P1");
        when(process.getName()).thenReturn("Alert Check Process");
        when(process.isBackground()).thenReturn(true);
        when(req.getId()).thenReturn("REQ1");
        when(req.getProcess()).thenReturn(process);
        when(run.getId()).thenReturn(id);
        when(run.getStatus()).thenReturn(status);
        when(run.getProcessRequest()).thenReturn(req);
        when(run.getStartTime()).thenReturn(new Date(updated - 1000));
        when(run.getEndTime()).thenReturn("PRC".equals(status) ? null : new Date(updated));
        when(run.getUpdated()).thenReturn(new Date(updated));
        return run;
    }
}
