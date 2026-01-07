package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.service.db.CallProcess;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for {@link ProcessExecutionUtils}.
 */
public class ProcessExecutionUtilsTest extends OBBaseTest {
    private static final String RECORD_STRING = "record";
    private static final String PROCESS_NAME = "ProcessName";
    private static final String PARAM_KEY = "param";
    private static final String PARAM_VALUE = "value";
    private static final String ASYNC_KEY = "async";
    private static final String TRUE_VALUE = "true";
    private static final String REC_ID = "rec";

    /**
     * Verifies that synchronous process execution delegates to {@link CallProcess}.
     */
    @Test
    public void testCallProcessDelegatesToCallProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap(PARAM_KEY, PARAM_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);

        try (MockedStatic<CallProcess> mockedCallProcess = mockStatic(CallProcess.class)) {
            CallProcess callProcess = mock(CallProcess.class);
            mockedCallProcess.when(CallProcess::getInstance).thenReturn(callProcess);
            when(callProcess.call(mockProcess, RECORD_STRING, parameters)).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcess(mockProcess, RECORD_STRING, parameters);

            assertSame("Result should match delegated instance", expectedInstance, result);
            verify(callProcess).call(mockProcess, RECORD_STRING, parameters);
        }
    }

    @Test
    public void testCallProcessByNameDelegatesToCallProcess() {
        Map<String, String> parameters = Collections.singletonMap(PARAM_KEY, PARAM_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);

        try (MockedStatic<CallProcess> mockedCallProcess = mockStatic(CallProcess.class)) {
            CallProcess callProcess = mock(CallProcess.class);
            mockedCallProcess.when(CallProcess::getInstance).thenReturn(callProcess);
            when(callProcess.call(PROCESS_NAME, RECORD_STRING, parameters)).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcess(PROCESS_NAME, RECORD_STRING, parameters);

            assertSame("Result should match delegated instance", expectedInstance, result);
            verify(callProcess).call(PROCESS_NAME, RECORD_STRING, parameters);
        }
    }

    @Test
    public void testCallProcessByNameWithCommitDelegatesToCallProcess() {
        Map<String, String> parameters = Collections.singletonMap(PARAM_KEY, PARAM_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);
        Boolean doCommit = true;

        try (MockedStatic<CallProcess> mockedCallProcess = mockStatic(CallProcess.class)) {
            CallProcess callProcess = mock(CallProcess.class);
            mockedCallProcess.when(CallProcess::getInstance).thenReturn(callProcess);
            when(callProcess.call(PROCESS_NAME, RECORD_STRING, parameters, doCommit)).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcess(PROCESS_NAME, RECORD_STRING, parameters,
                    doCommit);

            assertSame("Result should match delegated instance", expectedInstance, result);
            verify(callProcess).call(PROCESS_NAME, RECORD_STRING, parameters, doCommit);
        }
    }

    @Test
    public void testCallProcessWithCommitDelegatesToCallProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap(PARAM_KEY, PARAM_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);
        Boolean doCommit = false;

        try (MockedStatic<CallProcess> mockedCallProcess = mockStatic(CallProcess.class)) {
            CallProcess callProcess = mock(CallProcess.class);
            mockedCallProcess.when(CallProcess::getInstance).thenReturn(callProcess);
            when(callProcess.call(mockProcess, RECORD_STRING, parameters, doCommit)).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcess(mockProcess, RECORD_STRING, parameters,
                    doCommit);

            assertSame("Result should match delegated instance", expectedInstance, result);
            verify(callProcess).call(mockProcess, RECORD_STRING, parameters, doCommit);
        }
    }

    /**
     * Verifies that asynchronous execution delegates to {@link CallAsyncProcess}.
     */
    @Test
    public void testCallProcessAsyncDelegatesToCallAsyncProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap(ASYNC_KEY, TRUE_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);

        try (MockedStatic<CallAsyncProcess> mockedAsync = mockStatic(CallAsyncProcess.class)) {
            CallAsyncProcess asyncProcess = mock(CallAsyncProcess.class);
            mockedAsync.when(CallAsyncProcess::getInstance).thenReturn(asyncProcess);
            when(asyncProcess.call(eq(mockProcess), eq(REC_ID), any(Map.class))).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcessAsync(mockProcess, REC_ID, parameters);

            assertSame("Async call should return delegated instance", expectedInstance, result);
            verify(asyncProcess).call(mockProcess, REC_ID, parameters);
        }
    }

    @Test
    public void testCallProcessAsyncByNameDelegatesToCallAsyncProcess() {
        Map<String, String> parameters = Collections.singletonMap(ASYNC_KEY, TRUE_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);

        try (MockedStatic<CallAsyncProcess> mockedAsync = mockStatic(CallAsyncProcess.class)) {
            CallAsyncProcess asyncProcess = mock(CallAsyncProcess.class);
            mockedAsync.when(CallAsyncProcess::getInstance).thenReturn(asyncProcess);
            when(asyncProcess.call(eq(PROCESS_NAME), eq(REC_ID), any(Map.class))).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcessAsync(PROCESS_NAME, REC_ID, parameters);

            assertSame("Async call should return delegated instance", expectedInstance, result);
            verify(asyncProcess).call(PROCESS_NAME, REC_ID, parameters);
        }
    }

    @Test
    public void testCallProcessAsyncByNameWithCommitDelegatesToCallAsyncProcess() {
        Map<String, String> parameters = Collections.singletonMap(ASYNC_KEY, TRUE_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);
        Boolean doCommit = true;

        try (MockedStatic<CallAsyncProcess> mockedAsync = mockStatic(CallAsyncProcess.class)) {
            CallAsyncProcess asyncProcess = mock(CallAsyncProcess.class);
            mockedAsync.when(CallAsyncProcess::getInstance).thenReturn(asyncProcess);
            when(asyncProcess.call(eq(PROCESS_NAME), eq(REC_ID), any(Map.class), eq(doCommit)))
                    .thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcessAsync(PROCESS_NAME, REC_ID, parameters, doCommit);

            assertSame("Async call should return delegated instance", expectedInstance, result);
            verify(asyncProcess).call(PROCESS_NAME, REC_ID, parameters, doCommit);
        }
    }

    @Test
    public void testCallProcessAsyncWithCommitDelegatesToCallAsyncProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap(ASYNC_KEY, TRUE_VALUE);
        ProcessInstance expectedInstance = mock(ProcessInstance.class);
        Boolean doCommit = false;

        try (MockedStatic<CallAsyncProcess> mockedAsync = mockStatic(CallAsyncProcess.class)) {
            CallAsyncProcess asyncProcess = mock(CallAsyncProcess.class);
            mockedAsync.when(CallAsyncProcess::getInstance).thenReturn(asyncProcess);
            when(asyncProcess.call(eq(mockProcess), eq(REC_ID), any(Map.class), eq(doCommit)))
                    .thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcessAsync(mockProcess, REC_ID, parameters, doCommit);

            assertSame("Async call should return delegated instance", expectedInstance, result);
            verify(asyncProcess).call(mockProcess, REC_ID, parameters, doCommit);
        }
    }
}
