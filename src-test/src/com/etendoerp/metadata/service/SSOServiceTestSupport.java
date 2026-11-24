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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import com.etendoerp.metadata.auth.Utils;

/**
 * Mocks, constants and helpers shared by the {@link SSOService} tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class SSOServiceTestSupport {

    protected static final String SSO_CONFIG_PATH = "/sso/config";
    protected static final String SSO_CALLBACK_PATH = "/sso/callback";
    protected static final String SSO_LINK_PATH = "/sso/link";
    protected static final String ENABLED_KEY = "enabled";
    protected static final String SSO_AUTH_TYPE_PROP = "sso.auth.type";
    protected static final String SSO_DOMAIN_URL_PROP = "sso.domain.url";
    protected static final String SSO_CLIENT_ID_PROP = "sso.client.id";
    protected static final String AUTH0_TYPE = "Auth0";
    protected static final String MIDDLEWARE_TYPE = "Middleware";
    protected static final String ERROR_KEY = "error";
    protected static final String INTERNAL_ERROR_CODE = "internal_error";
    protected static final String INVALID_REQUEST_ERROR = "invalid_request";
    protected static final String UNAUTHORIZED_ERROR = "unauthorized";
    protected static final String AUTHORIZATION_HEADER = "Authorization";
    protected static final String INVALID_TOKEN_ERROR = "invalid_token";
    protected static final String TOKEN_EXCHANGE_FAILED_ERROR = "token_exchange_failed";
    protected static final String MIDDLEWARE_URL_PROP = "sso.middleware.url";
    protected static final String MIDDLEWARE_REDIRECT_PROP = "sso.middleware.redirectUri";
    protected static final String MIDDLEWARE_URL_VALUE = "https://middleware:3000";
    protected static final String TEST_REDIRECT_URI = "https://localhost/callback";
    protected static final String PROVIDERS_KEY = "providers";
    protected static final String LOCALHOST_INVALID = "localhost.invalid";
    protected static final String SSO_USER_ID = "usr-123";
    protected static final String SSO_USERNAME = "testuser";
    protected static final String CODE_BODY = "{\"code\":\"test-code\"}";
    // Minimal valid JWT: header.payload.signature (signature is irrelevant for decode)
    protected static final String FAKE_JWT =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InRlc3QifQ"
        + ".eyJzdWIiOiJ0ZXN0fDEyMyJ9.fakesig";
    protected static final String FAKE_JWT_BODY = "{\"accessToken\":\"" + FAKE_JWT + "\"}";

    @Mock
    protected HttpServletRequest request;

    @Mock
    protected HttpServletResponse response;

    @Mock
    protected OBPropertiesProvider propertiesProvider;

    protected StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // --- shared helpers ---

    protected void mockProperties(MockedStatic<OBPropertiesProvider> providerStatic, Properties props) {
        providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
        when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);
    }

    protected void setupRequest(String path, String method) {
        when(request.getPathInfo()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
    }

    protected void setRequestBody(String body) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    protected JSONObject executeAndParse() throws Exception {
        new SSOService().handle(request, response);
        return new JSONObject(responseWriter.toString());
    }

    protected Properties propsWithAuthType(String authType) {
        Properties props = new Properties();
        props.setProperty(SSO_AUTH_TYPE_PROP, authType);
        return props;
    }

    protected Properties middlewareConfigProps() {
        Properties props = propsWithAuthType(MIDDLEWARE_TYPE);
        props.setProperty(MIDDLEWARE_URL_PROP, MIDDLEWARE_URL_VALUE);
        props.setProperty(MIDDLEWARE_REDIRECT_PROP, TEST_REDIRECT_URI);
        return props;
    }

    protected Properties auth0ExchangeProps(boolean includeSecret) {
        Properties props = propsWithAuthType(AUTH0_TYPE);
        props.setProperty(SSO_DOMAIN_URL_PROP, LOCALHOST_INVALID);
        props.setProperty(SSO_CLIENT_ID_PROP, "cid");
        if (includeSecret) {
            props.setProperty("sso.client.secret", "csecret");
        }
        return props;
    }

    protected void setupValidBearerToken() {
        when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn("Bearer valid-test-token");
    }

    protected void mockValidBearer(MockedStatic<Utils> utilsStatic) {
        com.auth0.jwt.interfaces.DecodedJWT mockJwt = mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        com.auth0.jwt.interfaces.Claim mockClaim = mock(com.auth0.jwt.interfaces.Claim.class);
        when(mockClaim.asString()).thenReturn("test-user-id");
        when(mockJwt.getClaim("user")).thenReturn(mockClaim);
        utilsStatic.when(() -> Utils.decodeToken("valid-test-token")).thenReturn(mockJwt);
    }

    protected void assertCallbackError(String body, String authType, String expectedError)
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

    protected void assertLinkError(String body, String authType, String expectedError)
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

    // --- deep path tests (spy bypasses JWKS validation) ---

    protected JSONObject spyExecuteAndParse() throws Exception {
        SSOService service = spy(new SSOService());
        doReturn(true).when(service).validateJwksToken(anyString(), any(), anyString());
        service.handle(request, response);
        return new JSONObject(responseWriter.toString());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected OBCriteria mockOBDalCriteria(MockedStatic<OBDal> obDalStatic) {
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
}
