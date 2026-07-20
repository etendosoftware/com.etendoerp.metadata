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

package com.etendoerp.metadata.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.SystemInfo;
import org.openbravo.model.ad.access.User;

import javax.servlet.ServletException;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.etendoerp.etendorx.auth.JwkRSAKeyProvider;
import com.etendoerp.etendorx.data.ETRXTokenUser;
import com.etendoerp.metadata.auth.Utils;
import com.etendoerp.metadata.data.AuthData;

/**
 * Handles SSO authentication for the new UI.
 * <p>
 * Exposes two unauthenticated endpoints:
 * <ul>
 *   <li>{@code GET /meta/sso/config} — returns SSO provider configuration</li>
 *   <li>{@code POST /meta/sso/callback} — exchanges OAuth code/token for Etendo JWT</li>
 * </ul>
 * <p>
 * This service is dispatched directly by {@link com.etendoerp.metadata.http.MetadataFilter}
 * before the SWS auth chain, so no pre-existing JWT is required.
 */
public class SSOService {

    private static final Logger log = LogManager.getLogger(SSOService.class);
    private static final String SSO_AUTH_TYPE = "sso.auth.type";
    private static final String SSO_DOMAIN_URL = "sso.domain.url";
    private static final String AUTH0 = "Auth0";
    private static final String INTERNAL_ERROR = "internal_error";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String REDIRECT_URI_KEY = "redirectUri";
    private static final String[][] MIDDLEWARE_PROVIDERS = {
        { "google-oauth2", "google" },
        { "windowslive", "microsoft" },
        { "linkedin", "linkedin" },
        { "github", "github" },
        { "facebook", "facebook" }
    };
    private static final String MIDDLEWARE_ISSUER = "https://etendo-sso.us.auth0.com/";

    /**
     * Routes the request to the appropriate handler based on the path.
     *
     * @param request  the HTTP request containing the SSO path and parameters
     * @param response the HTTP response to write the result to
     * @throws IOException if an I/O error occurs while handling the request
     */
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        String ssoPath = pathInfo.substring("/sso".length());

        try {
            switch (ssoPath) {
                case "/config":
                    handleConfig(response);
                    break;
                case "/callback":
                    handleCallback(request, response);
                    break;
                case "/link":
                    handleLink(request, response);
                    break;
                default:
                    writeJson(response, HttpServletResponse.SC_NOT_FOUND,
                        errorJson("not_found", "Unknown SSO endpoint: " + ssoPath));
                    break;
            }
        } catch (JSONException e) {
            log.error("SSO JSON error", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorJson(INTERNAL_ERROR, e.getMessage()));
        }
    }

    /**
     * GET /meta/sso/config — returns SSO configuration for the login screen.
     */
    private void handleConfig(HttpServletResponse response)
            throws IOException, JSONException {
        Properties props = OBPropertiesProvider.getInstance().getOpenbravoProperties();
        String authType = StringUtils.trimToEmpty(props.getProperty(SSO_AUTH_TYPE));

        JSONObject config = new JSONObject();

        if (StringUtils.isBlank(authType)) {
            config.put("enabled", false);
            writeJson(response, HttpServletResponse.SC_OK, config);
            return;
        }

        config.put("enabled", true);
        config.put("authType", authType);

        if (AUTH0.equalsIgnoreCase(authType)) {
            config.put("domain", StringUtils.trimToEmpty(props.getProperty(SSO_DOMAIN_URL)));
            config.put("clientId", StringUtils.trimToEmpty(props.getProperty("sso.client.id")));
            config.put("callbackUrl", StringUtils.trimToEmpty(props.getProperty("sso.callback.url")));
        } else {
            config.put("middlewareUrl", StringUtils.trimToEmpty(props.getProperty("sso.middleware.url")));
            config.put(REDIRECT_URI_KEY, StringUtils.trimToEmpty(props.getProperty("sso.middleware.redirectUri")));
            try {
                OBContext.setAdminMode(true);
                config.put("accountId", SystemInfo.getSystemIdentifier());
            } catch (ServletException e) {
                log.error("Failed to retrieve system identifier", e);
                config.put("accountId", "");
            } finally {
                OBContext.restorePreviousMode();
            }
            JSONArray providers = new JSONArray();
            for (String[] p : MIDDLEWARE_PROVIDERS) {
                JSONObject provider = new JSONObject();
                provider.put("id", p[0]);
                provider.put("name", p[1]);
                providers.put(provider);
            }
            config.put("providers", providers);
        }

        writeJson(response, HttpServletResponse.SC_OK, config);
    }

    /**
     * POST /meta/sso/callback — exchanges OAuth code/token for an Etendo JWT.
     */
    private void handleCallback(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            writeJson(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                errorJson("method_not_allowed", "Use POST"));
            return;
        }

        Properties props = OBPropertiesProvider.getInstance().getOpenbravoProperties();
        String authType = StringUtils.trimToEmpty(props.getProperty(SSO_AUTH_TYPE));

        if (StringUtils.isBlank(authType)) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                errorJson("sso_not_configured", "SSO authentication is not configured on this server"));
            return;
        }

        JSONObject body = parseJsonBody(request, response);
        if (body == null) {
            return;
        }

        String idToken = resolveSsoToken(body, props, authType, response);
        if (idToken == null) {
            return;
        }

        if (!validateJwksToken(idToken, props, authType)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                errorJson("invalid_token", "JWT signature verification failed"));
            return;
        }

        DecodedJWT decoded = JWT.decode(idToken);
        String sub = decoded.getClaim("sub").asString();

        try {
            OBContext.setAdminMode(true);
            ETRXTokenUser tokenUser = (ETRXTokenUser) OBDal.getInstance()
                .createCriteria(ETRXTokenUser.class)
                .add(Restrictions.eq(ETRXTokenUser.PROPERTY_SUB, sub))
                .setFilterOnReadableClients(false)
                .setFilterOnReadableOrganization(false)
                .setMaxResults(1)
                .uniqueResult();

            if (tokenUser == null) {
                writeJson(response, HttpServletResponse.SC_NOT_FOUND,
                    errorJson("no_user_linked",
                        "No Etendo user is linked to this SSO account. "
                        + "Log in with credentials first and link your SSO account."));
                return;
            }

            tokenUser.setOAuthToken(idToken);
            OBDal.getInstance().save(tokenUser);
            OBDal.getInstance().flush();

            User user = tokenUser.getUserForToken();
            AuthData authData = new AuthData(user, user.getDefaultRole(),
                user.getDefaultOrganization(), user.getDefaultWarehouse(),
                user.getDefaultRole() != null ? user.getDefaultRole().getClient() : user.getClient());

            String token = Utils.generateToken(authData, null);

            JSONObject result = new JSONObject();
            result.put("token", token);
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            writeJson(response, HttpServletResponse.SC_OK, result);

        } catch (Exception e) {
            log.error("SSO callback error", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorJson(INTERNAL_ERROR, "Authentication failed: " + e.getMessage()));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * POST /meta/sso/link — links an SSO identity to the currently authenticated user.
     * Requires a valid Etendo JWT in the Authorization header.
     * Body: { "accessToken": "..." } (middleware) or { "code": "...", ... } (Auth0)
     */
    private void handleLink(HttpServletRequest request, HttpServletResponse response)
            throws IOException, JSONException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            writeJson(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                errorJson("method_not_allowed", "Use POST"));
            return;
        }

        String userId = extractUserIdFromBearer(request);
        if (userId == null) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                errorJson("unauthorized", "Valid Authorization: Bearer <token> header required"));
            return;
        }

        Properties props = OBPropertiesProvider.getInstance().getOpenbravoProperties();
        String authType = StringUtils.trimToEmpty(props.getProperty(SSO_AUTH_TYPE));

        if (StringUtils.isBlank(authType)) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                errorJson("sso_not_configured", "SSO authentication is not configured on this server"));
            return;
        }

        JSONObject body = parseJsonBody(request, response);
        if (body == null) {
            return;
        }

        String ssoToken = resolveSsoToken(body, props, authType, response);
        if (ssoToken == null) {
            return;
        }

        if (!validateJwksToken(ssoToken, props, authType)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                errorJson("invalid_token", "SSO token signature verification failed"));
            return;
        }

        DecodedJWT decoded = JWT.decode(ssoToken);
        String sub = decoded.getClaim("sub").asString();
        String tokenProvider = sub.contains("|") ? sub.substring(0, sub.indexOf('|')) : sub;

        try {
            OBContext.setAdminMode(true);

            User user = OBDal.getInstance().get(User.class, userId);
            if (user == null) {
                writeJson(response, HttpServletResponse.SC_NOT_FOUND,
                    errorJson("user_not_found", "Authenticated user not found"));
                return;
            }

            ETRXTokenUser existing = (ETRXTokenUser) OBDal.getInstance()
                .createCriteria(ETRXTokenUser.class)
                .add(Restrictions.eq(ETRXTokenUser.PROPERTY_SUB, sub))
                .setFilterOnReadableClients(false)
                .setFilterOnReadableOrganization(false)
                .setMaxResults(1)
                .uniqueResult();
            if (existing != null) {
                OBDal.getInstance().remove(existing);
                OBDal.getInstance().flush();
            }

            ETRXTokenUser tokenUser = new ETRXTokenUser();
            tokenUser.setClient(user.getClient());
            tokenUser.setOrganization(user.getOrganization());
            tokenUser.setSub(sub);
            tokenUser.setOAuthToken(ssoToken);
            tokenUser.setTokenProvider(tokenProvider);
            tokenUser.setUserForToken(user);
            OBDal.getInstance().save(tokenUser);
            OBDal.getInstance().flush();

            JSONObject result = new JSONObject();
            result.put("status", "linked");
            result.put("provider", tokenProvider);
            result.put("sub", sub);
            writeJson(response, HttpServletResponse.SC_OK, result);

        } catch (Exception e) {
            log.error("SSO link error", e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                errorJson(INTERNAL_ERROR, "Failed to link account: " + e.getMessage()));
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Extracts the user ID from the Authorization: Bearer header.
     * Returns null if the header is missing or the token is invalid.
     */
    private String extractUserIdFromBearer(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            DecodedJWT decoded = Utils.decodeToken(token);
            return decoded.getClaim("user").asString();
        } catch (Exception e) {
            log.debug("Failed to decode bearer token for SSO link", e);
            return null;
        }
    }

    /**
     * Reads and parses the JSON body from the request.
     * Returns null and writes a 400 error response if the body is not valid JSON.
     */
    private JSONObject parseJsonBody(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (var reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        try {
            return new JSONObject(sb.toString());
        } catch (JSONException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorJson(INVALID_REQUEST, "Request body must be valid JSON"));
            return null;
        }
    }

    /**
     * Resolves the SSO token from the request body based on auth type.
     * For Auth0, exchanges the authorization code. For middleware, extracts the access token directly.
     * Returns null and writes an error response if resolution fails.
     */
    private String resolveSsoToken(JSONObject body, Properties props, String authType,
            HttpServletResponse response) throws IOException, JSONException {
        if (AUTH0.equalsIgnoreCase(authType)) {
            String code = body.optString("code", "");
            if (StringUtils.isBlank(code)) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    errorJson(INVALID_REQUEST, "Missing required field: code"));
                return null;
            }
            String codeVerifier = body.optString("codeVerifier", null);
            String redirectUri = body.optString(REDIRECT_URI_KEY, "");
            String token = exchangeCodeForToken(props, code, codeVerifier, redirectUri);
            if (token == null) {
                writeJson(response, HttpServletResponse.SC_BAD_GATEWAY,
                    errorJson("token_exchange_failed", "Failed to exchange authorization code for token"));
            }
            return token;
        }
        String token = body.optString("accessToken", "");
        if (StringUtils.isBlank(token)) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                errorJson(INVALID_REQUEST, "Missing required field: accessToken"));
            return null;
        }
        return token;
    }

    /**
     * Exchanges an Auth0 authorization code for an ID token.
     * Replicates the logic from SWSAuthenticationManager.getAuthToken().
     */
    private String exchangeCodeForToken(Properties props, String code,
            String codeVerifier, String redirectUri) {
        String domain = props.getProperty(SSO_DOMAIN_URL);
        String tokenEndpoint = "https://" + domain + "/oauth/token";
        String clientId = props.getProperty("sso.client.id");
        String clientSecret = props.getProperty("sso.client.secret");

        try {
            URL url = new URL(tokenEndpoint);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setDoOutput(true);

            boolean isPKCE = StringUtils.isNotBlank(codeVerifier);
            String params;
            if (isPKCE) {
                params = String.format(
                    "grant_type=authorization_code&client_id=%s&code=%s&redirect_uri=%s&code_verifier=%s",
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                    URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8));
            } else {
                if (StringUtils.isBlank(clientSecret)) {
                    log.error("Auth0 client_secret not configured and no PKCE code_verifier provided");
                    return null;
                }
                params = String.format(
                    "grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s&redirect_uri=%s",
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
            }

            try (OutputStream os = con.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            if (con.getResponseCode() == 200) {
                try (InputStream in = con.getInputStream()) {
                    String responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    return new JSONObject(responseBody).getString("id_token");
                }
            } else {
                log.error("Auth0 token exchange failed: {} {}", con.getResponseCode(), con.getResponseMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("Auth0 token exchange error", e);
            return null;
        }
    }

    /**
     * Validates a JWT token's signature via JWKS endpoint.
     * Replicates logic from SWSAuthenticationManager.validateToken().
     */
    private boolean validateJwksToken(String token, Properties props, String authType) {
        try {
            String baseURL;
            String issuer;

            if (AUTH0.equalsIgnoreCase(authType)) {
                String domain = props.getProperty(SSO_DOMAIN_URL);
                baseURL = "https://" + domain;
                issuer = baseURL + "/";
            } else {
                baseURL = props.getProperty("sso.middleware.url");
                issuer = MIDDLEWARE_ISSUER;
            }

            URL jwkURL = new URL(baseURL + "/.well-known/jwks.json");
            JwkProvider provider = new UrlJwkProvider(jwkURL);

            DecodedJWT jwt = JWT.decode(token);
            RSAKeyProvider keyProvider = new JwkRSAKeyProvider(provider, jwt.getKeyId());
            Algorithm algorithm = Algorithm.RSA256(keyProvider);

            JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation failed", e);
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int status, JSONObject json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.toString());
        response.getWriter().flush();
    }

    private JSONObject errorJson(String error, String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("error", error);
            json.put("message", message);
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build error JSON", e);
        }
    }
}
