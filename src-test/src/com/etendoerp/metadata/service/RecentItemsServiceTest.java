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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.database.SessionInfo;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyConflictException;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.erpCommon.utility.PropertyNotFoundException;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import java.io.BufferedReader;
import java.io.StringReader;

import static com.etendoerp.metadata.service.RecentItemsService.DEFAULT_RECENT_LIST_SIZE;
import static com.etendoerp.metadata.service.RecentItemsService.ITEMS;
import static com.etendoerp.metadata.service.RecentItemsService.RECENT_ITEMS_PROPERTY;
import static com.etendoerp.metadata.service.RecentItemsService.RECENT_LIST_SIZE_PROPERTY;
import static com.etendoerp.metadata.service.RecentItemsService.SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RecentItemsService}: reading and storing the recent items list as an
 * AD_Preference scoped by client + organization + user + role and trimmed to UINAVBA_RecentListSize.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecentItemsServiceTest extends AbstractMockedContextTest {

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String THREE_ITEMS = "[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]";
    private static final String TWO_ITEMS = "[{\"id\":\"a\"},{\"id\":\"b\"}]";
    private static final String FIRST_ITEM_ID = "a";
    private static final String SIZE_THREE = "3";

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
     * Runs the action with OBContext, OBDal, Preferences and SessionInfo statics mocked.
     *
     * @param action the test logic to execute
     * @throws Exception if the action fails
     */
    private void runWithRecentItemsContext(ThrowingRunnable action) throws Exception {
        try (MockedStatic<Preferences> prefs = mockStatic(Preferences.class);
             MockedStatic<SessionInfo> ignored = mockStatic(SessionInfo.class)) {
            preferencesStatic = prefs;
            runWithMockedContext(action);
        }
    }

    /**
     * Stubs the value returned for a preference property in the current context.
     *
     * @param property the preference property name
     * @param value    the value to return
     */
    private void stubPreference(String property, String value) {
        preferencesStatic.when(() -> getPreferenceValue(property)).thenReturn(value);
    }

    /**
     * Stubs a preference lookup that fails with the given exception.
     *
     * @param property  the preference property name
     * @param exception the exception to throw
     */
    private void stubPreferenceFailure(String property, PropertyException exception) {
        preferencesStatic.when(() -> getPreferenceValue(property)).thenThrow(exception);
    }

    /**
     * Invokes the static lookup with the scope the service is expected to use.
     *
     * @param property the preference property name
     * @return the stubbed value
     * @throws PropertyException never in practice, declared by the real method
     */
    private String getPreferenceValue(String property) throws PropertyException {
        return Preferences.getPreferenceValue(eq(property), eq(true), eq(client), eq(organization), eq(user),
                eq(role), isNull());
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
     * GET returns the stored items trimmed to the configured size.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getReturnsStoredItemsTrimmedToConfiguredSize() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, "2");
            stubPreference(RECENT_ITEMS_PROPERTY, THREE_ITEMS);

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
            stubPreferenceFailure(RECENT_LIST_SIZE_PROPERTY, new PropertyNotFoundException());
            stubPreferenceFailure(RECENT_ITEMS_PROPERTY, new PropertyNotFoundException());

            JSONObject result = process(GET, null);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, result.getInt(SIZE));
            assertEquals(0, result.getJSONArray(ITEMS).length());
        });
    }

    /**
     * GET falls back to the default size when the size preference is not a number.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getUsesDefaultSizeWhenSizeIsNotANumber() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, "abc");
            stubPreference(RECENT_ITEMS_PROPERTY, THREE_ITEMS);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, process(GET, null).getInt(SIZE));
        });
    }

    /**
     * GET falls back to the default size when the size preference is not positive.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getUsesDefaultSizeWhenSizeIsNotPositive() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, "0");
            stubPreference(RECENT_ITEMS_PROPERTY, THREE_ITEMS);

            assertEquals(DEFAULT_RECENT_LIST_SIZE, process(GET, null).getInt(SIZE));
        });
    }

    /**
     * GET falls back to the default size when the size preference has no value or is in conflict.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getUsesDefaultSizeWhenSizeIsNullOrConflicting() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, null);
            stubPreference(RECENT_ITEMS_PROPERTY, THREE_ITEMS);
            assertEquals(DEFAULT_RECENT_LIST_SIZE, process(GET, null).getInt(SIZE));
        });
        runWithRecentItemsContext(() -> {
            stubPreferenceFailure(RECENT_LIST_SIZE_PROPERTY, new PropertyConflictException());
            stubPreference(RECENT_ITEMS_PROPERTY, THREE_ITEMS);
            assertEquals(DEFAULT_RECENT_LIST_SIZE, process(GET, null).getInt(SIZE));
        });
    }

    /**
     * GET returns an empty list when the stored value is null or not a JSON array.
     *
     * @throws Exception if the service fails
     */
    @Test
    void getReturnsEmptyListWhenStoredValueIsNullOrInvalid() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, SIZE_THREE);
            stubPreference(RECENT_ITEMS_PROPERTY, null);
            assertEquals(0, process(GET, null).getJSONArray(ITEMS).length());
        });
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, SIZE_THREE);
            stubPreference(RECENT_ITEMS_PROPERTY, "not-json");
            assertEquals(0, process(GET, null).getJSONArray(ITEMS).length());
        });
    }

    /**
     * POST trims the received list, stores it scoped to client + org + user + role without
     * auditing, flushes and returns the stored list.
     *
     * @throws Exception if the service fails
     */
    @Test
    void postStoresTrimmedItemsInTheCurrentScope() throws Exception {
        runWithRecentItemsContext(() -> {
            stubPreference(RECENT_LIST_SIZE_PROPERTY, "2");

            JSONObject result = process(POST, postBody(THREE_ITEMS));

            String expected = new JSONArray(TWO_ITEMS).toString();
            assertEquals(expected, result.getJSONArray(ITEMS).toString());
            assertEquals(FIRST_ITEM_ID, result.getJSONArray(ITEMS).getJSONObject(0).getString("id"));
            preferencesStatic.verify(() -> Preferences.setPreferenceValue(eq(RECENT_ITEMS_PROPERTY), eq(expected),
                    eq(true), eq(client), eq(organization), eq(user), eq(role), isNull(), isNull()));
            verify(obDal).flush();
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
            stubPreference(RECENT_LIST_SIZE_PROPERTY, SIZE_THREE);

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
