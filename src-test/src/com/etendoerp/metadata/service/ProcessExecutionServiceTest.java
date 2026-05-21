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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.hibernate.Session;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.CallAsyncProcess;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.ProcessExecutionUtils;

/**
 * Unit tests for {@link ProcessExecutionService}.
 */
public class ProcessExecutionServiceTest extends BaseMetadataServiceTest {

    private static final String POST_METHOD = "POST";
    private static final String GET_METHOD = "GET";
    private static final String PROCESS_ID = "123";
    private static final String RECORD_ID = "REC";
    private static final String PINSTANCE_ID = "PI1";
    private static final String PINSTANCE_ID_KEY = "\"pInstanceId\":\"";
    private static final String STATUS_STARTED = "\"status\":\"STARTED\"";
    private static final String IS_PROCESSING_FALSE = "\"isProcessing\":false";
    private static final String LIST_PATH = Constants.PROCESS_EXECUTION_PATH + Constants.PROCESS_EXECUTION_LIST_SUFFIX;
    private static final String HOURS_PARAM = "hours";
    private static final String STATUS_PARAM = "status";
    private static final String TOTAL_COUNT_ZERO = "\"totalCount\":0";

    @Override
    protected String getServicePath() {
        return Constants.PROCESS_PATH;
    }

    /**
     * Tests the execute action (POST) delegating to ProcessExecutionUtils.
     */
    @Test
    public void testExecuteAction() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        String body = "{\"processId\":\"" + PROCESS_ID + "\",\"recordId\":\"" + RECORD_ID
                + "\",\"parameters\":{\"foo\":\"bar\"}}";
        when(mockRequest.getInputStream()).thenReturn(new MockServletInputStream(body));

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
                MockedStatic<ProcessExecutionUtils> utilsStatic = mockStatic(ProcessExecutionUtils.class)) {

            OBDal obDal = mockObDal(obDalStatic);

            Process process = mock(Process.class);
            when(obDal.get(Process.class, PROCESS_ID)).thenReturn(process);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(PINSTANCE_ID);

            utilsStatic.when(() -> ProcessExecutionUtils.callProcessAsync(eq(process), eq(RECORD_ID), any(Map.class)))
                    .thenReturn(instance);

            String response = executeAndGetResponse();
            assertTrue("Response should contain pInstanceId",
                    response.contains(PINSTANCE_ID_KEY + PINSTANCE_ID + "\""));
            assertTrue("Response should indicate STARTED", response.contains(STATUS_STARTED));
        }
    }

    /**
     * Tests the status action (GET) reading process status from the database.
     */
    @Test
    public void testStatusAction() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET_METHOD);
        when(mockRequest.getPathInfo()).thenReturn(Constants.PROCESS_PATH + "/status/" + PINSTANCE_ID);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(PINSTANCE_ID);
            when(instance.getResult()).thenReturn(1L);
            when(instance.getErrorMsg()).thenReturn(null);
            when(obDal.get(ProcessInstance.class, PINSTANCE_ID)).thenReturn(instance);

            Session session = mock(Session.class);
            when(obDal.getSession()).thenReturn(session);

            String response = executeAndGetResponse();
            assertTrue("Response should contain pInstanceId", response.contains(PINSTANCE_ID));
            assertTrue("Response should indicate not processing", response.contains(IS_PROCESSING_FALSE));
        }
    }

    /**
     * Tests that execute action throws InternalServerException when processId is
     * missing.
     */
    @Test(expected = InternalServerException.class)
    public void testExecuteActionWithMissingProcessId() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        String body = "{\"recordId\":\"" + RECORD_ID + "\"}";
        when(mockRequest.getInputStream()).thenReturn(new MockServletInputStream(body));

        executeAndGetResponse();
    }

    /**
     * Tests that execute action throws NotFoundException when process is not found.
     */
    @Test(expected = NotFoundException.class)
    public void testExecuteActionWithNonExistentProcess() throws Exception {
        when(mockRequest.getMethod()).thenReturn(POST_METHOD);
        String body = "{\"processId\":\"nonexistent\",\"recordId\":\"" + RECORD_ID + "\"}";
        when(mockRequest.getInputStream()).thenReturn(new MockServletInputStream(body));

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            when(obDal.get(Process.class, "nonexistent")).thenReturn(null);

            executeAndGetResponse();
        }
    }

    /**
     * Tests that status action throws NotFoundException when path is invalid.
     */
    @Test(expected = NotFoundException.class)
    public void testStatusActionWithInvalidPath() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET_METHOD);
        when(mockRequest.getPathInfo()).thenReturn("/invalid/path");

        executeAndGetResponse();
    }

    /**
     * Tests that status action throws NotFoundException when process instance is
     * not found.
     */
    @Test(expected = NotFoundException.class)
    public void testStatusActionWithNonExistentProcessInstance() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET_METHOD);
        when(mockRequest.getPathInfo()).thenReturn(Constants.PROCESS_PATH + "/status/nonexistent");

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            when(obDal.get(ProcessInstance.class, "nonexistent")).thenReturn(null);

            executeAndGetResponse();
        }
    }

    /**
     * Tests handleList returns a COMPLETED item when result is 1.
     */
    @Test
    public void testListActionReturnsCompletedItem() throws Exception {
        setupListRequest(null, null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.singletonList(buildMockInstance(PINSTANCE_ID, 1L, null, false)));

            String response = executeAndGetResponse();
            assertTrue("Response should contain COMPLETED status", response.contains("\"status\":\"COMPLETED\""));
            assertTrue("Response should contain totalCount 1", response.contains("\"totalCount\":1"));
        }
    }

    /**
     * Tests handleList returns a RUNNING item when errorMsg equals PROCESSING_MSG.
     */
    @Test
    public void testListActionReturnsRunningItem() throws Exception {
        setupListRequest(null, null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.singletonList(
                    buildMockInstance(PINSTANCE_ID, 0L, CallAsyncProcess.PROCESSING_MSG, false)));

            String response = executeAndGetResponse();
            assertTrue("Response should contain RUNNING status", response.contains("\"status\":\"RUNNING\""));
        }
    }

    /**
     * Tests handleList returns a FAILED item and translates the error message.
     */
    @Test
    public void testListActionReturnsFailedItemWithErrorMsg() throws Exception {
        setupListRequest(null, null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBMessageUtils> msgStatic = mockStatic(OBMessageUtils.class)) {

            OBDal obDal = mockObDal(obDalStatic);
            msgStatic.when(() -> OBMessageUtils.parseTranslation(any(String.class)))
                     .thenAnswer(inv -> inv.getArgument(0));
            buildMockQuery(obDal, Collections.singletonList(
                    buildMockInstance(PINSTANCE_ID, 0L, "Process failed", false)));

            String response = executeAndGetResponse();
            assertTrue("Response should contain FAILED status", response.contains("\"status\":\"FAILED\""));
            assertTrue("Response should contain the error message", response.contains("Process failed"));
        }
    }

    /**
     * Tests handleList with status filter includes a matching item.
     */
    @Test
    public void testListActionWithStatusFilterIncludesMatching() throws Exception {
        setupListRequest(null, "COMPLETED");

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.singletonList(buildMockInstance(PINSTANCE_ID, 1L, null, false)));

            String response = executeAndGetResponse();
            assertTrue("COMPLETED filter should keep COMPLETED item", response.contains("\"totalCount\":1"));
        }
    }

    /**
     * Tests handleList with status filter excludes a non-matching item.
     */
    @Test
    public void testListActionWithStatusFilterExcludesNonMatching() throws Exception {
        setupListRequest(null, "COMPLETED");

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.singletonList(
                    buildMockInstance(PINSTANCE_ID, 0L, CallAsyncProcess.PROCESSING_MSG, false)));

            String response = executeAndGetResponse();
            assertTrue("COMPLETED filter should exclude RUNNING item", response.contains(TOTAL_COUNT_ZERO));
        }
    }

    /**
     * Tests handleList with ALL filter includes all items regardless of status.
     */
    @Test
    public void testListActionWithAllStatusFilter() throws Exception {
        setupListRequest(null, "ALL");

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Arrays.asList(
                buildMockInstance("PI1", 1L, null, false),
                buildMockInstance("PI2", 0L, CallAsyncProcess.PROCESSING_MSG, false)
            ));

            String response = executeAndGetResponse();
            assertTrue("ALL filter should include both items", response.contains("\"totalCount\":2"));
        }
    }

    /**
     * Tests handleList with a valid custom hours parameter.
     */
    @Test
    public void testListActionWithCustomHoursParam() throws Exception {
        setupListRequest("48", null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.emptyList());

            assertTrue("Response should be valid with custom hours",
                    executeAndGetResponse().contains(TOTAL_COUNT_ZERO));
        }
    }

    /**
     * Tests handleList with an invalid hours parameter falls back to the default of 24.
     */
    @Test
    public void testListActionWithInvalidHoursParam() throws Exception {
        setupListRequest("notANumber", null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.emptyList());

            assertTrue("Invalid hours should not throw and should return valid response",
                    executeAndGetResponse().contains(TOTAL_COUNT_ZERO));
        }
    }

    /**
     * Tests handleList returns an empty items array when no instances exist.
     */
    @Test
    public void testListActionEmptyList() throws Exception {
        setupListRequest(null, null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.emptyList());

            String response = executeAndGetResponse();
            assertTrue("Response should show empty items", response.contains("\"items\":[]"));
            assertTrue("Response should show totalCount 0", response.contains(TOTAL_COUNT_ZERO));
        }
    }

    /**
     * Tests handleList writes null for userId when createdBy is null.
     */
    @Test
    public void testListActionWithNullCreatedBy() throws Exception {
        setupListRequest(null, null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mockObDal(obDalStatic);
            buildMockQuery(obDal, Collections.singletonList(buildMockInstance(PINSTANCE_ID, 1L, null, true)));

            String response = executeAndGetResponse();
            assertTrue("userId should be null when createdBy is null", response.contains("\"userId\":null"));
        }
    }

    private void setupListRequest(String hours, String status) {
        when(mockRequest.getMethod()).thenReturn(GET_METHOD);
        when(mockRequest.getPathInfo()).thenReturn(LIST_PATH);
        when(mockRequest.getParameter(HOURS_PARAM)).thenReturn(hours);
        when(mockRequest.getParameter(STATUS_PARAM)).thenReturn(status);
    }

    private OBDal mockObDal(MockedStatic<OBDal> obDalStatic) {
        OBDal obDal = mock(OBDal.class);
        obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
        return obDal;
    }

    private String executeAndGetResponse() throws Exception {
        ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
        service.process();
        return responseWriter.toString();
    }

    @SuppressWarnings("unchecked")
    private ProcessInstance buildMockInstance(String id, Long result, String errorMsg, boolean nullCreatedBy) {
        ProcessInstance pi = mock(ProcessInstance.class);
        Process proc = mock(Process.class);
        when(pi.getId()).thenReturn(id);
        when(pi.getProcess()).thenReturn(proc);
        when(proc.getId()).thenReturn("PROC_" + id);
        when(proc.getName()).thenReturn("Process " + id);
        when(pi.getResult()).thenReturn(result);
        when(pi.getErrorMsg()).thenReturn(errorMsg);
        Date now = new Date();
        when(pi.getCreationDate()).thenReturn(now);
        when(pi.getUpdated()).thenReturn(now);
        if (nullCreatedBy) {
            when(pi.getCreatedBy()).thenReturn(null);
        } else {
            User user = mock(User.class);
            when(user.getId()).thenReturn("USER1");
            when(pi.getCreatedBy()).thenReturn(user);
        }
        return pi;
    }

    @SuppressWarnings("unchecked")
    private OBQuery<ProcessInstance> buildMockQuery(OBDal obDal, List<ProcessInstance> instances) {
        OBQuery<ProcessInstance> query = mock(OBQuery.class);
        when(obDal.createQuery(eq(ProcessInstance.class), any(String.class))).thenReturn(query);
        when(query.list()).thenReturn(instances);
        return query;
    }

    private static class MockServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        MockServletInputStream(String body) {
            this.delegate = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // No-op for tests
        }
    }
}
