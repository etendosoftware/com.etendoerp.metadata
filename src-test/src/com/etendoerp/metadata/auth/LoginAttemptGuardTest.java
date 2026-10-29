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

package com.etendoerp.metadata.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.secureApp.LoginUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Tests for {@link LoginAttemptGuard}, which checks New UI credentials with the Classic
 * failed-attempt delay and user lock ({@link LoginUtils#getValidUserId}).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginAttemptGuardTest {
    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password"; // NOSONAR - fake value, only used to stub mocked LoginUtils calls
    private static final String USER_ID = "user-1";
    private static final String INVALID_CREDENTIALS_TEXT = "Invalid user name or password.";
    private static final String LOCKED_USER_TEXT = "user is locked. Ask System Administrator to unlock it.";

    @Mock
    private HttpServletRequest request;

    private MockedStatic<LoginUtils> loginUtilsMock;
    private MockedStatic<LoginSessionRegistry> registryMock;
    private MockedStatic<Utility> utilityMock;
    private MockedStatic<OBContext> obContextMock;
    private MockedConstruction<DalConnectionProvider> connectionConstruction;

    /**
     * Opens the static and construction mocks around the Classic login utilities.
     */
    @Before
    public void setUp() {
        loginUtilsMock = mockStatic(LoginUtils.class);
        registryMock = mockStatic(LoginSessionRegistry.class);
        utilityMock = mockStatic(Utility.class);
        obContextMock = mockStatic(OBContext.class);
        connectionConstruction = mockConstruction(DalConnectionProvider.class);
    }

    /**
     * Releases the mocks opened in {@link #setUp()}.
     */
    @After
    public void tearDown() {
        connectionConstruction.close();
        obContextMock.close();
        utilityMock.close();
        registryMock.close();
        loginUtilsMock.close();
    }

    /**
     * Valid credentials of an unlocked user return the user id and record nothing: the successful
     * session is recorded once the token exists.
     */
    @Test
    public void testAuthenticateReturnsUserIdOfValidCredentials() {
        loginUtilsMock.when(() -> LoginUtils.getValidUserId(any(ConnectionProvider.class), eq(USERNAME), eq(PASSWORD)))
            .thenReturn(USER_ID);

        assertEquals(USER_ID, LoginAttemptGuard.authenticate(request, USERNAME, PASSWORD));
        registryMock.verify(() -> LoginSessionRegistry.register(any(), any(), any(), any()), never());
    }

    /**
     * A wrong password is recorded as a failed attempt of the username and refused with the
     * Classic identification failure message, in the default language when there is no context.
     */
    @Test
    public void testAuthenticateWrongPasswordRecordsFailureAndThrows() {
        stubRefusedAttempt(null);
        stubMessage(LoginAttemptGuard.INVALID_CREDENTIALS_MESSAGE, LoginAttemptGuard.DEFAULT_LANGUAGE,
            INVALID_CREDENTIALS_TEXT);

        assertUnauthorized(INVALID_CREDENTIALS_TEXT);
        registryMock.verify(() -> LoginSessionRegistry.register(request, USERNAME, null,
            LoginSessionRegistry.FAILED_STATUS));
    }

    /**
     * A locked user is refused with the Classic locked user message even when the password is
     * right, in the language of the current context.
     */
    @Test
    public void testAuthenticateLockedUserRecordsFailureAndThrowsLockedMessage() {
        stubRefusedAttempt(USER_ID);
        OBContext context = mock(OBContext.class);
        Language language = mock(Language.class);
        when(language.getLanguage()).thenReturn("es_ES");
        when(context.getLanguage()).thenReturn(language);
        obContextMock.when(OBContext::getOBContext).thenReturn(context);
        stubMessage(LoginAttemptGuard.LOCKED_USER_MESSAGE, "es_ES", LOCKED_USER_TEXT);

        assertUnauthorized(LOCKED_USER_TEXT);
        registryMock.verify(() -> LoginSessionRegistry.register(request, USERNAME, null,
            LoginSessionRegistry.FAILED_STATUS));
    }

    /**
     * Stubs an attempt refused by {@link LoginUtils#getValidUserId}, whose plain password check
     * answers the given user id.
     *
     * @param passwordOwnerId the user id the password alone matches, or {@code null} when it is wrong
     */
    private void stubRefusedAttempt(String passwordOwnerId) {
        loginUtilsMock.when(() -> LoginUtils.getValidUserId(any(ConnectionProvider.class), eq(USERNAME), eq(PASSWORD)))
            .thenReturn(null);
        loginUtilsMock.when(() -> LoginUtils.checkUserPassword(any(ConnectionProvider.class), eq(USERNAME), eq(PASSWORD)))
            .thenReturn(passwordOwnerId);
    }

    /**
     * Stubs the translation of a Classic message.
     *
     * @param key      the message key
     * @param language the language it is requested in
     * @param text     the translated text
     */
    private void stubMessage(String key, String language, String text) {
        utilityMock.when(() -> Utility.messageBD(any(ConnectionProvider.class), eq(key), eq(language)))
            .thenReturn(text);
    }

    /**
     * Asserts that authenticating {@link #USERNAME} fails with the given message.
     *
     * @param expectedMessage the expected message of the {@link UnauthorizedException}
     */
    private void assertUnauthorized(String expectedMessage) {
        try {
            LoginAttemptGuard.authenticate(request, USERNAME, PASSWORD);
            fail("Expected UnauthorizedException");
        } catch (UnauthorizedException expected) {
            assertEquals(expectedMessage, expected.getMessage());
        }
    }
}
