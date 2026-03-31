package com.etendoerp.metadata.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.etendoerp.metadata.MetadataTestConstants;

/**
 * Test class for {@link EmailConfigService}.
 */
public class EmailConfigServiceTest extends BaseMetadataServiceTest {

    private EmailConfigService emailConfigService;

    @Override
    protected String getServicePath() {
        return MetadataTestConstants.EMAIL_CONFIG_PATH;
    }

    @Before
    public void setUpEmailConfigService() {
        emailConfigService = new EmailConfigService(mockRequest, mockResponse);
    }

    @Test
    public void testEmailConfigServiceInstantiation() {
        assertNotNull("EmailConfigService should be successfully instantiated", emailConfigService);
    }

    @Test
    public void testProcessMissingParameters() throws IOException {
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("tabId")).thenReturn(null);

        emailConfigService.process();
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
