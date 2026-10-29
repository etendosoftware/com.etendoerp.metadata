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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.HttpBaseUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.security.SessionLogin;
import org.openbravo.model.ad.access.Session;
import org.openbravo.model.ad.access.User;

/**
 * Tests for {@link LoginSessionRegistry}, which records New UI login attempts in
 * {@code AD_Session} and closes them on logout.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginSessionRegistryTest {
    private static final String USERNAME = "test-user";
    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";
    private static final String SERVER_URL = "http://localhost:8080";
    private static final String SYSTEM_ID = "0";

    @Mock
    private HttpServletRequest request;

    @Mock
    private OBDal obDal;

    private MockedStatic<OBContext> obContextMock;
    private MockedStatic<OBDal> obDalMock;
    private MockedStatic<HttpBaseUtils> httpBaseUtilsMock;
    private List<?> sessionLoginArguments;

    /**
     * Opens the {@link OBContext}, {@link OBDal} and {@link HttpBaseUtils} static mocks.
     */
    @Before
    public void setUp() {
        obContextMock = mockStatic(OBContext.class);
        obDalMock = mockStatic(OBDal.class);
        httpBaseUtilsMock = mockStatic(HttpBaseUtils.class);
        obDalMock.when(OBDal::getInstance).thenReturn(obDal);
        httpBaseUtilsMock.when(() -> HttpBaseUtils.getLocalAddress(request)).thenReturn(SERVER_URL);
    }

    /**
     * Releases the static mocks opened in {@link #setUp()}.
     */
    @After
    public void tearDown() {
        httpBaseUtilsMock.close();
        obDalMock.close();
        obContextMock.close();
    }

    /**
     * A successful attempt is saved with the success status, the username, the user and the local
     * server address, and its id is returned.
     *
     * @throws ServletException if the mocked session cannot be saved
     */
    @Test
    public void testRegisterSavesSuccessfulAttempt() throws ServletException {
        try (MockedConstruction<SessionLogin> construction = mockSessionLogin()) {
            String sessionId = LoginSessionRegistry.register(request, USERNAME, USER_ID,
                LoginSessionRegistry.SUCCESS_STATUS);

            assertEquals(SESSION_ID, sessionId);
            SessionLogin sessionLogin = construction.constructed().get(0);
            verify(sessionLogin).setStatus(LoginSessionRegistry.SUCCESS_STATUS);
            verify(sessionLogin).setUserName(USERNAME);
            verify(sessionLogin).setServerUrl(SERVER_URL);
            verify(sessionLogin).save();
            assertEquals(Arrays.asList(request, SYSTEM_ID, SYSTEM_ID, USER_ID), constructorArguments(construction));
        }
    }

    /**
     * An attempt without a user id is saved as failed, on behalf of the system user, with the
     * username it was made with - that is what {@code UserLock} counts.
     *
     * @throws ServletException if the mocked session cannot be saved
     */
    @Test
    public void testRegisterSavesFailedAttemptWithoutUser() throws ServletException {
        try (MockedConstruction<SessionLogin> construction = mockSessionLogin()) {
            LoginSessionRegistry.register(request, USERNAME, null, LoginSessionRegistry.SUCCESS_STATUS);

            SessionLogin sessionLogin = construction.constructed().get(0);
            verify(sessionLogin).setStatus(LoginSessionRegistry.FAILED_STATUS);
            verify(sessionLogin).setUserName(USERNAME);
            assertEquals(Arrays.asList(request, SYSTEM_ID, SYSTEM_ID, SYSTEM_ID), constructorArguments(construction));
        }
    }

    /**
     * Without user id nor username there is nothing to record.
     */
    @Test
    public void testRegisterWithoutUserNorUsernameRecordsNothing() {
        try (MockedConstruction<SessionLogin> construction = mockSessionLogin()) {
            assertNull(LoginSessionRegistry.register(request, "", null, LoginSessionRegistry.SUCCESS_STATUS));
            assertEquals(0, construction.constructed().size());
        }
    }

    /**
     * Without a request nothing is recorded and the failure does not propagate.
     */
    @Test
    public void testRegisterWithoutRequestReturnsNull() {
        try (MockedConstruction<SessionLogin> construction = mockSessionLogin()) {
            assertNull(LoginSessionRegistry.register(null, USERNAME, USER_ID, LoginSessionRegistry.SUCCESS_STATUS));
            assertEquals(0, construction.constructed().size());
        }
    }

    /**
     * A failure while saving never blocks the login: {@code null} is returned.
     */
    @Test
    public void testRegisterSaveFailureReturnsNull() {
        try (MockedConstruction<SessionLogin> construction = mockConstruction(SessionLogin.class,
            (mock, context) -> doThrow(new ServletException("DB unavailable")).when(mock).save())) {
            assertNull(LoginSessionRegistry.register(request, USERNAME, USER_ID, LoginSessionRegistry.SUCCESS_STATUS));
        }
    }

    /**
     * A session of the closing user is marked as no longer active.
     */
    @Test
    public void testCloseDeactivatesOwnSession() {
        Session session = stubSessionCreatedBy(USER_ID);

        LoginSessionRegistry.close(SESSION_ID, USER_ID);

        verify(session).setSessionActive(false);
        verify(obDal).flush();
    }

    /**
     * A session of another user is left untouched.
     */
    @Test
    public void testCloseIgnoresSessionOfAnotherUser() {
        Session session = stubSessionCreatedBy("other-user");

        LoginSessionRegistry.close(SESSION_ID, USER_ID);

        verify(session, never()).setSessionActive(false);
        verify(obDal, never()).flush();
    }

    /**
     * An unknown session id is ignored.
     */
    @Test
    public void testCloseIgnoresUnknownSession() {
        when(obDal.get(Session.class, SESSION_ID)).thenReturn(null);

        LoginSessionRegistry.close(SESSION_ID, USER_ID);

        verify(obDal, never()).flush();
    }

    /**
     * A blank session id does not reach the database.
     */
    @Test
    public void testCloseWithBlankSessionIdDoesNothing() {
        LoginSessionRegistry.close(" ", USER_ID);

        verify(obDal, never()).get(any(Class.class), any());
    }

    /**
     * A database failure is swallowed: logout must never fail because of the registry.
     */
    @Test
    public void testCloseSwallowsDatabaseFailure() {
        Session session = stubSessionCreatedBy(USER_ID);
        doThrow(new IllegalStateException("DB unavailable")).when(obDal).flush();

        LoginSessionRegistry.close(SESSION_ID, USER_ID);

        verify(session).setSessionActive(false);
    }

    /**
     * Mocks every {@link SessionLogin} built by the registry so that it reports
     * {@link #SESSION_ID} as its id, keeping the arguments it was built with.
     *
     * @return the construction mock, to be closed by the caller
     */
    private MockedConstruction<SessionLogin> mockSessionLogin() {
        return mockConstruction(SessionLogin.class, (mock, context) -> {
            sessionLoginArguments = context.arguments();
            when(mock.getSessionID()).thenReturn(SESSION_ID);
        });
    }

    /**
     * Returns the constructor arguments of the only {@link SessionLogin} built.
     *
     * @param construction the construction mock
     * @return the request, client, organization and user ids it was built with
     */
    private List<?> constructorArguments(MockedConstruction<SessionLogin> construction) {
        assertEquals(1, construction.constructed().size());
        return sessionLoginArguments;
    }

    /**
     * Stubs {@link #SESSION_ID} as a session created by the given user.
     *
     * @param creatorId the id of the user that created the session
     * @return the stubbed session
     */
    private Session stubSessionCreatedBy(String creatorId) {
        Session session = mock(Session.class);
        User creator = mock(User.class);
        when(creator.getId()).thenReturn(creatorId);
        when(session.getCreatedBy()).thenReturn(creator);
        when(obDal.get(Session.class, SESSION_ID)).thenReturn(session);
        return session;
    }
}
