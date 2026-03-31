package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailService}.
 */
public class EmailServiceTest extends BaseMetadataServiceTest {

    private EmailService emailService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_PATH;
    }

    @Before
    public void setUpEmailService() {
        emailService = new EmailService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailServiceInstantiation() {
        fail("Forced failure");
        assertNotNull("EmailService should be successfully instantiated", emailService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException {
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("tabId")).thenReturn(null);

        emailService.process();
        String responseContent = responseWriter.toString();
        
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for missing parameters", jsonResponse.getBoolean("success"));
            assertTrue("Should have error message", jsonResponse.has("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testProcessTabNotFound() throws IOException {
        when(mockRequest.getParameter("recordId")).thenReturn("some-id");
        when(mockRequest.getParameter("tabId")).thenReturn("non-existent-tab");

        emailService.process();
        String responseContent = responseWriter.toString();
        
        try {
            JSONObject jsonResponse = new JSONObject(responseContent);
            assertFalse("Success should be false for non-existent tab", jsonResponse.getBoolean("success"));
            assertEquals("Tab not found.", jsonResponse.getString("message"));
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }
}
