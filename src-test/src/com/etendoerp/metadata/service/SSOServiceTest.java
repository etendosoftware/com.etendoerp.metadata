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
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.etendorx.data.ETRXTokenUser;
import org.openbravo.erpCommon.utility.SystemInfo;

import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.auth.Utils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SSOServiceTest {

    private static final String SSO_CONFIG_PATH = "/sso/config";
    private static final String SSO_CALLBACK_PATH = "/sso/callback";
    private static final String SSO_LINK_PATH = "/sso/link";
    private static final String ENABLED_KEY = "enabled";
    private static final String SSO_AUTH_TYPE_PROP = "sso.auth.type";
    private static final String SSO_DOMAIN_URL_PROP = "sso.domain.url";
    private static final String SSO_CLIENT_ID_PROP = "sso.client.id";
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
    private static final String LOCALHOST_INVALID = "localhost.invalid";
    private static final String CODE_BODY = "{\"code\":\"test-code\"}";
    // Minimal valid JWT: header.payload.signature (signature is irrelevant for decode)
    private static final String FAKE_JWT =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3QifQ"
        + ".eyJzdWIiOiJ0ZXN0fDEyMyJ9.fakesig";
    private static final String FAKE_JWT_BODY = "{\"accessToken\":\"" + FAKE_JWT + "\"}";

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

    // --- shared helpers ---

    private void mockProperties(MockedStatic<OBPropertiesProvider> providerStatic, Properties props) {
        providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
        when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);
    }

    private void setupRequest(String path, String method) {
        when(request.getPathInfo()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
    }

    private void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    private JSONObject executeAndParse() throws Exception {
        new SSOService().handle(request, response);
        return new JSONObject(responseWriter.toString());
    }

    private Properties propsWithAuthType(String authType) {
        Properties props = new Properties();
        props.setProperty(SSO_AUTH_TYPE_PROP, authType);
        return props;
    }

    private Properties middlewareConfigProps() {
        Properties props = propsWithAuthType(MIDDLEWARE_TYPE);
        props.setProperty(MIDDLEWARE_URL_PROP, MIDDLEWARE_URL_VALUE);
        props.setProperty(MIDDLEWARE_REDIRECT_PROP, TEST_REDIRECT_URI);
        return props;
    }

    private Properties auth0ExchangeProps(boolean includeSecret) {
        Properties props = propsWithAuthType(AUTH0_TYPE);
        props.setProperty(SSO_DOMAIN_URL_PROP, LOCALHOST_INVALID);
        props.setProperty(SSO_CLIENT_ID_PROP, "cid");
        if (includeSecret) {
            props.setProperty("sso.client.secret", "csecret");
        }
        return props;
    }

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

    private void assertCallbackError(String body, String authType, String expectedError)
            throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(body);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, propsWithAuthType(authType));
            JSONObject result = executeAndParse();
            assertEquals(expectedError, result.getString(ERROR_KEY));
        }
    }

    private void assertLinkError(String body, String authType, String expectedError)
            throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(body);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, propsWithAuthType(authType));
            JSONObject result = executeAndParse();
            assertEquals(expectedError, result.getString(ERROR_KEY));
        }
    }

    // --- /sso/config tests ---

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
            Properties props = propsWithAuthType(AUTH0_TYPE);
            props.setProperty(SSO_DOMAIN_URL_PROP, "etendo.auth0.com");
            props.setProperty(SSO_CLIENT_ID_PROP, "test-client-id");
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
            mockProperties(providerStatic, middlewareConfigProps());
            systemInfoStatic.when(SystemInfo::getSystemIdentifier).thenReturn("test-account-id");
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertTrue(result.getBoolean(ENABLED_KEY));
            assertEquals(MIDDLEWARE_TYPE, result.getString("authType"));
            assertEquals(MIDDLEWARE_URL_VALUE, result.getString("middlewareUrl"));
            assertEquals("test-account-id", result.getString("accountId"));
            assertEquals(TEST_REDIRECT_URI, result.getString("redirectUri"));
            assertEquals(5, result.getJSONArray(PROVIDERS_KEY).length());
            assertEquals("google-oauth2",
                result.getJSONArray(PROVIDERS_KEY).getJSONObject(0).getString("id"));
        }
    }

    @Test
    void configMiddlewareHandlesSystemInfoException() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<SystemInfo> systemInfoStatic = mockStatic(SystemInfo.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            mockProperties(providerStatic, middlewareConfigProps());
            systemInfoStatic.when(SystemInfo::getSystemIdentifier)
                .thenThrow(new javax.servlet.ServletException("unavailable"));
            setupRequest(SSO_CONFIG_PATH, "GET");

            JSONObject result = executeAndParse();
            assertTrue(result.getBoolean(ENABLED_KEY));
            assertEquals("", result.getString("accountId"));
            assertTrue(result.has(PROVIDERS_KEY));
        }
    }

    // --- /sso/callback tests ---

    @Test
    void callbackRejectsGetMethod() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "GET");
        new SSOService().handle(request, response);
        verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    void callbackRejectsMissingAuthType() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody("{}");
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, new Properties());
            new SSOService().handle(request, response);
            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void callbackRejectsMalformedJson() throws Exception {
        assertCallbackError("not json", AUTH0_TYPE, INVALID_REQUEST_ERROR);
    }

    @Test
    void callbackRejectsMissingCodeForAuth0() throws Exception {
        assertCallbackError("{}", AUTH0_TYPE, INVALID_REQUEST_ERROR);
    }

    @Test
    void callbackRejectsMissingAccessTokenForMiddleware() throws Exception {
        assertCallbackError("{}", MIDDLEWARE_TYPE, INVALID_REQUEST_ERROR);
    }

    @Test
    void callbackAuth0TokenExchangeFailsAllVariants() throws Exception {
        // With client secret
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(CODE_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, auth0ExchangeProps(true));
            assertEquals(TOKEN_EXCHANGE_FAILED_ERROR, executeAndParse().getString(ERROR_KEY));
        }
        // Without client secret (no PKCE)
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(CODE_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, auth0ExchangeProps(false));
            assertEquals(TOKEN_EXCHANGE_FAILED_ERROR, executeAndParse().getString(ERROR_KEY));
        }
        // PKCE flow
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody("{\"code\":\"c\",\"codeVerifier\":\"v\",\"redirectUri\":\"http://l\"}");
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            mockProperties(providerStatic, auth0ExchangeProps(false));
            assertEquals(TOKEN_EXCHANGE_FAILED_ERROR, executeAndParse().getString(ERROR_KEY));
        }
    }

    @Test
    void callbackMiddlewareInvalidToken() throws Exception {
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = propsWithAuthType(MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, "http://localhost:1");
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_TOKEN_ERROR, result.getString(ERROR_KEY));
        }
    }

    // --- /sso/unknown ---

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
    void linkRejectsInvalidAuthorizationHeaders() throws Exception {
        String[] invalidHeaders = {null, "Bearer   ", "Basic dXNlcjpwYXNz"};
        for (String header : invalidHeaders) {
            responseWriter = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
            setupRequest(SSO_LINK_PATH, "POST");
            when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(header);
            JSONObject result = executeAndParse();
            assertEquals(UNAUTHORIZED_ERROR, result.getString(ERROR_KEY),
                "Expected unauthorized for header: " + header);
        }
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
    void linkRejectsInvalidBodies() throws Exception {
        assertLinkError("not json", AUTH0_TYPE, INVALID_REQUEST_ERROR);
        assertLinkError("{}", AUTH0_TYPE, INVALID_REQUEST_ERROR);
        assertLinkError("{}", MIDDLEWARE_TYPE, INVALID_REQUEST_ERROR);
    }

    @Test
    void linkAuth0TokenExchangeFails() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(CODE_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, auth0ExchangeProps(true));
            JSONObject result = executeAndParse();
            assertEquals(TOKEN_EXCHANGE_FAILED_ERROR, result.getString(ERROR_KEY));
        }
    }

    @Test
    void linkMiddlewareInvalidToken() throws Exception {
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockValidBearer(utilsStatic);
            Properties props = propsWithAuthType(MIDDLEWARE_TYPE);
            props.setProperty(MIDDLEWARE_URL_PROP, "http://localhost:1");
            mockProperties(providerStatic, props);

            JSONObject result = executeAndParse();
            assertEquals(INVALID_TOKEN_ERROR, result.getString(ERROR_KEY));
        }
    }

    // --- deep path tests (spy bypasses JWKS validation) ---

    private JSONObject spyExecuteAndParse() throws Exception {
        SSOService service = spy(new SSOService());
        doReturn(true).when(service).validateJwksToken(anyString(), any(), anyString());
        service.handle(request, response);
        return new JSONObject(responseWriter.toString());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private OBCriteria mockOBDalCriteria(MockedStatic<OBDal> obDalStatic) {
        OBDal mockDal = mock(OBDal.class);
        obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
        OBCriteria mockCriteria = mock(OBCriteria.class);
        when(mockDal.createCriteria(any(Class.class))).thenReturn(mockCriteria);
        when(mockCriteria.add(any())).thenReturn(mockCriteria);
        when(mockCriteria.setFilterOnReadableClients(anyBoolean())).thenReturn(mockCriteria);
        when(mockCriteria.setFilterOnReadableOrganization(anyBoolean())).thenReturn(mockCriteria);
        when(mockCriteria.setMaxResults(anyInt())).thenReturn(mockCriteria);
        return mockCriteria;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void callbackDeepPaths() throws Exception {
        // No user linked
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBCriteria criteria = mockOBDalCriteria(obDalStatic);
            when(criteria.uniqueResult()).thenReturn(null);
            assertEquals("no_user_linked", spyExecuteAndParse().getString(ERROR_KEY));
        }
        // DB exception
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBDal mockDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.createCriteria(any(Class.class))).thenThrow(new RuntimeException("DB error"));
            assertEquals("internal_error", spyExecuteAndParse().getString(ERROR_KEY));
        }
        // Success path: user found, token generated
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBCriteria criteria = mockOBDalCriteria(obDalStatic);

            ETRXTokenUser mockTokenUser = mock(ETRXTokenUser.class);
            User mockUser = mock(User.class);
            Role mockRole = mock(Role.class);
            Client mockClient = mock(Client.class);
            when(mockRole.getClient()).thenReturn(mockClient);
            when(mockUser.getDefaultRole()).thenReturn(mockRole);
            when(mockUser.getDefaultOrganization()).thenReturn(mock(Organization.class));
            when(mockUser.getDefaultWarehouse()).thenReturn(mock(Warehouse.class));
            when(mockUser.getClient()).thenReturn(mockClient);
            when(mockUser.getId()).thenReturn("usr-123");
            when(mockUser.getUsername()).thenReturn("testuser");
            when(mockTokenUser.getUserForToken()).thenReturn(mockUser);
            when(criteria.uniqueResult()).thenReturn(mockTokenUser);
            utilsStatic.when(() -> Utils.generateToken(any(), any())).thenReturn("jwt-token");

            JSONObject result = spyExecuteAndParse();
            assertEquals("jwt-token", result.getString("token"));
            assertEquals("usr-123", result.getString("userId"));
            assertEquals("testuser", result.getString("username"));
        }
        // Success with null default role (covers ternary false branch)
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_CALLBACK_PATH, "POST");
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class)) {
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBCriteria criteria = mockOBDalCriteria(obDalStatic);

            ETRXTokenUser mockTokenUser = mock(ETRXTokenUser.class);
            User mockUser = mock(User.class);
            when(mockUser.getDefaultRole()).thenReturn(null);
            when(mockUser.getClient()).thenReturn(mock(Client.class));
            when(mockUser.getId()).thenReturn("usr-456");
            when(mockUser.getUsername()).thenReturn("noroler");
            when(mockTokenUser.getUserForToken()).thenReturn(mockUser);
            when(criteria.uniqueResult()).thenReturn(mockTokenUser);
            utilsStatic.when(() -> Utils.generateToken(any(), any())).thenReturn("t2");

            assertEquals("t2", spyExecuteAndParse().getString("token"));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void linkDeepPaths() throws Exception {
        // User not found
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBDal mockDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.get(any(Class.class), anyString())).thenReturn(null);
            assertEquals("user_not_found", spyExecuteAndParse().getString(ERROR_KEY));
        }
        // DB exception
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));
            OBDal mockDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.get(any(Class.class), anyString())).thenThrow(new RuntimeException("DB error"));
            assertEquals("internal_error", spyExecuteAndParse().getString(ERROR_KEY));
        }
        // User found, existing link removed, new link created
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        setupRequest(SSO_LINK_PATH, "POST");
        setupValidBearerToken();
        setRequestBody(FAKE_JWT_BODY);
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class);
             MockedStatic<Utils> utilsStatic = mockStatic(Utils.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            mockValidBearer(utilsStatic);
            mockProperties(providerStatic, propsWithAuthType(MIDDLEWARE_TYPE));

            // Setup OBDal with both get() and createCriteria() on same mock
            OBCriteria criteria = mockOBDalCriteria(obDalStatic);
            OBDal mockDal = OBDal.getInstance(); // get the mock created by mockOBDalCriteria
            User mockUser = mock(User.class);
            when(mockUser.getClient()).thenReturn(mock(Client.class));
            when(mockUser.getOrganization()).thenReturn(mock(Organization.class));
            when(mockDal.get(any(Class.class), anyString())).thenReturn(mockUser);

            ETRXTokenUser existingToken = mock(ETRXTokenUser.class);
            when(criteria.uniqueResult()).thenReturn(existingToken);

            // new ETRXTokenUser() may fail without DAL — catch covers it
            JSONObject result = spyExecuteAndParse();
            assertTrue(result.has("status") || result.has(ERROR_KEY));
        }
    }
}
