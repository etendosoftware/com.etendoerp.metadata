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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.http.session.LegacyHttpSessionAdapter;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.RequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A HttpServletRequest wrapper that extracts and decodes a JWT token and supports custom headers.
 * It extends the Openbravo RequestContext.HttpServletRequestWrapper to provide additional functionality.
 *
 * <p>This class is responsible for:</p>
 * <ul>
 *   <li>Extracting a JWT token from the "Authorization" header or token parameter.</li>
 *   <li>Decoding the token to retrieve the session ID and user ID.</li>
 *   <li>Allowing the addition of custom headers.</li>
 *   <li>Overriding header retrieval methods to include custom headers.</li>
 * </ul>
 *
 * @author luuchorocha
 */
public class HttpServletRequestWrapper extends RequestContext.HttpServletRequestWrapper {
  private String sessionId;
  private final String userId;
  private LegacyHttpSessionAdapter sessionAdapter;
  private final Map<String, List<String>> customHeaders;

  /**
   * Constructs a new HttpServletRequestWrapper by wrapping an existing HttpServletRequest.
   * It extracts the JWT token from the "Authorization" header or token parameter, decodes it,
   * and retrieves the session ID and user ID.
   *
   * @param request The original HttpServletRequest.
   */
  public HttpServletRequestWrapper(HttpServletRequest request) {
    super(request);
    this.customHeaders = new HashMap<>();

    String tokenHeader = request.getHeader("Authorization");
    String token = tokenHeader != null ? StringUtils.substringAfter(tokenHeader, "Bearer ") : null;
    if (StringUtils.isEmpty(token)) {
      token = request.getParameter("token");
      if (!StringUtils.isBlank(token)) {
        customHeaders.put("Authorization", Collections.singletonList("Bearer " + token));
      }
    }
    if (token == null) {
      this.userId = null;
      return;
    }
    DecodedJWT decodedJWT;
    try {
      decodedJWT = SecureWebServicesUtils.decodeToken(token);
    } catch (Exception e) {
      throw new OBException("Error decoding token", e);
    }
    this.userId = decodedJWT.getClaim("user").asString();
    if (request.getSession(false) == null ) {
      this.sessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;
    }
  }

  /**
   * Wraps the given HttpServletRequest in a HttpServletRequestWrapper.
   * If the request is already an instance of HttpServletRequestWrapper it returns the same instance.
   *
   * @param request The HttpServletRequest to wrap.
   * @return A HttpServletRequestWrapper encapsulating the original request.
   */
  public static HttpServletRequestWrapper wrap(HttpServletRequest request) {
    if (request.getClass().equals(HttpServletRequestWrapper.class)) {
      return (HttpServletRequestWrapper) request;
    } else {
      return new HttpServletRequestWrapper(request);
    }
  }

  /**
   * Returns the HttpSession associated with this request.
   * If the session hasn't been created yet, it creates one using LegacyHttpSessionAdapter.
   *
   * @return The HttpSession for this request.
   */
  @Override
  public HttpSession getSession() {
    if (this.sessionAdapter == null) {
      this.sessionAdapter = new LegacyHttpSessionAdapter(sessionId, getServletContext());
    }
    return this.sessionAdapter;
  }

  /**
   * Returns the HttpSession associated with this request.
   * This implementation ignores the 'create' parameter.
   *
   * @param create Ignored parameter.
   * @return The HttpSession for this request.
   */
  @Override
  public HttpSession getSession(boolean create) {
    return getSession();
  }

  /**
   * Adds a custom header to this request.
   * If the header already exists, the new value is added to the list of existing values.
   *
   * @param name  The header name.
   * @param value The header value.
   */
  public void addHeader(String name, String value) {
    if (name == null || value == null) {
      return; // Or throw IllegalArgumentException.
    }
    // Use lowercase for the header name as key.
    this.customHeaders.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(value);
  }

  /**
   * Retrieves the first value of a header by name.
   * It checks both the custom header map and the underlying request.
   *
   * @param name The header name.
   * @return The header value, or null if not found.
   */
  @Override
  public String getHeader(String name) {
    if (name == null) {
      return null;
    }
    List<String> headerValues = this.customHeaders.get(name);
    if (headerValues != null && !headerValues.isEmpty()) {
      return headerValues.get(0); // Standard behavior: return the first value.
    }
    return super.getHeader(name);
  }

  /**
   * Retrieves all values of the given header.
   * It checks both the custom header map and the underlying request.
   *
   * @param name The header name.
   * @return An Enumeration of header values.
   */
  @Override
  public Enumeration<String> getHeaders(String name) {
    if (name == null) {
      return Collections.emptyEnumeration();
    }
    List<String> headerValues = this.customHeaders.get(name.toLowerCase());
    if (headerValues != null && !headerValues.isEmpty()) {
      return Collections.enumeration(headerValues);
    }
    return super.getHeaders(name);
  }

  /**
   * Retrieves an enumeration of all header names.
   * Both custom headers and original headers from the request are returned.
   *
   * @return An Enumeration of header names.
   */
  @Override
  public Enumeration<String> getHeaderNames() {
    Set<String> headerNames = new HashSet<>();
    Enumeration<String> originalHeaderNames = super.getHeaderNames();
    while (originalHeaderNames.hasMoreElements()) {
      headerNames.add(originalHeaderNames.nextElement());
    }
    headerNames.addAll(this.customHeaders.keySet());
    return Collections.enumeration(headerNames);
  }

  /**
   * Retrieves the specified header as an integer.
   * If the header is not found, it returns -1.
   *
   * @param name The header name.
   * @return The header value as an int.
   * @throws NumberFormatException If the header cannot be parsed as an integer.
   */
  @Override
  public int getIntHeader(String name) {
    String value = getHeader(name);
    if (value == null) {
      return -1;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new NumberFormatException("Header " + name + " cannot be converted to int: " + value);
    }
  }

  /**
   * Retrieves the specified header as a date in milliseconds since January 1, 1970 GMT.
   * Custom date headers are not supported and will throw an exception.
   *
   * @param name The header name.
   * @return The header date in milliseconds, or -1 if not found.
   * @throws UnsupportedOperationException If a custom header is used for date parsing.
   */
  @Override
  public long getDateHeader(String name) {
    String value = getHeader(name);
    if (value == null) {
      return -1L;
    }
    if (this.customHeaders.containsKey(name.toLowerCase())) {
      throw new UnsupportedOperationException("Custom date header parsing not implemented in this wrapper for: " + name);
    }
    return super.getDateHeader(name);
  }

  /**
   * Returns the session ID extracted from the JWT token.
   *
   * @return The session ID.
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Sets the session ID to a new value.
   *
   * @param sessionId The new session ID.
   */
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
