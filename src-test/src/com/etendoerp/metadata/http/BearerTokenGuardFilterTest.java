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

package com.etendoerp.metadata.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.FilterChain;
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
import org.openbravo.base.exception.OBException;

import com.etendoerp.metadata.auth.TokenRevocationStore;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.utils.Constants;

/**
 * Tests for {@link BearerTokenGuardFilter}: a Classic servlet request that carries an unusable
 * Bearer token is answered with a JSON 401, while requests without a Bearer token (Classic itself)
 * and requests with a valid one go on untouched.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BearerTokenGuardFilterTest {

  private static final String TOKEN = "header.payload.signature";

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

  private MockedStatic<Utils> authUtilsStatic;
  private MockedStatic<TokenRevocationStore> revocationStatic;
  private BearerTokenGuardFilter filter;
  private StringWriter body;

  /**
   * Builds the filter and a response whose writer captures the body, with a valid, non-revoked
   * token by default.
   *
   * @throws Exception if the response writer cannot be stubbed
   */
  @BeforeEach
  void setUp() throws Exception {
    filter = new BearerTokenGuardFilter();
    body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    when(request.getRequestURI()).thenReturn("/etendo/attachments");

    authUtilsStatic = mockStatic(Utils.class);
    revocationStatic = mockStatic(TokenRevocationStore.class);
    authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(TOKEN);
    revocationStatic.when(() -> TokenRevocationStore.isRevoked(any())).thenReturn(false);
  }

  /** Releases the static mocks. */
  @AfterEach
  void tearDown() {
    authUtilsStatic.close();
    revocationStatic.close();
  }

  /**
   * Asserts that the request was answered with a JSON 401 carrying the given error and never
   * reached the servlet.
   *
   * @param error the error expected in the response body
   * @throws Exception if the chain verification fails
   */
  private void assertRejectedWith(String error) throws Exception {
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(body.toString().contains(error), "Response body must carry " + error);
    verify(chain, never()).doFilter(any(), any());
  }

  /**
   * Asserts that the request reached the servlet without any status being written.
   *
   * @throws Exception if the chain verification fails
   */
  private void assertPassedThrough() throws Exception {
    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  /**
   * A request without a Bearer token, as Classic sends it, goes on untouched and the token is
   * never decoded.
   *
   * @throws Exception if the filter fails
   */
  @Test
  void requestWithoutBearerTokenPassesThrough() throws Exception {
    authUtilsStatic.when(() -> Utils.extractBearerToken(request)).thenReturn(null);

    filter.doFilter(request, response, chain);

    assertPassedThrough();
    authUtilsStatic.verify(() -> Utils.decodeToken(any()), never());
  }

  /**
   * A valid and non-revoked Bearer token goes on untouched.
   *
   * @throws Exception if the filter fails
   */
  @Test
  void validTokenPassesThrough() throws Exception {
    filter.doFilter(request, response, chain);

    assertPassedThrough();
  }

  /**
   * A token the decoder rejects (malformed, badly signed or expired) is answered with a 401.
   *
   * @throws Exception if the filter fails
   */
  @Test
  void invalidTokenIsRejected() throws Exception {
    authUtilsStatic.when(() -> Utils.decodeToken(TOKEN)).thenThrow(new OBException("expired"));

    filter.doFilter(request, response, chain);

    assertRejectedWith(Constants.INVALID_TOKEN_ERROR);
  }

  /**
   * A token revoked through the logout is answered with a 401.
   *
   * @throws Exception if the filter fails
   */
  @Test
  void revokedTokenIsRejected() throws Exception {
    revocationStatic.when(() -> TokenRevocationStore.isRevoked(TOKEN)).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertRejectedWith(Constants.TOKEN_REVOKED_ERROR);
  }

  /**
   * An invalid token is reported as invalid without querying the revocation store.
   */
  @Test
  void invalidTokenIsNotLookedUpInTheRevocationStore() {
    authUtilsStatic.when(() -> Utils.decodeToken(TOKEN)).thenThrow(new OBException("bad signature"));

    assertEquals(Constants.INVALID_TOKEN_ERROR, BearerTokenGuardFilter.resolveRejection(TOKEN));
    revocationStatic.verify(() -> TokenRevocationStore.isRevoked(any()), never());
  }

  /**
   * A missing token is never a reason to reject the request.
   */
  @Test
  void missingTokenHasNoRejection() {
    assertNull(BearerTokenGuardFilter.resolveRejection(null));
  }

  /**
   * The lifecycle methods have nothing to do and never fail.
   */
  @Test
  void lifecycleMethodsDoNothing() {
    assertDoesNotThrow(() -> {
      filter.init(null);
      filter.destroy();
    });
  }
}
