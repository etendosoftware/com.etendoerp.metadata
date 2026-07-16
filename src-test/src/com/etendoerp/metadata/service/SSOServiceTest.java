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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SSOServiceTest {

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

    @Test
    void configReturnsDisabledWhenNoSSOConfigured() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);

            when(request.getPathInfo()).thenReturn("/sso/config");
            when(request.getMethod()).thenReturn("GET");

            SSOService service = new SSOService();
            service.handle(request, response);

            JSONObject result = new JSONObject(responseWriter.toString());
            assertFalse(result.getBoolean("enabled"));
        }
    }

    @Test
    void configReturnsAuth0Config() throws Exception {
        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty("sso.auth.type", "Auth0");
            props.setProperty("sso.domain.url", "etendo.auth0.com");
            props.setProperty("sso.client.id", "test-client-id");
            props.setProperty("sso.callback.url", "http://localhost:8080/etendo/callback");

            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);

            when(request.getPathInfo()).thenReturn("/sso/config");
            when(request.getMethod()).thenReturn("GET");

            SSOService service = new SSOService();
            service.handle(request, response);

            JSONObject result = new JSONObject(responseWriter.toString());
            assertTrue(result.getBoolean("enabled"));
            assertEquals("Auth0", result.getString("authType"));
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
            props.setProperty("sso.auth.type", "Middleware");
            props.setProperty("sso.middleware.url", "http://middleware:3000");
            props.setProperty("sso.middleware.redirectUri", "http://localhost:8080/etendo/saveToken");

            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);
            systemInfoStatic.when(SystemInfo::getSystemIdentifier).thenReturn("test-account-id");

            when(request.getPathInfo()).thenReturn("/sso/config");
            when(request.getMethod()).thenReturn("GET");

            SSOService service = new SSOService();
            service.handle(request, response);

            JSONObject result = new JSONObject(responseWriter.toString());
            assertTrue(result.getBoolean("enabled"));
            assertEquals("Middleware", result.getString("authType"));
            assertEquals("http://middleware:3000", result.getString("middlewareUrl"));
            assertEquals("test-account-id", result.getString("accountId"));
        }
    }

    @Test
    void callbackRejectsGetMethod() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/callback");
        when(request.getMethod()).thenReturn("GET");

        SSOService service = new SSOService();
        service.handle(request, response);

        verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    void callbackRejectsMissingAuthType() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/callback");
        when(request.getMethod()).thenReturn("POST");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);

            SSOService service = new SSOService();
            service.handle(request, response);

            verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void callbackRejectsMalformedJson() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/callback");
        when(request.getMethod()).thenReturn("POST");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("not json")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty("sso.auth.type", "Auth0");
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);

            SSOService service = new SSOService();
            service.handle(request, response);

            JSONObject result = new JSONObject(responseWriter.toString());
            assertEquals("invalid_request", result.getString("error"));
        }
    }

    @Test
    void callbackRejectsMissingCodeForAuth0() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/callback");
        when(request.getMethod()).thenReturn("POST");
        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader("{\"authType\":\"Auth0\"}")));

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            Properties props = new Properties();
            props.setProperty("sso.auth.type", "Auth0");
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
            when(propertiesProvider.getOpenbravoProperties()).thenReturn(props);

            SSOService service = new SSOService();
            service.handle(request, response);

            JSONObject result = new JSONObject(responseWriter.toString());
            assertEquals("invalid_request", result.getString("error"));
        }
    }

    @Test
    void unknownPathReturns404() throws Exception {
        when(request.getPathInfo()).thenReturn("/sso/unknown");

        SSOService service = new SSOService();
        service.handle(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
