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

import com.etendoerp.metadata.data.UserFavorite;
import com.etendoerp.metadata.exceptions.InternalServerException;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.common.enterprise.Organization;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoritesServiceCoverageTest extends AbstractMockedContextTest {

    private static final String USER_ID = "user-001";
    private static final String ROLE_ID = "role-001";
    private static final String MENU_ID = "menu-001";
    private static final String FAVORITES_PATH = "/favorites";
    private static final String FAVORITES_TOGGLE_PATH = "/favorites/toggle";
    private static final String ITEMS_KEY = "items";

    @Mock OBProvider obProvider;

    private void runWithFavoritesContext(ThrowingRunnable action) throws Exception {
        try (MockedStatic<OBProvider> providerStatic = mockStatic(OBProvider.class)) {
            providerStatic.when(OBProvider::getInstance).thenReturn(obProvider);

            runWithMockedContext(() -> {
                lenient().when(obContext.getCurrentClient()).thenReturn(mock(Client.class));
                lenient().when(obContext.getCurrentOrganization()).thenReturn(mock(Organization.class));

                User mockUser = mock(User.class);
                lenient().when(mockUser.getId()).thenReturn(USER_ID);
                lenient().when(obContext.getUser()).thenReturn(mockUser);

                Role mockRole = mock(Role.class);
                lenient().when(mockRole.getId()).thenReturn(ROLE_ID);
                lenient().when(obContext.getRole()).thenReturn(mockRole);

                action.run();
            });
        }
    }

    private void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    @SuppressWarnings("unchecked")
    private UserFavorite setupToggleAddScenario(Long maxSeqResult) throws Exception {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("POST");
        setRequestBody("{\"menuId\":\"" + MENU_ID + "\"}");

        when(obDal.get(Menu.class, MENU_ID)).thenReturn(mock(Menu.class));

        Query<Long> seqQuery = mock(Query.class);
        when(session.createQuery(any(String.class), eq(Long.class)))
                .thenReturn(seqQuery);
        when(seqQuery.setParameter(anyString(), anyString())).thenReturn(seqQuery);
        when(seqQuery.uniqueResult()).thenReturn(0L).thenReturn(maxSeqResult);

        UserFavorite mockFav = mock(UserFavorite.class);
        when(obProvider.get(UserFavorite.class)).thenReturn(mockFav);

        when(obDal.get(User.class, USER_ID)).thenReturn(mock(User.class));
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(mock(Role.class));

        return mockFav;
    }

    private void assertNotFoundForMethodAndPath(String method, String path) {
        when(request.getPathInfo()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(com.etendoerp.metadata.exceptions.NotFoundException.class, svc::process);
    }

    @Test
    void saveExceptionEvictsEntityAndRethrows() throws Exception {
        runWithFavoritesContext(() -> {
            UserFavorite mockFav = setupToggleAddScenario(10L);
            doThrow(new RuntimeException("constraint violation")).when(session).save(any());

            FavoritesService svc = new FavoritesService(request, response);
            assertThrows(InternalServerException.class, svc::process);

            verify(session).evict(mockFav);
        });
    }

    @Test
    void addFavoriteWithNullMaxSeqnoDefaultsToTen() throws Exception {
        runWithFavoritesContext(() -> {
            UserFavorite mockFav = setupToggleAddScenario(null);

            FavoritesService svc = new FavoritesService(request, response);
            svc.process();

            verify(mockFav).setSequenceNo(10L);
            assertTrue(responseCapture.toString().contains("added"));
        });
    }

    @Test
    void processThrowsNotFoundForPutMethod() {
        assertNotFoundForMethodAndPath("PUT", FAVORITES_TOGGLE_PATH);
    }

    @Test
    void processThrowsNotFoundForDeleteMethod() {
        assertNotFoundForMethodAndPath("DELETE", FAVORITES_TOGGLE_PATH);
    }

    @Test
    void processThrowsNotFoundForWrongPathSuffix() {
        assertNotFoundForMethodAndPath("POST", FAVORITES_PATH + "/list");
    }

    @SuppressWarnings("unchecked")
    private void setupListQuery(java.util.List<Object[]> rows) {
        Query<Object[]> listQuery = mock(Query.class);
        when(session.createQuery(any(String.class), eq(Object[].class)))
                .thenReturn(listQuery);
        when(listQuery.setParameter(anyString(), anyString())).thenReturn(listQuery);
        when(listQuery.list()).thenReturn(rows);
    }

    private void runGetFavoritesTest(String pathInfo, java.util.List<Object[]> rows,
                                     ThrowingRunnable assertions) throws Exception {
        runWithMockedContext(() -> {
            User mockUser = mock(User.class);
            lenient().when(mockUser.getId()).thenReturn(USER_ID);
            lenient().when(obContext.getUser()).thenReturn(mockUser);
            Role mockRole = mock(Role.class);
            lenient().when(mockRole.getId()).thenReturn(ROLE_ID);
            lenient().when(obContext.getRole()).thenReturn(mockRole);

            when(request.getPathInfo()).thenReturn(pathInfo);
            when(request.getMethod()).thenReturn("GET");
            setupListQuery(rows);

            new FavoritesService(request, response).process();
            assertions.run();
        });
    }

    @Test
    void listFavoritesReturnsItems() throws Exception {
        runGetFavoritesTest(FAVORITES_PATH, Arrays.asList(
                new Object[]{"Sales Order", "W", MENU_ID, "win-001"},
                new Object[]{"Purchase Invoice", "W", "menu-002", "win-002"}
        ), () -> {
            String output = responseCapture.toString();
            assertTrue(output.contains(ITEMS_KEY));
            assertTrue(output.contains("Sales Order"));
            assertTrue(output.contains("Purchase Invoice"));
            assertTrue(output.contains("win-001"));
        });
    }

    @Test
    void listFavoritesReturnsEmptyItems() throws Exception {
        runGetFavoritesTest(FAVORITES_PATH, Collections.emptyList(), () -> {
            String output = responseCapture.toString();
            assertTrue(output.contains(ITEMS_KEY));
            assertFalse(output.contains("label"));
        });
    }

    @Test
    void listFavoritesHandlesNullWindowId() throws Exception {
        runGetFavoritesTest(FAVORITES_PATH, Collections.singletonList(
                new Object[]{"Report", "R", "menu-003", null}
        ), () -> {
            String output = responseCapture.toString();
            assertTrue(output.contains("Report"));
            assertTrue(output.contains("null"));
        });
    }

    @Test
    void processNormalizesMetaModulePath() throws Exception {
        runGetFavoritesTest("/com.etendoerp.metadata.meta" + FAVORITES_PATH,
                Collections.emptyList(),
                () -> assertTrue(responseCapture.toString().contains(ITEMS_KEY)));
    }

    @Test
    void processNormalizesSWSModulePath() throws Exception {
        runGetFavoritesTest("/com.etendoerp.metadata.sws" + FAVORITES_PATH,
                Collections.emptyList(),
                () -> assertTrue(responseCapture.toString().contains(ITEMS_KEY)));
    }

    @Test
    void processThrowsNotFoundForGetOnTogglePath() {
        assertNotFoundForMethodAndPath("GET", FAVORITES_TOGGLE_PATH);
    }

    @Test
    void processThrowsNotFoundForPatchMethod() {
        assertNotFoundForMethodAndPath("PATCH", FAVORITES_PATH);
    }
}
