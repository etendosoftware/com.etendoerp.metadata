package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailSendService}.
 */
public class EmailSendServiceTest extends BaseMetadataServiceTest {

    private EmailSendService emailSendService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_SEND_PATH;
    }

    @Before
    public void setUpEmailSendService() {
        emailSendService = new EmailSendService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailSendServiceInstantiation() {
        assertNotNull("EmailSendService should be successfully instantiated", emailSendService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException {
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("tabId")).thenReturn(null);

        emailSendService.process();
        String responseContent = responseWriter.toString();
        
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean("success"));
            assertTrue("Should have error message", jsonResponse.has("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }
}
