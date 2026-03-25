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

    private static final String USER_ID = "USER_ID";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String ORG_ID = "ORG_ID";
    private static final String ROLE_ID = "ROLE_ID";
    private static final String PREFERENCES_KEY = "preferences";
    private static final String VALUE_W = "valueW";

    @BeforeEach
    void setUp() throws Exception {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

        mockContext = mock(OBContext.class);
        User mockUser = mock(User.class);
        Client mockClient = mock(Client.class);
        Organization mockOrg = mock(Organization.class);
        Role mockRole = mock(Role.class);

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockContext.getRole()).thenReturn(mockRole);

        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
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
            mockedPreferences.when(() -> Preferences.getAllPreferences(CLIENT_ID, ORG_ID, USER_ID, ROLE_ID))
                             .thenReturn(allPrefs);

            PreferencesService service = new PreferencesService(mockRequest, mockResponse);
            service.process();

            String output = responseWriter.toString();
            JSONObject result = new JSONObject(output);
            assertTrue(result.has(PREFERENCES_KEY));
            JSONObject preferences = result.getJSONObject(PREFERENCES_KEY);
            assertEquals("value1", preferences.getString("property1"));
        }
    }

    @Test
    void processHandlesWindowSpecificPreferences() throws Exception {
        Preference pref1 = mock(Preference.class);
        when(pref1.getAttribute()).thenReturn("attribute1");
        when(pref1.getSearchKey()).thenReturn(VALUE_W);
        org.openbravo.model.ad.ui.Window mockWindow = mock(org.openbravo.model.ad.ui.Window.class);
        when(mockWindow.getId()).thenReturn("WINDOW_ID");
        when(pref1.getWindow()).thenReturn(mockWindow);

        List<Preference> allPrefs = new ArrayList<>();
        allPrefs.add(pref1);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<Preferences> mockedPreferences = mockStatic(Preferences.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedPreferences.when(() -> Preferences.getAllPreferences(CLIENT_ID, ORG_ID, USER_ID, ROLE_ID))
                             .thenReturn(allPrefs);

            PreferencesService service = new PreferencesService(mockRequest, mockResponse);
            service.process();

            JSONObject result = new JSONObject(responseWriter.toString());
            JSONObject preferences = result.getJSONObject(PREFERENCES_KEY);
            assertEquals(VALUE_W, preferences.getString("attribute1_WINDOW_ID"));
            assertEquals(VALUE_W, preferences.getString("attribute1"));
        }
    }
}
