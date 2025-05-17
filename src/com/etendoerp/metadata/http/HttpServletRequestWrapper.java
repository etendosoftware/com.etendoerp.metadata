package com.etendoerp.metadata.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.jboss.weld.module.web.servlet.SessionHolder;
import org.openbravo.client.kernel.RequestContext;

/**
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
  private static final ThreadLocal<HttpSessionWrapper> session = new ThreadLocal<>();

  public HttpServletRequestWrapper(HttpServletRequest request) {
    super(request);
    session.set(new HttpSessionWrapper());
    SessionHolder.requestInitialized(this);
  }

  public static HttpServletRequestWrapper wrap(HttpServletRequest request) {
    if (request.getClass().equals(HttpServletRequestWrapper.class)) {
      return (HttpServletRequestWrapper) request;
    } else {
      return new HttpServletRequestWrapper(request);
    }
  }

  public static void clear() {
    session.remove();
    SessionHolder.clear();
  }

  @Override
  public HttpSession getSession() {
    return session.get();
  }

  @Override
  public HttpSession getSession(boolean f) {
    return session.get();
  }
}
