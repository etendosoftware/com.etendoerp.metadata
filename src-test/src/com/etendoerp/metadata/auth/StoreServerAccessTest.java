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
package com.etendoerp.metadata.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import javax.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.base.secureApp.LoginUtils.RoleDefaults;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.CachedPreference;
import org.openbravo.server.ServerControllerHandler;

import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Tests {@link StoreServerAccess}: classic RestrictErpAccessInStoreServer, which leaves only System
 * Administrator on a restricted store server.
 */
class StoreServerAccessTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_ROLE_ID = "role-1";
    /** Value of RestrictErpAccessInStoreServer that restricts the access. */
    private static final String YES = "Y";
    /** Role the login asks for: none, the default role of the user decides. */
    private static final String NO_REQUESTED_ROLE = "";
    private static final String RESTRICTED_TEXT = "Only System Administrator can access the ERP";

    private MockedStatic<WeldUtils> weld;
    private MockedStatic<LoginAttemptGuard> guard;
    private MockedStatic<LoginUtils> loginUtils;
    private ServerControllerHandler server;
    private CachedPreference preferences;

    /**
     * Mocks the CDI beans classic reads and the message of the refusal.
     */
    @BeforeEach
    void setUp() {
        weld = mockStatic(WeldUtils.class);
        guard = mockStatic(LoginAttemptGuard.class);
        loginUtils = mockStatic(LoginUtils.class);
        server = mock(ServerControllerHandler.class);
        preferences = mock(CachedPreference.class);
        weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(ServerControllerHandler.class)).thenReturn(server);
        weld.when(() -> WeldUtils.getInstanceFromStaticBeanManager(CachedPreference.class)).thenReturn(preferences);
        guard.when(() -> LoginAttemptGuard.readMessage(any(), eq(StoreServerAccess.RESTRICTED_MESSAGE)))
                .thenReturn(RESTRICTED_TEXT);
    }

    /**
     * Closes the static mocks.
     */
    @AfterEach
    void tearDown() {
        weld.close();
        guard.close();
        loginUtils.close();
    }

    /**
     * Configures the server and the preference.
     *
     * @param storeServer whether the server is a store server
     * @param restrict the value of RestrictErpAccessInStoreServer
     */
    private void givenServer(boolean storeServer, String restrict) {
        when(server.isThisAStoreServer()).thenReturn(storeServer);
        when(preferences.getPreferenceValue(CachedPreference.RESTRICT_ERP_ACCESS_IN_STORE_SERVER)).thenReturn(restrict);
    }

    /**
     * Only a store server with the preference set to Y is restricted.
     */
    @Test
    void isErpAccessRestrictedOnlyOnARestrictedStoreServer() {
        givenServer(true, YES);
        assertTrue(StoreServerAccess.isErpAccessRestricted());

        givenServer(true, "N");
        assertFalse(StoreServerAccess.isErpAccessRestricted());

        givenServer(false, YES);
        assertFalse(StoreServerAccess.isErpAccessRestricted());
    }

    /**
     * On a restricted store server only System Administrator is accepted.
     */
    @Test
    void checkRoleAcceptsOnlySystemAdministratorWhenRestricted() {
        givenServer(true, YES);

        assertDoesNotThrow(() -> StoreServerAccess.checkRole(StoreServerAccess.SYSTEM_ADMIN_ROLE_ID));
        UnauthorizedException refusal = assertThrows(UnauthorizedException.class,
                () -> StoreServerAccess.checkRole(OTHER_ROLE_ID));
        assertEquals(RESTRICTED_TEXT, refusal.getMessage());
    }

    /**
     * Without the restriction any role is accepted.
     */
    @Test
    void checkRoleAcceptsAnyRoleWithoutTheRestriction() {
        givenServer(false, YES);

        assertDoesNotThrow(() -> StoreServerAccess.checkRole(OTHER_ROLE_ID));
    }

    /**
     * The login is checked with the default role of the user.
     */
    @Test
    void checkLoginUsesTheDefaultRoleOfTheUser() {
        givenServer(true, YES);
        RoleDefaults defaults = new RoleDefaults();
        defaults.role = OTHER_ROLE_ID;
        loginUtils.when(() -> LoginUtils.getLoginDefaults(eq(USER_ID), eq(NO_REQUESTED_ROLE), any())).thenReturn(defaults);

        assertThrows(UnauthorizedException.class, () -> StoreServerAccess.checkLogin(USER_ID));
    }

    /**
     * On a restricted store server a user whose default role is System Administrator logs in.
     */
    @Test
    void checkLoginAcceptsADefaultRoleOfSystemAdministrator() {
        givenServer(true, YES);
        RoleDefaults defaults = new RoleDefaults();
        defaults.role = StoreServerAccess.SYSTEM_ADMIN_ROLE_ID;
        loginUtils.when(() -> LoginUtils.getLoginDefaults(eq(USER_ID), eq(NO_REQUESTED_ROLE), any())).thenReturn(defaults);

        assertDoesNotThrow(() -> StoreServerAccess.checkLogin(USER_ID));
    }

    /**
     * When the login defaults of the user cannot be read, no role is allowed on a restricted store server.
     */
    @Test
    void checkLoginRefusesAUserWithoutReadableDefaults() {
        givenServer(true, YES);
        loginUtils.when(() -> LoginUtils.getLoginDefaults(eq(USER_ID), eq(NO_REQUESTED_ROLE), any()))
                .thenThrow(new ServletException("no defaults"));

        assertThrows(UnauthorizedException.class, () -> StoreServerAccess.checkLogin(USER_ID));
    }

    /**
     * Without the restriction the login defaults are not even read.
     */
    @Test
    void checkLoginDoesNothingWithoutTheRestriction() {
        givenServer(false, YES);

        assertDoesNotThrow(() -> StoreServerAccess.checkLogin(USER_ID));
        loginUtils.verifyNoInteractions();
    }
}
