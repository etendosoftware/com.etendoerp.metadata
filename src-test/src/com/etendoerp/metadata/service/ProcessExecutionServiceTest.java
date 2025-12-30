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

import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.ProcessExecutionUtils;

/**
 * Unit tests for {@link ProcessExecutionService}.
 */
public class ProcessExecutionServiceTest extends BaseMetadataServiceTest {

    @Override
    protected String getServicePath() {
        return Constants.PROCESS_PATH;
    }

    /**
     * Tests the execute action (POST) delegating to ProcessExecutionUtils.
     */
    @Test
    public void testExecuteAction() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        String body = "{\"processId\":\"123\",\"recordId\":\"REC\",\"parameters\":{\"foo\":\"bar\"}}";
        when(mockRequest.getInputStream()).thenReturn(new MockServletInputStream(body));

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<ProcessExecutionUtils> utilsStatic = mockStatic(ProcessExecutionUtils.class)) {

            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            Process process = mock(Process.class);
            when(obDal.get(Process.class, "123")).thenReturn(process);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn("PI1");

            utilsStatic.when(() -> ProcessExecutionUtils.callProcessAsync(eq(process), eq("REC"), any(Map.class)))
                .thenReturn(instance);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();

            String response = responseWriter.toString();
            assertTrue("Response should contain pInstanceId", response.contains("\"pInstanceId\":\"PI1\""));
            assertTrue("Response should indicate STARTED", response.contains("\"status\":\"STARTED\""));
        }
    }

    /**
     * Tests the status action (GET) reading process status from the database.
     */
    @Test
    public void testStatusAction() throws Exception {
        String piId = "PI1";
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(Constants.PROCESS_PATH + "/status/" + piId);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn(piId);
            when(instance.getResult()).thenReturn(1L);
            when(instance.getErrorMsg()).thenReturn(null);
            when(obDal.get(ProcessInstance.class, piId)).thenReturn(instance);

            Session session = mock(Session.class);
            when(obDal.getSession()).thenReturn(session);

            ProcessExecutionService service = new ProcessExecutionService(mockRequest, mockResponse);
            service.process();

            String response = responseWriter.toString();
            assertTrue("Response should contain pInstanceId", response.contains(piId));
            assertTrue("Response should indicate not processing", response.contains("\"isProcessing\":false"));
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
