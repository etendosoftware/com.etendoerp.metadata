package com.etendoerp.metadata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Unit tests for PreferencesService focusing on preference retrieval and processing.
 */
@ExtendWith(MockitoExtension.class)
class PreferencesServiceTest {

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private StringWriter responseWriter;
    private OBContext mockContext;
    private User mockUser;
    private Client mockClient;
    private Organization mockOrg;
    private Role mockRole;

    @BeforeEach
    void setUp() throws Exception {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

        mockContext = mock(OBContext.class);
        mockUser = mock(User.class);
        mockClient = mock(Client.class);
        mockOrg = mock(Organization.class);
        mockRole = mock(Role.class);

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockContext.getRole()).thenReturn(mockRole);

        when(mockUser.getId()).thenReturn("USER_ID");
        when(mockClient.getId()).thenReturn("CLIENT_ID");
        when(mockOrg.getId()).thenReturn("ORG_ID");
        when(mockRole.getId()).thenReturn("ROLE_ID");
    }

    @Test
    void processRetrievesAndWritesPreferences() throws Exception {
        Preference pref1 = mock(Preference.class);
        when(pref1.getProperty()).thenReturn("property1");
        when(pref1.getSearchKey()).thenReturn("value1");

        List<Preference> allPrefs = new ArrayList<>();
        allPrefs.add(pref1);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<Preferences> mockedPreferences = mockStatic(Preferences.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedPreferences.when(() -> Preferences.getAllPreferences("CLIENT_ID", "ORG_ID", "USER_ID", "ROLE_ID"))
                             .thenReturn(allPrefs);

            PreferencesService service = new PreferencesService(mockRequest, mockResponse);
            service.process();

            String output = responseWriter.toString();
            JSONObject result = new JSONObject(output);
            assertTrue(result.has("preferences"));
            JSONObject preferences = result.getJSONObject("preferences");
            assertEquals("value1", preferences.getString("property1"));
        }
    }

    @Test
    void processHandlesWindowSpecificPreferences() throws Exception {
        Preference pref1 = mock(Preference.class);
        when(pref1.getAttribute()).thenReturn("attribute1");
        when(pref1.getSearchKey()).thenReturn("valueW");
        org.openbravo.model.ad.ui.Window mockWindow = mock(org.openbravo.model.ad.ui.Window.class);
        when(mockWindow.getId()).thenReturn("WINDOW_ID");
        when(pref1.getWindow()).thenReturn(mockWindow);

        List<Preference> allPrefs = new ArrayList<>();
        allPrefs.add(pref1);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<Preferences> mockedPreferences = mockStatic(Preferences.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedPreferences.when(() -> Preferences.getAllPreferences("CLIENT_ID", "ORG_ID", "USER_ID", "ROLE_ID"))
                             .thenReturn(allPrefs);

            PreferencesService service = new PreferencesService(mockRequest, mockResponse);
            service.process();

            JSONObject result = new JSONObject(responseWriter.toString());
            JSONObject preferences = result.getJSONObject("preferences");
            assertEquals("valueW", preferences.getString("attribute1_WINDOW_ID"));
            assertEquals("valueW", preferences.getString("attribute1"));
        }
    }
}
