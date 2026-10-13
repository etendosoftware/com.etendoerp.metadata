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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.service.GridConfigurationService.FIELD_CONFIGURATION;
import static com.etendoerp.metadata.service.GridConfigurationService.GRID_CONFIGURATION_ATTRIBUTE;
import static com.etendoerp.metadata.utils.Constants.DELETE;
import static com.etendoerp.metadata.utils.Constants.GET;
import static com.etendoerp.metadata.utils.Constants.PUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.SessionInfo;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnprocessableContentException;

/**
 * Unit tests for {@link GridConfigurationService}.
 * Covers reading the per-window map, merging and removing a tab configuration, removing the
 * preference when the map becomes empty, request validation and unsupported methods.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class GridConfigurationServiceTest {

    private static final String WINDOW_ID = "window-001";
    private static final String TAB_ID = "tab-001";
    private static final String OTHER_TAB_ID = "tab-002";
    private static final String USER_ID = "user-001";
    private static final String CLIENT_ID = "client-001";
    private static final String PREFERENCE_ID = "pref-001";
    private static final String TAB_CONFIG = "{\"version\":1,\"sizing\":{\"name\":200}}";
    private static final String OTHER_TAB_CONFIG = "{\"version\":1,\"sizing\":{\"name\":90}}";
    private static final String SIZING = "sizing";
    private static final String COLUMN_NAME = "name";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private OBDal mockOBDal;
    @Mock private OBContext mockOBContext;
    @Mock private Client mockClient;
    @Mock private User mockUser;
    @Mock private Window mockWindow;
    @Mock private Tab mockTab;
    @Mock private Preference mockPreference;

    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<Preferences> preferencesMock;
    private MockedStatic<SessionInfo> sessionInfoMock;

    private StringWriter responseWriter;
    private GridConfigurationService service;

    /**
     * Initialises static mocks, the window/tab/user stubs and the service under test.
     *
     * @throws IOException if the response writer cannot be configured
     */
    @Before
    public void setUp() throws IOException {
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

        obDalMock = mockStatic(OBDal.class);
        obContextMock = mockStatic(OBContext.class);
        preferencesMock = mockStatic(Preferences.class);
        sessionInfoMock = mockStatic(SessionInfo.class);

        obDalMock.when(OBDal::getInstance).thenReturn(mockOBDal);
        obContextMock.when(OBContext::getOBContext).thenReturn(mockOBContext);
        obContextMock.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        obContextMock.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);

        when(mockOBContext.getCurrentClient()).thenReturn(mockClient);
        when(mockOBContext.getUser()).thenReturn(mockUser);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockWindow.getId()).thenReturn(WINDOW_ID);
        when(mockTab.getWindow()).thenReturn(mockWindow);
        when(mockPreference.getId()).thenReturn(PREFERENCE_ID);
        when(mockOBDal.get(Window.class, WINDOW_ID)).thenReturn(mockWindow);
        when(mockOBDal.get(Tab.class, TAB_ID)).thenReturn(mockTab);
        givenStoredPreference(null);

        service = new GridConfigurationService(mockRequest, mockResponse);
    }

    /**
     * Closes all static mocks and clears thread-local state after each test.
     */
    @After
    public void tearDown() {
        obDalMock.close();
        obContextMock.close();
        preferencesMock.close();
        sessionInfoMock.close();
        MetadataService.clear();
    }

    // --- Helpers ---

    private void givenStoredPreference(String value) {
        List<Preference> preferences = Collections.emptyList();
        if (value != null) {
            when(mockPreference.getSearchKey()).thenReturn(value);
            preferences = Collections.singletonList(mockPreference);
        }
        preferencesMock.when(() -> Preferences.getPreferences(eq(GRID_CONFIGURATION_ATTRIBUTE), eq(false),
                eq(CLIENT_ID), isNull(), eq(USER_ID), isNull(), eq(WINDOW_ID)))
                .thenReturn(preferences);
    }

    private void givenGet(String windowId) {
        when(mockRequest.getMethod()).thenReturn(GET);
        when(mockRequest.getParameter(GridConfigurationService.FIELD_WINDOW_ID)).thenReturn(windowId);
    }

    private void givenPut(String body) throws IOException {
        when(mockRequest.getMethod()).thenReturn(PUT);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    private static String putBody(String tabId, String configuration) {
        return "{\"windowId\":\"" + WINDOW_ID + "\",\"tabId\":\"" + tabId + "\",\"configuration\":" + configuration
                + "}";
    }

    private JSONObject responseConfiguration() throws JSONException {
        return new JSONObject(responseWriter.toString()).getJSONObject(FIELD_CONFIGURATION);
    }

    private String verifyStoredValue() {
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        preferencesMock.verify(() -> Preferences.setPreferenceValue(eq(GRID_CONFIGURATION_ATTRIBUTE), value.capture(),
                eq(false), eq(mockClient), isNull(), eq(mockUser), isNull(),
                eq(mockWindow), isNull()));
        return value.getValue();
    }

    private void verifyNothingStored() {
        preferencesMock.verify(() -> Preferences.setPreferenceValue(anyString(), anyString(), anyBoolean(), any(),
                any(), any(), any(), any(), any()), never());
    }

    // --- GET ---

    /**
     * Verifies that GET returns an empty map when the user has no stored configuration.
     *
     * @throws Exception if the request cannot be processed or the response parsed
     */
    @Test
    public void testGetWithoutPreferenceReturnsEmptyMap() throws Exception {
        givenGet(WINDOW_ID);

        service.process();

        assertEquals(WINDOW_ID, new JSONObject(responseWriter.toString()).getString(GridConfigurationService.FIELD_WINDOW_ID));
        assertEquals(0, responseConfiguration().length());
    }

    /**
     * Verifies that GET returns the stored map keyed by tab.
     *
     * @throws Exception if the request cannot be processed or the response parsed
     */
    @Test
    public void testGetReturnsStoredMap() throws Exception {
        givenStoredPreference("{\"" + TAB_ID + "\":" + TAB_CONFIG + "}");
        givenGet(WINDOW_ID);

        service.process();

        assertEquals(200, responseConfiguration().getJSONObject(TAB_ID).getJSONObject(SIZING).getInt(COLUMN_NAME));
    }

    /**
     * Verifies that an unreadable stored value is ignored instead of failing the request.
     *
     * @throws Exception if the request cannot be processed or the response parsed
     */
    @Test
    public void testGetWithCorruptPreferenceReturnsEmptyMap() throws Exception {
        givenStoredPreference("not-json");
        givenGet(WINDOW_ID);

        service.process();

        assertEquals(0, responseConfiguration().length());
    }

    /**
     * Verifies that GET without a window id is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testGetWithoutWindowIdIsRejected() throws IOException {
        givenGet(null);

        service.process();
    }

    /**
     * Verifies that GET for an unknown window throws NotFoundException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = NotFoundException.class)
    public void testGetWithUnknownWindowThrowsNotFound() throws IOException {
        givenGet("unknown-window");

        service.process();
    }

    // --- PUT ---

    /**
     * Verifies that PUT stores the tab configuration when the user had none.
     *
     * @throws Exception if the request cannot be processed or the stored value parsed
     */
    @Test
    public void testPutCreatesTabConfiguration() throws Exception {
        givenPut(putBody(TAB_ID, TAB_CONFIG));

        service.process();

        JSONObject stored = new JSONObject(verifyStoredValue());
        assertEquals(200, stored.getJSONObject(TAB_ID).getJSONObject(SIZING).getInt(COLUMN_NAME));
        assertTrue(responseConfiguration().has(TAB_ID));
        verify(mockOBDal).flush();
    }

    /**
     * Verifies that PUT replaces only its own tab and keeps the other tabs of the window.
     *
     * @throws Exception if the request cannot be processed or the stored value parsed
     */
    @Test
    public void testPutMergesWithOtherTabs() throws Exception {
        givenStoredPreference("{\"" + OTHER_TAB_ID + "\":" + OTHER_TAB_CONFIG + "}");
        givenPut(putBody(TAB_ID, TAB_CONFIG));

        service.process();

        JSONObject stored = new JSONObject(verifyStoredValue());
        assertTrue(stored.has(TAB_ID));
        assertEquals(90, stored.getJSONObject(OTHER_TAB_ID).getJSONObject(SIZING).getInt(COLUMN_NAME));
    }

    /**
     * Verifies that PUT with a null configuration removes the tab and keeps the rest.
     *
     * @throws Exception if the request cannot be processed or the stored value parsed
     */
    @Test
    public void testPutNullRemovesTabAndKeepsOthers() throws Exception {
        givenStoredPreference("{\"" + TAB_ID + "\":" + TAB_CONFIG + ",\"" + OTHER_TAB_ID + "\":" + OTHER_TAB_CONFIG + "}");
        givenPut(putBody(TAB_ID, "null"));

        service.process();

        JSONObject stored = new JSONObject(verifyStoredValue());
        assertFalse(stored.has(TAB_ID));
        assertTrue(stored.has(OTHER_TAB_ID));
    }

    /**
     * Verifies that removing the last tab deletes the preference instead of storing an empty map.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPutNullOnLastTabRemovesPreference() throws IOException {
        givenStoredPreference("{\"" + TAB_ID + "\":" + TAB_CONFIG + "}");
        givenPut(putBody(TAB_ID, "null"));

        service.process();

        verify(mockOBDal).remove(mockPreference);
        verifyNothingStored();
    }

    /**
     * Verifies that removing a tab when nothing is stored neither writes nor deletes anything.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testPutNullWithoutPreferenceDoesNothing() throws IOException {
        givenPut(putBody(TAB_ID, "null"));

        service.process();

        verify(mockOBDal, never()).remove(any());
        verifyNothingStored();
    }

    /**
     * Verifies that a tab of another window is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPutWithTabOfAnotherWindowIsRejected() throws IOException {
        Window otherWindow = mock(Window.class);
        when(otherWindow.getId()).thenReturn("window-002");
        when(mockTab.getWindow()).thenReturn(otherWindow);
        givenPut(putBody(TAB_ID, TAB_CONFIG));

        service.process();
    }

    /**
     * Verifies that an unknown tab is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPutWithUnknownTabIsRejected() throws IOException {
        givenPut(putBody("unknown-tab", TAB_CONFIG));

        service.process();
    }

    /**
     * Verifies that a body without the configuration key is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPutWithoutConfigurationIsRejected() throws IOException {
        givenPut("{\"windowId\":\"" + WINDOW_ID + "\",\"tabId\":\"" + TAB_ID + "\"}");

        service.process();
    }

    /**
     * Verifies that a configuration that is neither an object nor null is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPutWithNonObjectConfigurationIsRejected() throws IOException {
        givenPut(putBody(TAB_ID, "\"wide\""));

        service.process();
    }

    /**
     * Verifies that a body that is not valid JSON is rejected.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = UnprocessableContentException.class)
    public void testPutWithInvalidJsonIsRejected() throws IOException {
        givenPut("{invalid");

        service.process();
    }

    // --- Other methods and lifecycle ---

    /**
     * Verifies that an unsupported HTTP method throws MethodNotAllowedException.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test(expected = MethodNotAllowedException.class)
    public void testUnsupportedMethodThrowsMethodNotAllowed() throws IOException {
        when(mockRequest.getMethod()).thenReturn(DELETE);

        service.process();
    }

    /**
     * Verifies that the request runs in admin mode without client/org access check (the preference is
     * stored in client 0) and restores the previous mode afterwards.
     *
     * @throws IOException if an I/O error occurs during processing
     */
    @Test
    public void testAdminModeIsSetAndRestored() throws IOException {
        givenGet(WINDOW_ID);

        service.process();

        obContextMock.verify(OBContext::setAdminMode);
        obContextMock.verify(OBContext::restorePreviousMode);
    }
}
