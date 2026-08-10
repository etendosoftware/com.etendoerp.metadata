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
package com.etendoerp.metadata.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Date;

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
import org.openbravo.model.ad.system.Client;

/**
 * Test class for {@link PasswordExpirationUtils}.
 * Covers both expiration triggers Etendo Classic applies at login: the administrator flag on the
 * user record and the per-client validity window based on the last password update.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class PasswordExpirationUtilsTest {

  private static final long ONE_DAY_IN_MILLIS = 24L * 60 * 60 * 1000;
  private static final Long THIRTY_DAYS = 30L;

  @Mock
  private User user;

  @Mock
  private Client client;

  private MockedStatic<OBContext> obContextStatic;

  /**
   * Neutralizes the admin-mode switches so the utility can be exercised without a real OBContext.
   */
  @BeforeEach
  void setUp() {
    obContextStatic = mockStatic(OBContext.class);
    obContextStatic.when(() -> OBContext.setAdminMode(anyBoolean())).thenAnswer(invocation -> null);
    obContextStatic.when(OBContext::restorePreviousMode).thenAnswer(invocation -> null);

    when(user.getClient()).thenReturn(client);
  }

  @AfterEach
  void tearDown() {
    obContextStatic.close();
  }

  /**
   * Builds a date the given number of days away from now.
   *
   * @param daysAgo how many days in the past the returned date must be; negative values return a
   *                date in the future
   * @return the computed date
   */
  private Date daysAgo(int daysAgo) {
    return new Date(System.currentTimeMillis() - (daysAgo * ONE_DAY_IN_MILLIS));
  }

  /**
   * Configures the user with a validity window and a last password update date.
   *
   * @param validityDays the days configured on the client, may be {@code null}
   * @param lastUpdate   the last password update date, may be {@code null}
   */
  private void givenValidityWindow(Long validityDays, Date lastUpdate) {
    when(client.getDaysToPasswordExpiration()).thenReturn(validityDays);
    when(user.getLastPasswordUpdate()).thenReturn(lastUpdate);
  }

  /**
   * Verifies that a null user is never reported as expired, so unauthenticated requests are not
   * mistakenly blocked.
   */
  @Test
  void testNullUserIsNotExpired() {
    assertFalse(PasswordExpirationUtils.isExpired(null));
  }

  /**
   * Verifies that the administrator flag on the user record alone marks the password as expired,
   * regardless of the client validity window.
   */
  @Test
  void testFlaggedUserIsExpired() {
    when(user.isPasswordExpired()).thenReturn(true);
    givenValidityWindow(null, daysAgo(0));

    assertTrue(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a user with no flag and no validity window configured is not expired.
   */
  @Test
  void testNullValidityDaysIsNotExpired() {
    givenValidityWindow(null, daysAgo(500));

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a validity window of zero disables the time-based rule, matching the help text of
   * {@code AD_Client.DaysToPasswordExpiration}.
   */
  @Test
  void testZeroValidityDaysIsNotExpired() {
    givenValidityWindow(0L, daysAgo(500));

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a password older than the validity window is reported as expired.
   */
  @Test
  void testPasswordOlderThanValidityWindowIsExpired() {
    givenValidityWindow(THIRTY_DAYS, daysAgo(31));

    assertTrue(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a password still inside the validity window is not reported as expired.
   */
  @Test
  void testPasswordInsideValidityWindowIsNotExpired() {
    givenValidityWindow(THIRTY_DAYS, daysAgo(29));

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that reaching the validity window exactly already expires the password, mirroring the
   * {@code <=} comparison of the Classic implementation.
   */
  @Test
  void testPasswordAtValidityWindowBoundaryIsExpired() {
    givenValidityWindow(THIRTY_DAYS, daysAgo(30));

    assertTrue(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a user without a last password update date is not expired, so an incomplete record
   * cannot lock the user out through the time-based rule.
   */
  @Test
  void testMissingLastPasswordUpdateIsNotExpired() {
    givenValidityWindow(THIRTY_DAYS, null);

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a user without an associated client is not expired through the time-based rule.
   */
  @Test
  void testMissingClientIsNotExpired() {
    when(user.getClient()).thenReturn(null);
    when(user.getLastPasswordUpdate()).thenReturn(daysAgo(500));

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that the admin-mode scope is always restored, even for the trivial null-user path, so
   * the guard cannot leak an elevated context onto the request.
   */
  @Test
  void testAdminModeIsRestored() {
    givenValidityWindow(THIRTY_DAYS, daysAgo(1));

    PasswordExpirationUtils.isExpired(user);

    obContextStatic.verify(OBContext::restorePreviousMode);
  }

  /**
   * Sanity check that the boundary helper produces a future date for negative inputs, keeping the
   * validity-window assertions meaningful.
   */
  @Test
  void testFuturePasswordUpdateIsNotExpired() {
    givenValidityWindow(THIRTY_DAYS, daysAgo(-1));

    assertFalse(PasswordExpirationUtils.isExpired(user));
  }

  /**
   * Verifies that a password far beyond a very short validity window is expired, ruling out any
   * dependency on calendar rounding at the threshold.
   */
  @Test
  void testLongExpiredPasswordIsExpired() {
    givenValidityWindow(1L, daysAgo(365));

    assertTrue(PasswordExpirationUtils.isExpired(user));
  }
}
