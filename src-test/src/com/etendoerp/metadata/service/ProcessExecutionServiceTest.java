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
import java.util.Map;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.hibernate.Session;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
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

            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            Process process = mock(Process.class);
            when(obDal.get(Process.class, PROCESS_ID)).thenReturn(process);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(PINSTANCE_ID);

            utilsStatic.when(() -> ProcessExecutionUtils.callProcessAsync(eq(process), eq(RECORD_ID), any(Map.class)))
                    .thenReturn(instance);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();

            String response = responseWriter.toString();
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
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(PINSTANCE_ID);
            when(instance.getResult()).thenReturn(1L);
            when(instance.getErrorMsg()).thenReturn(null);
            when(obDal.get(ProcessInstance.class, PINSTANCE_ID)).thenReturn(instance);

            Session session = mock(Session.class);
            when(obDal.getSession()).thenReturn(session);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();

            String response = responseWriter.toString();
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

        ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
        service.process();
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
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(Process.class, "nonexistent")).thenReturn(null);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();
        }
    }

    /**
     * Tests that status action throws NotFoundException when path is invalid.
     */
    @Test(expected = NotFoundException.class)
    public void testStatusActionWithInvalidPath() throws Exception {
        when(mockRequest.getMethod()).thenReturn(GET_METHOD);
        when(mockRequest.getPathInfo()).thenReturn("/invalid/path");

        ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
        service.process();
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
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(ProcessInstance.class, "nonexistent")).thenReturn(null);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();
        }
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
