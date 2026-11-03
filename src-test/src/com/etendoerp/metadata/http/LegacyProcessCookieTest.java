package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.junit.Test;

public class LegacyProcessCookieTest {
  private String build(boolean prod, boolean crossSite) throws Exception {
    Method m = LegacyProcessServlet.class.getDeclaredMethod(
        "buildSessionCookieHeader", String.class, boolean.class, boolean.class);
    m.setAccessible(true);
    return (String) m.invoke(null, "SID123", prod, crossSite);
  }

  @Test public void sameSiteLax_byDefault_noDomain_noEmptyAttr() throws Exception {
    String h = build(false, false);
    assertTrue(h.contains("SameSite=Lax"));
    assertFalse("must not pin Domain", h.contains("Domain="));
    assertFalse("must not emit empty attribute", h.contains("; ;"));
    assertFalse("Lax/dev is not Secure", h.contains("Secure"));
    assertTrue(h.contains("HttpOnly"));
    assertTrue(h.contains("JSESSIONID=SID123"));
  }
  @Test public void crossSiteProduction_isNoneAndSecure() throws Exception {
    String h = build(true, true);
    assertTrue(h.contains("SameSite=None"));
    assertTrue(h.contains("Secure"));
  }
  @Test public void crossSiteOnHttp_fallsBackToLax() throws Exception {
    String h = build(false, true); // None without Secure is invalid -> Lax
    assertTrue(h.contains("SameSite=Lax"));
    assertFalse(h.contains("SameSite=None"));
  }
}
