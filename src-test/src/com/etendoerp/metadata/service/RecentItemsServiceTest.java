/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.SessionInfo;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.erpCommon.utility.PropertyNotFoundException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Preference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.common.enterprise.Organization;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;

import static com.etendoerp.metadata.service.RecentItemsService.DEFAULT_RECENT_LIST_SIZE;
import static com.etendoerp.metadata.service.RecentItemsService.ITEMS;
import static com.etendoerp.metadata.service.RecentItemsService.RECENT_ITEMS_PROPERTY;
import static com.etendoerp.metadata.service.RecentItemsService.RECENT_LIST_SIZE_PROPERTY;
import static com.etendoerp.metadata.service.RecentItemsService.SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecentItemsService}: reading and storing the recent items list as an
 * AD_Preference scoped by client + organization + user + role and trimmed to the list size, which
 * defaults to 5 unless UINAVBA_RecentListSize is explicitly configured below System level.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecentItemsServiceTest extends AbstractMockedContextTest {

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String THREE_ITEMS = "[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]";
    private static final String TWO_ITEMS = "[{\"id\":\"a\"},{\"id\":\"b\"}]";
    private static final String FIRST_ITEM_ID = "a";
    private static final String SIZE_TWO = "2";

    @Mock private Client client;
    @Mock private Organization organization;
    @Mock private User user;
    @Mock private Role role;

    private MockedStatic<Preferences> preferencesStatic;

    /** Wires the mocked OBContext so it exposes the current client, organization, user and role. */
    @BeforeEach
    void setUpContext() {
        lenient().when(obContext.getCurrentClient()).thenReturn(client);
        lenient().when(obContext.getCurrentOrganization()).thenReturn(organization);
        lenient().when(obContext.getUser()).thenReturn(user);
        lenient().when(obContext.getRole()).thenReturn(role);
    }

    /**
     * Runs the action with OBContext, OBDal, Preferences and SessionInfo statics mocked. By default
     * no UINAVBA_RecentListSize preference applies.
     *
     * @param action the test logic to execute
     * @throws Exception if the action fails
     */
    private void runWithRecentItemsContext(ThrowingRunnable action) throws Exception {
        try (MockedStatic<Preferences> prefs = mockStatic(Preferences.class);
             MockedStatic<SessionInfo> ignored = mockStatic(SessionInfo.class)) {
            preferencesStatic = prefs;
            stubApplicablePreferences();
            runWithMockedContext(action);
        }
    }

    /**
     * Stubs the preferences applicable to the current context.
     *
     * @param preferences the preferences returned by {@link Preferences#getAllPreferences}
     */
    private void stubApplicablePreferences(Preference... preferences) {
        preferencesStatic.when(() -> Preferences.getAllPreferences(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(preferences));
    }

    /**
     * Builds a global UINAVBA_RecentListSize preference.
     *
     * @param value      the configured size
     * @param visibleAtRole the role it is visible at, or null for a System-level preference
     * @return the mocked preference
     */
    private static Preference listSizePreference(String value, Role visibleAtRole) {
        Preference preference = mock(Preference.class);
        when(preference.isPropertyList()).thenReturn(true);
        when(preference.getProperty()).thenReturn(RECENT_LIST_SIZE_PROPERTY);
        when(preference.getSearchKey()).thenReturn(value);
        when(preference.getVisibleAtRole()).thenReturn(visibleAtRole);
        return preference;
    }

    /**
     * Stubs the stored recent items value.
     *
     * @param value the stored JSON value
     */
    private void stubStoredItems(String value) {
        preferencesStatic.when(this::getStoredItems).thenReturn(value);
    }

    /**
     * Invokes the stored items lookup with the scope the service is expected to use.
     *
     * @return the stubbed value
     * @throws PropertyException never in practice, declared by the real method
     */
    private String getStoredItems() throws PropertyException {
        return Preferences.getPreferenceValue(eq(RECENT_ITEMS_PROPERTY), eq(true), eq(client), eq(organization),
                eq(user), eq(role), isNull());
    }

    /**
     * Executes the service for the given method and body and returns the parsed JSON response.
     *
     * @param method the HTTP method
     * @param body   the request body, or null for none
     * @return the written JSON response
     * @throws Exception if the service or the JSON parsing fails
     */
    private JSONObject process(String method, String body) throws Exception {
        lenient().when(request.getMethod()).thenReturn(method);
        if (body != null) {
            lenient().when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        }
        new RecentItemsService(request, response).process();
        return new JSONObject(responseCapture.toString());
    }

    /**
     * Builds a POST body holding the given items.
     *
     * @param items the JSON array literal
     * @return the request body
     */
    private static String postBody(String items) {
        return "{\"" + ITEMS + "\":" + items + "}";
    }

    /**
     * GET returns the stored items trimmed to the size explicitly configured for the role.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getReturnsStoredItemsTrimmedToConfiguredSize() throws Exception {
        runWithRecentItemsContext(() -> {
            stubApplicablePreferences(listSizePreference(SIZE_TWO, role));
            stubStoredItems(THREE_ITEMS);

            JSONObject result = process(GET, null);

            assertEquals(2, result.getInt(SIZE));
            assertEquals(new JSONArray(TWO_ITEMS).toString(), result.getJSONArray(ITEMS).toString());
        });
    }

    /**
     * GET returns an empty list and the default size when neither preference exists.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getReturnsEmptyListAndDefaultSizeWhenPreferencesAreMissing() throws Exception {
        runWithRecentItemsContext(() -> {
            preferencesStatic.when(this::getStoredItems).thenThrow(new PropertyNotFoundException());

            JSONObject result = process(GET, null);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, result.getInt(SIZE));
            assertEquals(0, result.getJSONArray(ITEMS).length());
        });
    }

    /**
     * GET ignores the System-level size shipped by core and uses the default size.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getIgnoresSystemLevelListSize() throws Exception {
        runWithRecentItemsContext(() -> {
            stubApplicablePreferences(listSizePreference(SIZE_TWO, null));
            stubStoredItems(THREE_ITEMS);

            JSONObject result = process(GET, null);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, result.getInt(SIZE));
            assertEquals(new JSONArray(THREE_ITEMS).toString(), result.getJSONArray(ITEMS).toString());
        });
    }

    /**
     * GET ignores other properties and window-scoped size preferences.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getIgnoresWindowScopedAndUnrelatedPreferences() throws Exception {
        runWithRecentItemsContext(() -> {
            Preference windowScoped = listSizePreference(SIZE_TWO, role);
            when(windowScoped.getWindow()).thenReturn(mock(Window.class));
            Preference otherProperty = listSizePreference(SIZE_TWO, role);
            when(otherProperty.getProperty()).thenReturn(RECENT_ITEMS_PROPERTY);
            Preference attribute = listSizePreference(SIZE_TWO, role);
            when(attribute.isPropertyList()).thenReturn(false);
            stubApplicablePreferences(windowScoped, otherProperty, attribute);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, process(GET, null).getInt(SIZE));
        });
    }

    /**
     * parseListSize falls back to the default for missing, invalid or non-positive values.
     *
     * @param value the configured value
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "abc", "0", "-1" })
    void parseListSizeUsesDefaultForInvalidValues(String value) {
        assertEquals(DEFAULT_RECENT_LIST_SIZE, RecentItemsService.parseListSize(value));
    }

    /** parseListSize accepts a positive integer surrounded by blanks. No checked exceptions are thrown. */
    @Test
    void parseListSizeAcceptsPositiveIntegers() {
        assertEquals(4, RecentItemsService.parseListSize(" 4 "));
    }

    /** A preference visible at the System client and organization only is System level. No exceptions. */
    @Test
    void isSystemLevelTreatsSystemClientAndOrganizationAsUnscoped() {
        Preference preference = listSizePreference(SIZE_TWO, null);
        Client systemClient = mock(Client.class);
        when(systemClient.getId()).thenReturn("0");
        when(preference.getVisibleAtClient()).thenReturn(systemClient);

        assertTrue(RecentItemsService.isSystemLevel(preference));
    }

    /** A preference visible at a client or for a user is not System level. No exceptions are thrown. */
    @Test
    void isSystemLevelIsFalseForClientOrUserScopedPreferences() {
        Preference clientScoped = listSizePreference(SIZE_TWO, null);
        when(clientScoped.getVisibleAtClient()).thenReturn(client);
        Preference userScoped = listSizePreference(SIZE_TWO, null);
        when(userScoped.getUserContact()).thenReturn(user);

        assertFalse(RecentItemsService.isSystemLevel(clientScoped));
        assertFalse(RecentItemsService.isSystemLevel(userScoped));
    }

    /**
     * GET returns an empty list when the stored value is null or not a JSON array.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getReturnsEmptyListWhenStoredValueIsNullOrInvalid() throws Exception {
        runWithRecentItemsContext(() -> {
            stubStoredItems(null);
            assertEquals(0, process(GET, null).getJSONArray(ITEMS).length());
        });
        runWithRecentItemsContext(() -> {
            stubStoredItems("not-json");
            assertEquals(0, process(GET, null).getJSONArray(ITEMS).length());
        });
    }

    /**
     * POST trims the received list, stores it scoped to client + org + user + role without
     * auditing nor org/client access check, flushes and returns the stored list.
     *
     * @throws Exception if the service fails
     */
    @Test
    void postStoresTrimmedItemsInTheCurrentScope() throws Exception {
        runWithRecentItemsContext(() -> {
            stubApplicablePreferences(listSizePreference(SIZE_TWO, role));

            JSONObject result = process(POST, postBody(THREE_ITEMS));

            String expected = new JSONArray(TWO_ITEMS).toString();
            assertEquals(expected, result.getJSONArray(ITEMS).toString());
            assertEquals(FIRST_ITEM_ID, result.getJSONArray(ITEMS).getJSONObject(0).getString("id"));
            preferencesStatic.verify(() -> Preferences.setPreferenceValue(eq(RECENT_ITEMS_PROPERTY), eq(expected),
                    eq(true), eq(client), eq(organization), eq(user), eq(role), isNull(), isNull()));
            verify(obDal).flush();
            // The System-owned (client 0) preference must be written without org/client access check,
            // otherwise updating it fails with "Client (0) ... is not present in ClientList".
            contextStatic.verify(OBContext::setAdminMode);
            contextStatic.verify(() -> OBContext.setAdminMode(true), never());
        });
    }

    /**
     * POST without an items array fails and stores nothing.
     *
     * @throws Exception if the test setup fails
     */
    @Test
    void postWithoutItemsThrowsInternalServerError() throws Exception {
        runWithRecentItemsContext(() -> {
            assertThrows(InternalServerException.class, () -> process(POST, "{}"));
            preferencesStatic.verify(() -> Preferences.setPreferenceValue(anyString(), anyString(), anyBoolean(),
                    any(), any(), any(), any(), any(), any()), never());
        });
    }

    /**
     * Unsupported HTTP methods are rejected as not found.
     *
     * @throws Exception if the test setup fails
     */
    @Test
    void processThrowsNotFoundForUnsupportedMethod() throws Exception {
        runWithRecentItemsContext(() -> assertThrows(NotFoundException.class, () -> process("DELETE", null)));
    }

    /**
     * trim keeps the whole list when it is shorter than the requested size.
     *
     * @throws Exception if the JSON handling fails
     */
    @Test
    void trimKeepsListShorterThanSize() throws Exception {
        assertEquals(new JSONArray(TWO_ITEMS).toString(),
                RecentItemsService.trim(new JSONArray(TWO_ITEMS), DEFAULT_RECENT_LIST_SIZE).toString());
    }
}
