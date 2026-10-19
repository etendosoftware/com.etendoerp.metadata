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

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.exceptions.MethodNotAllowedException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogoutServiceTest {

    private static final String RAW_TOKEN = "header.payload.signature";

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private MockedStatic<Utils> authUtilsStatic;
    private MockedStatic<TokenRevocationStore> revocationStoreStatic;

    @BeforeEach
    void setUp() {
        authUtilsStatic = mockStatic(Utils.class);
        revocationStoreStatic = mockStatic(TokenRevocationStore.class);
    }

    @AfterEach
    void tearDown() {
        authUtilsStatic.close();
        revocationStoreStatic.close();
    }

    @Test
    void nonPostIsRejected() {
        when(request.getMethod()).thenReturn("GET");

        LogoutService service = new LogoutService(request, response);
        assertThrows(MethodNotAllowedException.class, service::process);
    }

    @Test
    void missingTokenIsRejected() {
        when(request.getMethod()).thenReturn("POST");
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(null);

        LogoutService service = new LogoutService(request, response);
        assertThrows(UnauthorizedException.class, service::process);

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()), never());
    }

    @Test
    void validTokenRevokesRawTokenAndReturns200() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        DecodedJWT decoded = mock(DecodedJWT.class);
        Date expiresAt = new Date();
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);
        authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(RAW_TOKEN);

        LogoutService service = new LogoutService(request, response);
        service.process();

        revocationStoreStatic.verify(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt));
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
        when(request.getMethod()).thenReturn("POST");
        DecodedJWT decoded = mock(DecodedJWT.class);
        Date expiresAt = new Date();
        when(decoded.getExpiresAt()).thenReturn(expiresAt);
        authUtilsStatic.when(() -> Utils.decodeBearerToken(request)).thenReturn(decoded);
        authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(RAW_TOKEN);
        revocationStoreStatic.when(() -> TokenRevocationStore.revoke(RAW_TOKEN, expiresAt))
                .thenThrow(new RuntimeException("DB unavailable"));

        LogoutService service = new LogoutService(request, response);
        assertThrows(RuntimeException.class, service::process);

        verify(response, never()).setStatus(HttpServletResponse.SC_OK);
    }
}
