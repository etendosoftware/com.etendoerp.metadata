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
package com.etendoerp.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.SystemInfo;

import com.etendoerp.etendorx.data.ETRXoAuthProvider;

/**
 * Returns the OAuth provider catalogue published by an Etendo Middleware instance, so the new UI can
 * render the "Get Middleware Token" chooser.
 * <p>
 * The middleware serves {@code /available-providers} without any {@code Access-Control-Allow-Origin}
 * header, so no browser can read the response: the classic handler's own {@code fetch}
 * (ETRX_GetMiddlewareToken.js:21) fails for exactly this reason. Proxying the call server-side is
 * what makes the process usable at all, and it also covers instances whose users have no direct
 * internet egress.
 * <p>
 * The endpoint to contact is resolved <em>from the provider record</em> rather than taken from the
 * request, so a caller cannot point this handler at an arbitrary host.
 * <p>
 * Input ({@code _params}): <code>{ "etrxOauthProviderId": "&lt;id&gt;" }</code>
 * <p>
 * Output on success:
 * <pre>
 * { "status": "Success",
 *   "accountId": "&lt;system identifier&gt;",
 *   "redirectUri": "&lt;provider redirect URI&gt;",
 *   "startEndpoint": "&lt;authorization endpoint&gt;/start",
 *   "providers": { "google": { "name": …, "scopes": [ … ] } } }
 * </pre>
 * Failures answer {@code { "status": "Error", "message": … }} instead of throwing: the caller renders
 * the text, mirroring the message bar the classic handler filled from its {@code .catch()}.
 */
@ApplicationScoped
public class SSOProviderListActionHandler extends BaseActionHandler {

  private static final Logger log = LogManager.getLogger();

  private static final String PARAMS = "_params";
  private static final String STATUS = "status";
  private static final String MESSAGE = "message";
  private static final String ERROR_CODE = "errorCode";
  private static final String STATUS_SUCCESS = "Success";
  private static final String STATUS_ERROR = "Error";
  private static final String PROVIDER_ID_PARAM = "etrxOauthProviderId";

  // Stable error codes. The caller maps these to translated text, so the English `message` beside
  // them is a diagnostic for logs and support, never what the user reads.
  static final String ERROR_NO_PROVIDER_ID = "noProviderId";
  static final String ERROR_PROVIDER_NOT_FOUND = "providerNotFound";
  static final String ERROR_NO_ENDPOINT = "noEndpoint";
  static final String ERROR_UNREADABLE_RESPONSE = "unreadableResponse";
  static final String ERROR_UNREACHABLE = "unreachable";

  /** Path appended to the provider's authorization endpoint to list the available providers. */
  static final String AVAILABLE_PROVIDERS_PATH = "/available-providers";
  /** Path appended to the provider's authorization endpoint to begin the consent hand-off. */
  static final String START_PATH = "/start";

  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 15000;

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    try {
      JSONObject params = new JSONObject(content).getJSONObject(PARAMS);
      String providerId = params.optString(PROVIDER_ID_PARAM, "");

      if (StringUtils.isBlank(providerId)) {
        return error(ERROR_NO_PROVIDER_ID, "No OAuth provider record was supplied");
      }

      // Read under the caller's context on purpose: a record the user's role cannot see must not be
      // proxied on their behalf either.
      ETRXoAuthProvider provider = OBDal.getInstance().get(ETRXoAuthProvider.class, providerId);
      if (provider == null) {
        return error(ERROR_PROVIDER_NOT_FOUND, "OAuth provider record not found: " + providerId);
      }

      String authorizationEndpoint = StringUtils.trimToEmpty(provider.getAuthorizationEndpoint());
      if (StringUtils.isBlank(authorizationEndpoint)) {
        // Authorization_Endpoint is not a mandatory column, so an incomplete record is a normal
        // data state rather than an exceptional one.
        return error(ERROR_NO_ENDPOINT, "The OAuth provider record has no Authorization Endpoint configured");
      }
      authorizationEndpoint = StringUtils.removeEnd(authorizationEndpoint, "/");

      String body = fetchAvailableProviders(authorizationEndpoint + AVAILABLE_PROVIDERS_PATH);

      return buildSuccess(new JSONObject(body), authorizationEndpoint,
          StringUtils.trimToEmpty(provider.getRedirectURI()));

    } catch (JSONException e) {
      log.error("Middleware returned a malformed provider list", e);
      return error(ERROR_UNREADABLE_RESPONSE, "The middleware returned an unreadable provider list");
    } catch (Exception e) {
      log.error("Error retrieving the middleware provider list", e);
      return error(ERROR_UNREACHABLE,
          StringUtils.defaultIfBlank(e.getMessage(), "Could not reach the middleware"));
    }
  }

  /**
   * Assembles the success payload. Kept separate from {@link #execute} so the JSON shape can be
   * asserted without a database or a live middleware.
   *
   * @param providers             the raw catalogue as published by the middleware
   * @param authorizationEndpoint the provider's authorization endpoint, without a trailing slash
   * @param redirectUri           the provider's redirect URI
   * @return the response object
   * @throws JSONException if the response cannot be assembled
   */
  JSONObject buildSuccess(JSONObject providers, String authorizationEndpoint, String redirectUri)
      throws JSONException {
    JSONObject result = new JSONObject();
    result.put(STATUS, STATUS_SUCCESS);
    result.put("providers", providers);
    result.put("redirectUri", redirectUri);
    // The classic handler ignores the authorizationEndpoint each provider publishes and derives
    // this one from the record instead (ETRX_GetMiddlewareToken.js:29); keep that behaviour.
    result.put("startEndpoint", authorizationEndpoint + START_PATH);
    result.put("accountId", getAccountId());
    return result;
  }

  /**
   * Reads the instance's System Identifier, which the middleware expects as {@code account_id}.
   * Equivalent to {@code com.etendoerp.etendorx.GetSSOProperties} with {@code properties=account},
   * folded in here so the caller needs a single round trip and its click handler stays synchronous.
   * <p>
   * An unreadable identifier is downgraded to an empty string rather than failing the whole call:
   * the chooser is still worth rendering, and the middleware is the one that judges the account.
   * Package-private so tests can supply a value without the static AD singletons.
   *
   * @return the system identifier, or an empty string when it cannot be read
   */
  String getAccountId() {
    boolean adminMode = false;
    try {
      OBContext.setAdminMode(true);
      adminMode = true;
      return StringUtils.trimToEmpty(SystemInfo.getSystemIdentifier());
    } catch (Exception e) {
      log.warn("Could not read the system identifier; account id will be empty", e);
      return "";
    } finally {
      if (adminMode) {
        OBContext.restorePreviousMode();
      }
    }
  }

  /**
   * Performs the outbound GET against the middleware. Package-private and overridable so tests can
   * exercise the parsing and error paths without network access.
   *
   * @param url the absolute URL to read
   * @return the response body
   * @throws IOException if the request fails or the service answers a non-200 status
   */
  String fetchAvailableProviders(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/json");
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);

      int status = connection.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) {
        throw new IOException("The middleware answered HTTP " + status);
      }

      try (InputStream in = connection.getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Builds a business-error response. Errors are reported in the payload rather than thrown so the
   * caller can show the reason instead of a generic failure.
   *
   * @param errorCode a stable code the caller maps to translated text
   * @param message   an English diagnostic for logs and support, not for display
   * @return the error response object
   */
  private JSONObject error(String errorCode, String message) {
    JSONObject result = new JSONObject();
    try {
      result.put(STATUS, STATUS_ERROR);
      result.put(ERROR_CODE, errorCode);
      result.put(MESSAGE, message);
    } catch (JSONException e) {
      throw new IllegalStateException("Failed to build error JSON", e);
    }
    return result;
  }
}
