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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.User;

import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.PasswordExpirationUtils;

/**
 * Test class for the expired-password guard applied by {@link BaseWebService} to every HTTP verb.
 * Ensures the metadata data plane is closed while the password is expired, while keeping the
 * bootstrap endpoints the mandatory password-change screen depends on reachable.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BaseWebServiceGuardTest {

  private static final String BLOCKED_PATH = "/window/123";

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private OBContext obContext;

  @Mock
  private User user;

  private MockedStatic<OBContext> obContextStatic;
  private MockedStatic<PasswordExpirationUtils> expirationStatic;
  private TestWebService service;

  /**
   * Minimal {@link BaseWebService} implementation that only records whether the guard let the
   * request reach the actual processing.
   */
  private static class TestWebService extends BaseWebService {
    private boolean processed;

    @Override
    protected void process(HttpServletRequest request, HttpServletResponse response) {
      processed = true;
    }

    boolean wasProcessed() {
      return processed;
    }
  }

  @BeforeEach
  void setUp() {
    service = new TestWebService();
    obContextStatic = mockStatic(OBContext.class);
    expirationStatic = mockStatic(PasswordExpirationUtils.class);

    obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getUser()).thenReturn(user);
  }

  @AfterEach
  void tearDown() {
    obContextStatic.close();
    expirationStatic.close();
  }

  /**
   * Configures the request path and whether the current user's password is expired.
   *
   * @param pathInfo the path info reported by the request
   * @param expired  whether the password must be considered expired
   */
  private void given(String pathInfo, boolean expired) {
    when(request.getPathInfo()).thenReturn(pathInfo);
    expirationStatic.when(() -> PasswordExpirationUtils.isExpired(user)).thenReturn(expired);
  }

  /**
   * Verifies that a regular data request is served when the password is valid.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testValidPasswordReachesProcessing() throws Exception {
    given(BLOCKED_PATH, false);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }

  /**
   * Verifies that a data request is rejected with an unauthorized error when the password is expired,
   * and that the underlying service is never executed.
   */
  @Test
  void testExpiredPasswordBlocksProcessing() {
    given(BLOCKED_PATH, true);

    UnauthorizedException exception = assertThrows(UnauthorizedException.class,
        () -> service.doGet("", request, response));

    assertEquals(Constants.PASSWORD_EXPIRED_ERROR, exception.getMessage());
    assertFalse(service.wasProcessed());
  }

  /**
   * Verifies that the session endpoint stays reachable with an expired password, since the client
   * reads the {@code passwordExpired} flag from it.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testSessionPathIsAllowedWhileExpired() throws Exception {
    given(Constants.SESSION_PATH, true);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }

  /**
   * Verifies that /logout stays reachable with an expired password — otherwise an account whose
   * password was force-expired could never revoke its own leaked token.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testLogoutPathIsAllowedWhileExpired() throws Exception {
    given(Constants.LOGOUT_PATH, true);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }

  /**
   * Verifies that every bootstrap endpoint of the allowlist stays reachable with an expired password,
   * so the mandatory change screen can render translated content.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testAllAllowedPathsAreReachableWhileExpired() throws Exception {
    for (String allowedPath : Constants.PASSWORD_EXPIRED_ALLOWED_PATHS) {
      TestWebService allowedService = new TestWebService();
      given(allowedPath, true);

      allowedService.doGet("", request, response);

      assertTrue(allowedService.wasProcessed(), allowedPath + " must be reachable while expired");
    }
  }

  /**
   * Verifies that the SWS path prefix is stripped before matching the allowlist, so the real
   * {@code /com.etendoerp.metadata.meta/session} request is recognized as a bootstrap endpoint.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testSwsPrefixedSessionPathIsAllowedWhileExpired() throws Exception {
    given("/com.etendoerp.metadata.meta" + Constants.SESSION_PATH, true);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }

  /**
   * Verifies that forwarded requests, which carry the forward prefix and therefore never match the
   * allowlist, are blocked while the password is expired.
   */
  @Test
  void testForwardedRequestIsBlockedWhileExpired() {
    given("/com.etendoerp.metadata.forward/org.openbravo.service.datasource/Order", true);

    assertThrows(UnauthorizedException.class, () -> service.doGet("", request, response));
    assertFalse(service.wasProcessed());
  }

  /**
   * Verifies that a request without an established context is served, so the guard never breaks
   * flows that run before authentication resolves a user.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testMissingContextReachesProcessing() throws Exception {
    obContextStatic.when(OBContext::getOBContext).thenReturn(null);
    given(BLOCKED_PATH, true);

    service.doGet("", request, response);

    assertTrue(service.wasProcessed());
  }

  /**
   * Verifies that the guard is applied to every HTTP verb, not only to GET.
   */
  @Test
  void testEveryVerbIsGuarded() {
    given(BLOCKED_PATH, true);

    assertThrows(UnauthorizedException.class, () -> service.doPost("", request, response));
    assertThrows(UnauthorizedException.class, () -> service.doPut("", request, response));
    assertThrows(UnauthorizedException.class, () -> service.doDelete("", request, response));
    assertThrows(UnauthorizedException.class, () -> service.doPatch("", request, response));
    assertFalse(service.wasProcessed());
  }

  /**
   * Verifies that every HTTP verb reaches processing when the password is valid.
   *
   * @throws Exception if the service dispatch fails
   */
  @Test
  void testEveryVerbReachesProcessingWithValidPassword() throws Exception {
    given(BLOCKED_PATH, false);

    service.doPost("", request, response);
    service.doPut("", request, response);
    service.doDelete("", request, response);
    service.doPatch("", request, response);

    assertTrue(service.wasProcessed());
  }
}
