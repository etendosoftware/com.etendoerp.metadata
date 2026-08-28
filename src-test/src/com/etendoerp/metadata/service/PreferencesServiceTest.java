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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Unit tests for PreferencesService focusing on preference retrieval and processing.
 *
 * The service mirrors classic PropertiesComponent: every preference is published under exactly one
 * key — {@code KEY_<windowId>} when the row is window-specific, the bare {@code KEY} otherwise — so
 * a global lookup can never be satisfied by a window-scoped row.
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
    private static final String ATTRIBUTE_NAME = "attribute1";
    private static final String PROPERTY_NAME = "property1";
    private static final String WINDOW_ID = "WINDOW_ID";
    private static final String SCOPED_ATTRIBUTE_KEY = ATTRIBUTE_NAME + "_" + WINDOW_ID;

    @BeforeEach
    void setUp() {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
    }

    /**
     * Stubs the collaborators the service reads while processing a request: the response writer and
     * the OBContext identity accessors. Kept out of {@code setUp} so the tests that only exercise
     * {@link PreferencesService#buildPreferenceKey} do not trip Mockito's strict stubbing.
     *
     * @throws Exception if the response writer cannot be stubbed
     */
    private void stubServiceCollaborators() throws Exception {
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

    /**
     * Builds a Preference mock. Only the accessor matching the requested identity is stubbed, so
     * Mockito's strict stubbing does not flag the unused one.
     *
     * @param property the preference property name, or {@code null} when the row uses an attribute
     * @param attribute the preference attribute name, or {@code null} when the row uses a property
     * @param value the preference value
     * @param windowId the AD window id the preference is scoped to, or {@code null} when global
     * @return the configured Preference mock
     */
    private Preference mockPreference(String property, String attribute, String value, String windowId) {
        Preference pref = mock(Preference.class);
        if (property != null) {
            when(pref.getProperty()).thenReturn(property);
        } else {
            when(pref.getProperty()).thenReturn(null);
            when(pref.getAttribute()).thenReturn(attribute);
        }
        when(pref.getSearchKey()).thenReturn(value);

        if (windowId != null) {
            Window mockWindow = mock(Window.class);
            when(mockWindow.getId()).thenReturn(windowId);
            when(pref.getWindow()).thenReturn(mockWindow);
        } else {
            when(pref.getWindow()).thenReturn(null);
        }
        return pref;
    }

    /**
     * Runs the service against the given preference list and returns the emitted preferences map.
     *
     * @param allPrefs the preferences {@code Preferences.getAllPreferences} should return
     * @return the {@code preferences} object of the JSON response
     * @throws Exception if the service fails or the response cannot be parsed as JSON
     */
    private JSONObject runProcessAndGetPreferences(List<Preference> allPrefs) throws Exception {
        stubServiceCollaborators();

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
             MockedStatic<Preferences> mockedPreferences = mockStatic(Preferences.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            mockedPreferences.when(() -> Preferences.getAllPreferences(CLIENT_ID, ORG_ID, USER_ID, ROLE_ID))
                             .thenReturn(allPrefs);

            PreferencesService service = new PreferencesService(mockRequest, mockResponse);
            service.process();
        }

        JSONObject result = new JSONObject(responseWriter.toString());
        assertTrue(result.has(PREFERENCES_KEY), "response must carry a preferences object");
        return result.getJSONObject(PREFERENCES_KEY);
    }

    /**
     * A global preference is published under its bare property name.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @Test
    void processRetrievesAndWritesPreferences() throws Exception {
        List<Preference> allPrefs = new ArrayList<>();
        allPrefs.add(mockPreference(PROPERTY_NAME, null, "value1", null));

        JSONObject preferences = runProcessAndGetPreferences(allPrefs);

        assertEquals("value1", preferences.getString(PROPERTY_NAME));
    }

    /**
     * A window-specific preference is published ONLY under {@code KEY_<windowId>}. Publishing the
     * bare key as well would let a global lookup resolve one window's value in every window.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @Test
    void processPublishesWindowSpecificPreferenceUnderWindowKeyOnly() throws Exception {
        List<Preference> allPrefs = new ArrayList<>();
        allPrefs.add(mockPreference(null, ATTRIBUTE_NAME, VALUE_W, WINDOW_ID));

        JSONObject preferences = runProcessAndGetPreferences(allPrefs);

        assertEquals(VALUE_W, preferences.getString(SCOPED_ATTRIBUTE_KEY));
        assertFalse(preferences.has(ATTRIBUTE_NAME), "a window-scoped row must not leak into the bare key");
    }

    /**
     * A global row and a window-scoped row for the same name coexist under distinct keys, so the
     * client can prefer the window entry and fall back to the global one.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @Test
    void processKeepsGlobalAndWindowEntriesForSameName() throws Exception {
        List<Preference> allPrefs = Arrays.asList(
                mockPreference(null, ATTRIBUTE_NAME, VALUE_W, WINDOW_ID),
                mockPreference(null, ATTRIBUTE_NAME, "globalValue", null));

        JSONObject preferences = runProcessAndGetPreferences(allPrefs);

        assertEquals(VALUE_W, preferences.getString(SCOPED_ATTRIBUTE_KEY));
        assertEquals("globalValue", preferences.getString(ATTRIBUTE_NAME));
    }

    /**
     * Two window-scoped rows for the same name stay independent, one key per window.
     *
     * @throws Exception if the service fails or the response cannot be parsed
     */
    @Test
    void processKeepsOneEntryPerWindowForSameName() throws Exception {
        String otherWindowId = "OTHER_WINDOW";
        List<Preference> allPrefs = Arrays.asList(
                mockPreference(null, ATTRIBUTE_NAME, VALUE_W, WINDOW_ID),
                mockPreference(null, ATTRIBUTE_NAME, "otherValue", otherWindowId));

        JSONObject preferences = runProcessAndGetPreferences(allPrefs);

        assertEquals(VALUE_W, preferences.getString(SCOPED_ATTRIBUTE_KEY));
        assertEquals("otherValue", preferences.getString(ATTRIBUTE_NAME + "_" + otherWindowId));
        assertFalse(preferences.has(ATTRIBUTE_NAME), "neither window-scoped row may claim the bare key");
    }

    /**
     * A preference with no window keeps its key untouched.
     */
    @Test
    void buildPreferenceKeyReturnsKeyWhenWindowIsNull() {
        assertEquals(PROPERTY_NAME, PreferencesService.buildPreferenceKey(PROPERTY_NAME, null));
    }

    /**
     * A window-specific preference gets the window id appended, as classic PropertiesComponent does.
     */
    @Test
    void buildPreferenceKeyAppendsWindowId() {
        Window mockWindow = mock(Window.class);
        when(mockWindow.getId()).thenReturn(WINDOW_ID);

        assertEquals(SCOPED_ATTRIBUTE_KEY, PreferencesService.buildPreferenceKey(ATTRIBUTE_NAME, mockWindow));
    }
}
