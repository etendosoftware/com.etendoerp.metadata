package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Verifies the SameSite/Secure attributes produced by
 * {@link LegacySessionCookie#header}.
 */
public class LegacyProcessCookieTest {
  /**
   * Builds the cookie header with the given flags.
   */
  private String build(boolean prod, boolean crossSite) {
    return LegacySessionCookie.header("SID123", "/etendo", prod, crossSite);
  }

  /**
   * The cookie is scoped to the actual context path, not a hardcoded one.
   */
  @Test public void pathFollowsContextPath() {
    assertTrue(LegacySessionCookie.header("SID123", "/erp", false, false).contains("; Path=/erp;"));
  }

  /**
   * The root context (empty context path) scopes the cookie to "/".
   */
  @Test public void rootContextUsesSlashPath() {
    assertTrue(LegacySessionCookie.header("SID123", "", false, false).contains("; Path=/;"));
    assertTrue(LegacySessionCookie.header("SID123", null, false, false).contains("; Path=/;"));
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
    assertTrue(h.contains("JSESSIONID=SID123"));
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
