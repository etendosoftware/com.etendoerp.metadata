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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.SystemInfo;

import com.etendoerp.metadata.auth.Utils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SSOServiceTest {

    private static final String SSO_CONFIG_PATH = "/sso/config";
    private static final String SSO_CALLBACK_PATH = "/sso/callback";
    private static final String SSO_LINK_PATH = "/sso/link";
    private static final String ENABLED_KEY = "enabled";
    private static final String SSO_AUTH_TYPE_PROP = "sso.auth.type";
    private static final String AUTH0_TYPE = "Auth0";
    private static final String MIDDLEWARE_TYPE = "Middleware";
    private static final String ERROR_KEY = "error";
    private static final String INVALID_REQUEST_ERROR = "invalid_request";
    private static final String UNAUTHORIZED_ERROR = "unauthorized";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String INVALID_TOKEN_ERROR = "invalid_token";
    private static final String TOKEN_EXCHANGE_FAILED_ERROR = "token_exchange_failed";
    private static final String MIDDLEWARE_URL_PROP = "sso.middleware.url";
    private static final String MIDDLEWARE_REDIRECT_PROP = "sso.middleware.redirectUri";
    private static final String MIDDLEWARE_URL_VALUE = "http://middleware:3000";
    private static final String TEST_REDIRECT_URI = "http://localhost/callback";
    private static final String PROVIDERS_KEY = "providers";
    // Minimal valid JWT: header.payload.signature (signature is irrelevant for decode)
    private static final String FAKE_JWT =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3QifQ"
        + ".eyJzdWIiOiJ0ZXN0fDEyMyJ9.fakesig";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OBPropertiesProvider propertiesProvider;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    private void mockProperties(MockedStatic<OBPropertiesProvider> providerStatic, Properties props) {
        providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
        when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);
    }

    private void setupRequest(String path, String method) {
        when(request.getPathInfo()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
    }

    private JSONObject executeAndParse() throws Exception {
        new SSOService().handle(request, response);
        return new JSONObject(responseWriter.toString());
    }

    @Test
    void configReturnsDisabledWhenNoSSOConfigured() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, new Properties());
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertFalse(result.getBoolean(ENABLED_KEY));
        }
    }

    @Test
    void configReturnsAuth0Config() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            props.setProperty("sso.domain.url", "etendo.auth0.com");
            props.setProperty("sso.client.id", "test-client-id");
            props.setProperty("sso.callback.url", "http://localhost:8080/etendo/callback");

            mockProperties(providerStatic, props);
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertTrue(result.getBoolean(ENABLED_KEY));
            assertEquals(AUTH0_TYPE, result.getString("authType"));
            assertEquals("etendo.auth0.com", result.getString("domain"));
            assertEquals("test-client-id", result.getString("clientId"));
        }
    }

    @Test
    void configReturnsMiddlewareConfig() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<SystemInfo> systemInfoStatic = mockStatic(SystemInfo.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, MIDDLEWARE_URL_VALUE);
            props.setProperty(MIDDLEWARE_REDIRECT_PROP, "http://localhost:8080/etendo/saveToken");

            mockProperties(providerStatic, props);
            systemInfoStatic.when(SystemInfo::getSystemIdentifier).thenReturn("test-account-id");
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertTrue(result.getBoolean(ENABLED_KEY));
            assertEquals(MIDDLEWARE_TYPE, result.getString("authType"));
            assertEquals(MIDDLEWARE_URL_VALUE, result.getString("middlewareUrl"));
            assertEquals("test-account-id", result.getString("accountId"));
        }
    }

    @Test
    void callbackRejectsGetMethod() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "GET");

        new SSOService().handle(request, response);

        verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    void callbackRejectsMissingAuthType() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, new Properties());

            new SSOService().handle(request, response);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void callbackRejectsMalformedJson() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("not json")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void callbackRejectsMissingCodeForAuth0() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"authType\":\"Auth0\"}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void unknownPathReturns404() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/unknown");

        new SSOService().handle(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    // --- /sso/link tests ---

    @Test
    void linkRejectsGetMethod() throws Exception {
        setupRequest(SSO_LINK_PATH, "GET");

        new SSOService().handle(request, response);

        verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    void linkRejectsNoAuthorizationHeader() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);

        JSONObject result = executeAndParse();
        assertEquals(UNAUTHORIZED_ERROR, result.getString(ERROR_KEY));
    }

    @Test
    void linkRejectsEmptyBearerToken() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer   ");

        JSONObject result = executeAndParse();
        assertEquals(UNAUTHORIZED_ERROR, result.getString(ERROR_KEY));
    }

    @Test
    void linkRejectsInvalidBearerToken() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer invalid-token");

        try (MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            utilsStatic.when(() -> Utils.decodeToken("invalid-token"))
                .thenThrow(new RuntimeException("bad token"));

            JSONObject result = executeAndParse();
            assertEquals(UNAUTHORIZED_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkRejectsMissingAuthType() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, new Properties());

            new SSOService().handle(request, response);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void linkRejectsMalformedJson() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("not json")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkRejectsMissingCodeForAuth0() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkRejectsMissingAccessTokenForMiddleware() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    // --- middleware callback tests ---

    @Test
    void callbackRejectsMissingAccessTokenForMiddleware() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_REQUEST_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkRejectsNonBearerAuthorizationHeader() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Basic dXNlcjpwYXNz");

        JSONObject result = executeAndParse();
        assertEquals(UNAUTHORIZED_ERROR, result.getString(ERROR_KEY));
    }

    // --- token exchange / validation tests ---

    @Test
    void callbackAuth0TokenExchangeFails() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"code\":\"test-code\"}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, AUTH0_TYPE);
            props.setProperty("sso.domain.url", "localhost.invalid");
            props.setProperty("sso.client.id", "cid");
            props.setProperty("sso.client.secret", "csecret");
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(TOKEN_EXCHANGE_FAILED_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void callbackMiddlewareInvalidToken() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"accessToken\":\"" + FAKE_JWT + "\"}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, "http://localhost:1");
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_TOKEN_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkMiddlewareInvalidToken() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"accessToken\":\"" + FAKE_JWT + "\"}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, "http://localhost:1");
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_TOKEN_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void configMiddlewareHandlesSystemInfoException() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<SystemInfo> systemInfoStatic = mockStatic(SystemInfo.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, MIDDLEWARE_URL_VALUE);
            props.setProperty(MIDDLEWARE_REDIRECT_PROP, TEST_REDIRECT_URI);

            mockProperties(providerStatic, props);
            systemInfoStatic.when(SystemInfo::getSystemIdentifier)
                .thenThrow(new javax.servlet.ServletException("unavailable"));
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertTrue(result.getBoolean(ENABLED_KEY));
            assertEquals("", result.getString("accountId"));
            assertTrue(result.has(PROVIDERS_KEY));
        }
    }

    @Test
    void configMiddlewareIncludesAllProviders() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<SystemInfo> systemInfoStatic = mockStatic(SystemInfo.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            Properties props = new Properties();
            props.setProperty(SSO_AUTH_TYPE_PROP, MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, MIDDLEWARE_URL_VALUE);
            props.setProperty(MIDDLEWARE_REDIRECT_PROP, TEST_REDIRECT_URI);

            mockProperties(providerStatic, props);
            systemInfoStatic.when(SystemInfo::getSystemIdentifier).thenReturn("acct");
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertEquals(5, result.getJSONArray(PROVIDERS_KEY).length());
            assertEquals("google-oauth2",
                result.getJSONArray(PROVIDERS_KEY).getJSONObject(0).getString("id"));
            assertEquals(TEST_REDIRECT_URI, result.getString("redirectUri"));
        }
    }

    // --- helpers for bearer token mocking ---

    private void setupValidBearerToken() {
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer valid-test-token");
    }

    private void mockValidBearer(MockedStatic<Utils> utilsStatic) {
        com.auth0.jwt.interfaces.DecodedJWT mockJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim mockClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(mockClaim.asString()).thenReturn("test-user-id");
        when(mockJwt.getClaim("user")).thenReturn(mockClaim);
        utilsStatic.when(() -> Utils.decodeToken("valid-test-token")).thenReturn(mockJwt);
    }
}
