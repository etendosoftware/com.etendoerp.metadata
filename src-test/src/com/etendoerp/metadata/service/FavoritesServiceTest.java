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
import com.etendoerp.metadata.exceptions.NotFoundException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.ad.ui.Menu;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoritesServiceTest {

    private static final String USER_ID = "user-001";
    private static final String ROLE_ID = "role-001";
    private static final String MENU_ID = "menu-001";
    private static final String FAVORITES_TOGGLE_PATH = "/favorites/toggle";
    private static final String MENU_ID_JSON_PREFIX = "{\"menuId\":\"";

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock OBDal obDal;
    @Mock OBContext obContext;
    @Mock Session session;
    @Mock OBProvider obProvider;

    private MockedStatic<OBDal> dalStatic;
    private MockedStatic<OBContext> ctxStatic;
    private MockedStatic<OBProvider> providerStatic;
    private StringWriter outputWriter;

    @BeforeEach
    void setUp() throws Exception {
        dalStatic = mockStatic(OBDal.class);
        ctxStatic = mockStatic(OBContext.class);
        providerStatic = mockStatic(OBProvider.class);

        dalStatic.when(OBDal::getInstance).thenReturn(obDal);
        ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
        ctxStatic.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(inv -> null);
        ctxStatic.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
        providerStatic.when(OBProvider::getInstance).thenReturn(obProvider);

        when(obDal.getSession()).thenReturn(session);

        User mockUser = mock(User.class);
        lenient().when(mockUser.getId()).thenReturn(USER_ID);
        lenient().when(obContext.getUser()).thenReturn(mockUser);

        Role mockRole = mock(Role.class);
        lenient().when(mockRole.getId()).thenReturn(ROLE_ID);
        lenient().when(obContext.getRole()).thenReturn(mockRole);

        lenient().when(obContext.getCurrentClient()).thenReturn(mock(Client.class));
        lenient().when(obContext.getCurrentOrganization()).thenReturn(mock(Organization.class));

        outputWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(outputWriter));
    }

    @AfterEach
    void tearDown() {
        if (dalStatic != null) dalStatic.close();
        if (ctxStatic != null) ctxStatic.close();
        if (providerStatic != null) providerStatic.close();
    }

    private void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    @SuppressWarnings("unchecked")
    private void setupExistsQuery(long count) {
        Query<Long> existsQuery = mock(Query.class);
        when(session.createQuery(
                any(String.class), eq(Long.class)))
                .thenReturn(existsQuery);
        when(existsQuery.setParameter(anyString(), anyString())).thenReturn(existsQuery);
        when(existsQuery.uniqueResult()).thenReturn(count);
    }

    @SuppressWarnings("unchecked")
    private Query<Object> setupDeleteQuery() {
        Query<Object> deleteQuery = mock(Query.class);
        when(session.createQuery(any(String.class))).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), anyString())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);
        return deleteQuery;
    }

    @Test
    void processThrowsNotFoundForNullPath() {
        when(request.getPathInfo()).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");

        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processThrowsNotFoundForWrongPath() {
        when(request.getPathInfo()).thenReturn("/wrong/path");
        when(request.getMethod()).thenReturn("POST");

        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processThrowsNotFoundForGetMethod() {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("GET");

        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(NotFoundException.class, svc::process);
    }

    @Test
    void processRemovesExistingFavorite() throws Exception {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("POST");
        setRequestBody(MENU_ID_JSON_PREFIX + MENU_ID + "\"}");

        setupExistsQuery(1L);
        setupDeleteQuery();

        FavoritesService svc = new FavoritesService(request, response);
        svc.process();

        String output = outputWriter.toString();
        assertTrue(output.contains("removed"));
        assertTrue(output.contains(MENU_ID));
        verify(obDal).flush();
    }

    @Test
    void processAddsNewFavorite() throws Exception {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("POST");
        setRequestBody(MENU_ID_JSON_PREFIX + MENU_ID + "\"}");

        setupExistsQuery(0L);

        Menu mockMenu = mock(Menu.class);
        when(obDal.get(Menu.class, MENU_ID)).thenReturn(mockMenu);

        @SuppressWarnings("unchecked")
        Query<Long> seqQuery = mock(Query.class);
        when(session.createQuery(
                any(String.class), eq(Long.class)))
                .thenReturn(seqQuery);
        when(seqQuery.setParameter(anyString(), anyString())).thenReturn(seqQuery);
        // First call returns 0 (exists check), second call returns 20 (max seqno)
        when(seqQuery.uniqueResult()).thenReturn(0L).thenReturn(20L);

        UserFavorite mockFav = mock(UserFavorite.class);
        when(obProvider.get(UserFavorite.class)).thenReturn(mockFav);

        when(obDal.get(User.class, USER_ID)).thenReturn(mock(User.class));
        when(obDal.get(Role.class, ROLE_ID)).thenReturn(mock(Role.class));

        FavoritesService svc = new FavoritesService(request, response);
        svc.process();

        String output = outputWriter.toString();
        assertTrue(output.contains("added"));
        assertTrue(output.contains(MENU_ID));
        verify(mockFav).setMenu(mockMenu);
        verify(mockFav).setSequenceNo(30L);
        verify(session).save(mockFav);
    }

    @Test
    void processThrowsNotFoundWhenMenuDoesNotExist() throws Exception {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("POST");
        setRequestBody(MENU_ID_JSON_PREFIX + MENU_ID + "\"}");

        setupExistsQuery(0L);
        when(obDal.get(Menu.class, MENU_ID)).thenReturn(null);

        // seqno query won't be reached but the exists query returns Long
        @SuppressWarnings("unchecked")
        Query<Long> existsQuery = mock(Query.class);
        when(session.createQuery(any(String.class), eq(Long.class)))
                .thenReturn(existsQuery);
        when(existsQuery.setParameter(anyString(), anyString())).thenReturn(existsQuery);
        when(existsQuery.uniqueResult()).thenReturn(0L);

        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(InternalServerException.class, svc::process);
    }

    @Test
    void processThrowsInternalServerOnInvalidJson() throws Exception {
        when(request.getPathInfo()).thenReturn(FAVORITES_TOGGLE_PATH);
        when(request.getMethod()).thenReturn("POST");
        setRequestBody("not-valid-json");

        FavoritesService svc = new FavoritesService(request, response);
        assertThrows(InternalServerException.class, svc::process);
    }
}
