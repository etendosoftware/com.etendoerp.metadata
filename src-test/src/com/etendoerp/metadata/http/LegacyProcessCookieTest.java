package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Verifies the SameSite/Secure attributes produced by
 * {@link LegacyProcessServlet#buildSessionCookieHeader}.
 */
public class LegacyProcessCookieTest {
  /**
   * Invokes the private cookie-header builder via reflection with the given flags.
   */
  private String build(boolean prod, boolean crossSite) throws ReflectiveOperationException {
    Method m = LegacyProcessServlet.class.getDeclaredMethod(
        "buildSessionCookieHeader", String.class, boolean.class, boolean.class);
    m.setAccessible(true);
    return (String) m.invoke(null, "SID123", prod, crossSite);
  }

  /**
   * Default (non-production, non-cross-site) cookies use SameSite=Lax without Domain/Secure.
   * @throws ReflectiveOperationException if the private builder cannot be invoked
   */
  @Test public void sameSiteLaxByDefaultNoDomainNoEmptyAttr() throws ReflectiveOperationException {
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
   * @throws ReflectiveOperationException if the private builder cannot be invoked
   */
  @Test public void crossSiteProductionIsNoneAndSecure() throws ReflectiveOperationException {
    String h = build(true, true);
    assertTrue(h.contains("SameSite=None"));
    assertTrue(h.contains("Secure"));
  }

  /**
   * Cross-site requests over plain HTTP fall back to SameSite=Lax (None requires Secure).
   * @throws ReflectiveOperationException if the private builder cannot be invoked
   */
  @Test public void crossSiteOnHttpFallsBackToLax() throws ReflectiveOperationException {
    String h = build(false, true); // None without Secure is invalid -> Lax
    assertTrue(h.contains("SameSite=Lax"));
    assertFalse(h.contains("SameSite=None"));
  }
}
