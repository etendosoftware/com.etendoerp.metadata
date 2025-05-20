package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.http.session.LegacyHttpSessionAdapter;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
  private final String sessionId;
  private final String userId;
  private LegacyHttpSessionAdapter sessionAdapter;

  public HttpServletRequestWrapper(HttpServletRequest request) {
    super(request);
    String tokenHeader = request.getHeader("Authorization");
    String token = tokenHeader != null ? StringUtils.substringAfter(tokenHeader, "Bearer ") : null;
    DecodedJWT decodedJWT;
    try {
      decodedJWT = SecureWebServicesUtils.decodeToken(token);
    } catch (Exception e) {
      throw new OBException("Error decoding token", e);
    }
    this.sessionId = decodedJWT.getClaims().get("jti").asString();
    this.userId = decodedJWT.getClaim("user").asString();
  }

  public static HttpServletRequestWrapper wrap(HttpServletRequest request) {
    if (request.getClass().equals(HttpServletRequestWrapper.class)) {
      return (HttpServletRequestWrapper) request;
    } else {
      return new HttpServletRequestWrapper(request);
    }
  }

  @Override
  public HttpSession getSession() {
    if (this.sessionAdapter == null) {
      this.sessionAdapter = new LegacyHttpSessionAdapter(sessionId, getServletContext());
    }
    return this.sessionAdapter;
  }

  @Override
  public HttpSession getSession(boolean create) {
    return getSession();
  }
}
