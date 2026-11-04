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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.LoginSessionRegistry;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceTest {

    private static final String RAW_TOKEN = "header.payload.signature";
    private static final String POST_METHOD = "POST";
    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private MockedStatic<Utils> authUtilsStatic;
    private MockedStatic<TokenRevocationStore> revocationStoreStatic;
    private MockedStatic<LoginSessionRegistry> registryStatic;

    @BeforeEach
    void setUp() {
        authUtilsStatic = mockStatic(Utils.class);
        revocationStoreStatic = mockStatic(TokenRevocationStore.class);
        registryStatic = mockStatic(LoginSessionRegistry.class);
    }

    @AfterEach
    void tearDown() {
        registryStatic.close();
        authUtilsStatic.close();
        revocationStoreStatic.close();
    }

    /**
     * Stubs a POST logout request carrying a valid Bearer token of {@link #USER_ID}.
     *
     * @return the expiration date of the stubbed token
     */
    private Date stubValidTokenRequest() {
        when(request.getMethod()).thenReturn(POST_METHOD);
        DecodedJWT decoded = mock(DecodedJWT.class);
        Claim userClaim = mock(Claim.class);
        Date expiresAt = new Date();
        when(userClaim.asString()).thenReturn(USER_ID);
        when(decoded.getClaim("user")).thenReturn(userClaim);
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);
        authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(RAW_TOKEN);
        return expiresAt;
    }

    /**
     * Stubs the raw JSON body of the logout request.
     *
     * @param body the raw JSON body
     * @throws Exception if stubbing the mocked reader fails
     */
    private void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    @Test
    void nonPostIsRejected() {
        when(request.getMethod()).thenReturn("GET");

        LogoutService service = new LogoutService(request, response);
        assertThrows(MethodNotAllowedException.class, service::process);
    }

    @Test
    void missingTokenIsRejected() {
        when(request.getMethod()).thenReturn(POST_METHOD);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(null);

        LogoutService service = new LogoutService(request, response);
        assertThrows(UnauthorizedException.class, service::process);

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()), never());
    }

    @Test
    void validTokenRevokesRawTokenAndReturns200() throws Exception {
        Date expiresAt = stubValidTokenRequest();

        LogoutService service = new LogoutService(request, response);
        service.process();

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt));
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * A body naming the login session closes it on behalf of the token's user, next to the token
     * revocation.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    void sessionIdInBodyClosesTheLoginSessionOfTheTokenUser() throws Exception {
        stubValidTokenRequest();
        setRequestBody("{\"sessionId\":\"" + SESSION_ID + "\"}");

        new LogoutService(request, response).process();

        registryStatic.verify(() -> LoginSessionRegistry.close(SESSION_ID, USER_ID));
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Without a body (a token from a login that recorded no session) no session is closed and the
     * logout still succeeds.
     *
     * @throws Exception if the request body cannot be read or the service fails
     */
    @Test
    void missingSessionIdClosesNoSession() throws Exception {
        stubValidTokenRequest();
        setRequestBody("");

        new LogoutService(request, response).process();

        registryStatic.verify(() -> LoginSessionRegistry.close(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()), never());
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * A DB failure inside {@code TokenRevocationStore.revoke} must propagate out of
     * {@code process()} uncaught, not be swallowed - matches the design spec's "client state
     * cleared even if revocation fails" scenario: the failure is real and visible (a 500), not
     * silently absorbed into a false 200.
     */
    @Test
    void revocationFailurePropagatesUncaught() {
        Date expiresAt = stubValidTokenRequest();
        revocationStoreStatic.when(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt))
                .thenThrow(new RuntimeException("DB unavailable"));

        LogoutService service = new LogoutService(request, response);
        assertThrows(RuntimeException.class, service::process);

        verify(response, never()).setStatus(HttpServletResponse.SC_OK);
    }
}
