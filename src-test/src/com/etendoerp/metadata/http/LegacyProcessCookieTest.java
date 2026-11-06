package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Verifies the SameSite/Secure attributes produced by
 * {@link LegacySessionCookie#header}.
 */
public class LegacyProcessCookieTest {
  private static final String SESSION_ID = "SID123";

  /**
   * Builds the cookie header with the given flags.
   */
  private String build(boolean prod, boolean crossSite) {
    return LegacySessionCookie.header(SESSION_ID, "/etendo", prod, crossSite);
  }

  /**
   * The cookie is scoped to the actual context path, not a hardcoded one.
   */
  @Test public void pathFollowsContextPath() {
    assertTrue(LegacySessionCookie.header(SESSION_ID, "/erp", false, false).contains("; Path=/erp;"));
  }

  /**
   * The root context (empty context path) scopes the cookie to "/".
   */
  @Test public void rootContextUsesSlashPath() {
    assertTrue(LegacySessionCookie.header(SESSION_ID, "", false, false).contains("; Path=/;"));
    assertTrue(LegacySessionCookie.header(SESSION_ID, null, false, false).contains("; Path=/;"));
  }

  /**
   * Default (non-production, non-cross-site) cookies use SameSite=Lax without Domain/Secure.
   */
  @Test public void sameSiteLaxByDefaultNoDomainNoEmptyAttr() {
    String h = build(false, false);
    assertTrue(h.contains("SameSite=Lax"));
    assertFalse("must not pin Domain", h.contains("Domain="));
    assertFalse("must not emit empty attribute", h.contains("; ;"));
    assertFalse("Lax/dev is not Secure", h.contains("Secure"));
    assertTrue(h.contains("HttpOnly"));
    assertTrue(h.contains("JSESSIONID=" + SESSION_ID));
  }

  /**
   * Cross-site requests in production use SameSite=None and are marked Secure.
   */
  @Test public void crossSiteProductionIsNoneAndSecure() {
    String h = build(true, true);
    assertTrue(h.contains("SameSite=None"));
    assertTrue(h.contains("Secure"));
  }

  /**
   * Cross-site requests over plain HTTP fall back to SameSite=Lax (None requires Secure).
   */
  @Test public void crossSiteOnHttpFallsBackToLax() {
    String h = build(false, true); // None without Secure is invalid -> Lax
    assertTrue(h.contains("SameSite=Lax"));
    assertFalse(h.contains("SameSite=None"));
  }
}
