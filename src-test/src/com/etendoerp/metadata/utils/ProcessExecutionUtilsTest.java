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

    /**
     * Verifies that synchronous process execution delegates to {@link CallProcess}.
     */
    @Test
    public void testCallProcessDelegatesToCallProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap("param", "value");
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

    /**
     * Verifies that asynchronous execution delegates to {@link CallAsyncProcess}.
     */
    @Test
    public void testCallProcessAsyncDelegatesToCallAsyncProcess() {
        Process mockProcess = mock(Process.class);
        Map<String, String> parameters = Collections.singletonMap("async", "true");
        ProcessInstance expectedInstance = mock(ProcessInstance.class);

        try (MockedStatic<CallAsyncProcess> mockedAsync = mockStatic(CallAsyncProcess.class)) {
            CallAsyncProcess asyncProcess = mock(CallAsyncProcess.class);
            mockedAsync.when(CallAsyncProcess::getInstance).thenReturn(asyncProcess);
            when(asyncProcess.call(eq(mockProcess), eq("rec"), any(Map.class))).thenReturn(expectedInstance);

            ProcessInstance result = ProcessExecutionUtils.callProcessAsync(mockProcess, "rec", parameters);

            assertSame("Async call should return delegated instance", expectedInstance, result);
            verify(asyncProcess).call(mockProcess, "rec", parameters);
        }
    }
}
