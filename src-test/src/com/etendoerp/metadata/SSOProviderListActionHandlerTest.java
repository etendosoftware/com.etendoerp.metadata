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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Consumer;

import javax.servlet.ServletException;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.SystemInfo;

import com.etendoerp.etendorx.data.ETRXoAuthProvider;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests for {@link SSOProviderListActionHandler}.
 * <p>
 * The request paths replace the outbound call by overriding
 * {@link SSOProviderListActionHandler#fetchAvailableProviders(String)}, so every branch of
 * {@code execute} is exercised without network access. That method has its own two tests, which
 * answer it from a throw-away HTTP server bound to the loopback address — it is the only way to
 * cover the connection handling, and it still contacts nothing outside the machine.
 */
@ExtendWith(MockitoExtension.class)
class SSOProviderListActionHandlerTest {

    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String ERROR_CODE_KEY = "errorCode";
    private static final String SUCCESS_STATUS = "Success";
    private static final String ERROR_STATUS = "Error";
    private static final String PARAMS_KEY = "_params";
    private static final String PROVIDER_ID_KEY = "etrxOauthProviderId";
    private static final String PROVIDER_ID = "0DF0EFEF0F1F4DEEA25F4DEC5B30969D";
    private static final String ENDPOINT = "https://sso.example.test/oauth-integrations";
    private static final String REDIRECT_URI = "http://localhost:8080/etendo/saveTokenMiddleware";
    private static final String PROVIDERS_KEY = "providers";
    private static final String START_ENDPOINT_KEY = "startEndpoint";
    private static final String ACCOUNT_ID = "c45c4946-714a-4e2d-8e30-5944fe2e3533";

    /** A trimmed-down copy of what the middleware actually publishes. */
    private static final String MIDDLEWARE_PAYLOAD = "{\"google\":{\"name\":\"google\",\"scopes\":["
            + "{\"name\":\"Google Drive - Edit Access Level\","
            + "\"scope\":\"https://www.googleapis.com/auth/drive.file\","
            + "\"description\":\"Allows you to upload and manage files.\"}]}}";

    /**
     * Handler whose outbound call is stubbed: it either returns a canned body or raises the given
     * failure, so the parsing and error branches can be reached directly.
     */
    private static class StubHandler extends SSOProviderListActionHandler {
        private final String body;
        private final IOException failure;
        private String requestedUrl;

        StubHandler(String body, IOException failure) {
            this.body = body;
            this.failure = failure;
        }

        @Override
        String fetchAvailableProviders(String url) throws IOException {
            this.requestedUrl = url;
            if (failure != null) {
                throw failure;
            }
            return body;
        }

        /** Avoids the static AD singletons; the identifier itself is not under test here. */
        @Override
        String getAccountId() {
            return ACCOUNT_ID;
        }
    }

    /** Publishes no record, for the paths that must fail before one is ever read. */
    private static final Consumer<OBDal> NO_PROVIDER = obDal -> {
        // Intentionally empty: an unstubbed OBDal.get answers null, which is the state under test.
    };

    private JSONObject content(String providerId) throws JSONException {
        JSONObject params = new JSONObject();
        params.put(PROVIDER_ID_KEY, providerId);
        JSONObject root = new JSONObject();
        root.put(PARAMS_KEY, params);
        return root;
    }

    private ETRXoAuthProvider mockProvider(OBDal obDal, String authorizationEndpoint) {
        ETRXoAuthProvider provider = mock(ETRXoAuthProvider.class);
        when(provider.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
        when(obDal.get(ETRXoAuthProvider.class, PROVIDER_ID)).thenReturn(provider);
        return provider;
    }

    /**
     * Publishes a record whose redirect URI is readable too. Only the success path reaches that
     * getter, so the error paths deliberately use {@link #mockProvider} instead: stubbing it there
     * would be an unnecessary stubbing and strict stubs would fail the test.
     */
    private void mockCompleteProvider(OBDal obDal, String authorizationEndpoint) {
        when(mockProvider(obDal, authorizationEndpoint).getRedirectURI()).thenReturn(REDIRECT_URI);
    }

    /**
     * Runs the handler with the DAL singleton stubbed, publishing whatever record the case needs.
     *
     * @param handler    the handler under test
     * @param providerId the identifier travelling in the request
     * @param dalSetup   populates the stubbed DAL before the call
     * @return the handler response
     * @throws JSONException if the request payload cannot be built
     */
    private JSONObject run(StubHandler handler, String providerId, Consumer<OBDal> dalSetup)
            throws JSONException {
        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            dalSetup.accept(obDal);
            return handler.execute(Collections.emptyMap(), content(providerId).toString());
        }
    }

    /**
     * A well-formed request returns the catalogue verbatim plus the derived start endpoint.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void returnsProvidersAndDerivedStartEndpoint() throws JSONException {
        StubHandler handler = new StubHandler(MIDDLEWARE_PAYLOAD, null);

        JSONObject result = run(handler, PROVIDER_ID, obDal -> mockCompleteProvider(obDal, ENDPOINT));

        assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
        assertEquals(ENDPOINT + SSOProviderListActionHandler.START_PATH,
                result.getString(START_ENDPOINT_KEY));
        assertEquals(REDIRECT_URI, result.getString("redirectUri"));
        assertEquals(ACCOUNT_ID, result.getString("accountId"));
        assertTrue(result.getJSONObject(PROVIDERS_KEY).has("google"));
        assertEquals(ENDPOINT + SSOProviderListActionHandler.AVAILABLE_PROVIDERS_PATH,
                handler.requestedUrl);
    }

    /**
     * A trailing slash on the stored endpoint must not produce a doubled separator.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void normalizesTrailingSlashInEndpoint() throws JSONException {
        StubHandler handler = new StubHandler(MIDDLEWARE_PAYLOAD, null);

        run(handler, PROVIDER_ID, obDal -> mockCompleteProvider(obDal, ENDPOINT + "/"));

        assertEquals(ENDPOINT + SSOProviderListActionHandler.AVAILABLE_PROVIDERS_PATH,
                handler.requestedUrl);
    }

    /**
     * A missing provider id is reported as a business error, not raised.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void reportsErrorWhenProviderIdMissing() throws JSONException {
        JSONObject result = run(new StubHandler(MIDDLEWARE_PAYLOAD, null), "", NO_PROVIDER);

        assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
        assertEquals(SSOProviderListActionHandler.ERROR_NO_PROVIDER_ID, result.getString(ERROR_CODE_KEY));
        assertFalse(result.getString(MESSAGE_KEY).isEmpty());
    }

    /**
     * An id that matches no record is reported rather than throwing a null pointer downstream.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void reportsErrorWhenProviderNotFound() throws JSONException {
        JSONObject result = run(new StubHandler(MIDDLEWARE_PAYLOAD, null), PROVIDER_ID, NO_PROVIDER);

        assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
        assertEquals(SSOProviderListActionHandler.ERROR_PROVIDER_NOT_FOUND, result.getString(ERROR_CODE_KEY));
    }

    /**
     * Authorization_Endpoint is an optional column, so a blank one is normal data and must produce a
     * readable message instead of a malformed URL.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void reportsErrorWhenEndpointBlank() throws JSONException {
        StubHandler handler = new StubHandler(MIDDLEWARE_PAYLOAD, null);

        JSONObject result = run(handler, PROVIDER_ID, obDal -> mockProvider(obDal, "  "));

        assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
        // The code is what the caller translates; the English message is only a diagnostic.
        assertEquals(SSOProviderListActionHandler.ERROR_NO_ENDPOINT, result.getString(ERROR_CODE_KEY));
    }

    /**
     * A non-200 answer from the middleware surfaces as an error the caller can display.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void reportsErrorWhenMiddlewareFails() throws JSONException {
        StubHandler handler = new StubHandler(null, new IOException("The middleware answered HTTP 503"));

        JSONObject result = run(handler, PROVIDER_ID, obDal -> mockProvider(obDal, ENDPOINT));

        assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
        assertEquals(SSOProviderListActionHandler.ERROR_UNREACHABLE, result.getString(ERROR_CODE_KEY));
        assertTrue(result.getString(MESSAGE_KEY).contains("503"));
    }

    /**
     * A body that is not JSON must not escape as a raw parser exception.
     *
     * @throws JSONException if the payloads cannot be built or read
     */
    @Test
    void reportsErrorWhenPayloadUnreadable() throws JSONException {
        StubHandler handler = new StubHandler("<html>gateway error</html>", null);

        JSONObject result = run(handler, PROVIDER_ID, obDal -> mockProvider(obDal, ENDPOINT));

        assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
        assertEquals(SSOProviderListActionHandler.ERROR_UNREADABLE_RESPONSE, result.getString(ERROR_CODE_KEY));
    }

    /**
     * The account id is the instance's System Identifier, read under admin mode and trimmed. The
     * mode must be restored, since leaking it would let the rest of the request bypass access
     * control.
     */
    @Test
    void readsTheSystemIdentifierAsAccountId() {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<SystemInfo> systemInfoMock = mockStatic(SystemInfo.class)) {
            systemInfoMock.when(SystemInfo::getSystemIdentifier).thenReturn("  " + ACCOUNT_ID + "  ");

            assertEquals(ACCOUNT_ID, new SSOProviderListActionHandler().getAccountId());

            contextMock.verify(() -> OBContext.setAdminMode(true));
            contextMock.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * An unreadable identifier downgrades to an empty account id instead of failing the whole call:
     * the chooser is still worth rendering, and the middleware is the one that judges the account.
     * The admin mode was entered before the failure, so it must still be restored.
     */
    @Test
    void reportsEmptyAccountIdWhenTheSystemIdentifierCannotBeRead() {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<SystemInfo> systemInfoMock = mockStatic(SystemInfo.class)) {
            systemInfoMock.when(SystemInfo::getSystemIdentifier)
                    .thenThrow(new ServletException("No database connection"));

            assertEquals("", new SSOProviderListActionHandler().getAccountId());

            contextMock.verify(OBContext::restorePreviousMode);
        }
    }

    /**
     * A 200 answer is returned verbatim, so the caller parses exactly what the middleware published.
     *
     * @throws IOException if the stub server cannot be started or the read fails
     */
    @Test
    void fetchReturnsTheBodyWhenTheMiddlewareAnswersOk() throws IOException {
        HttpServer server = startMiddlewareStub(HttpURLConnection.HTTP_OK, MIDDLEWARE_PAYLOAD);
        try {
            assertEquals(MIDDLEWARE_PAYLOAD,
                    new SSOProviderListActionHandler().fetchAvailableProviders(stubUrl(server)));
        } finally {
            server.stop(0);
        }
    }

    /**
     * A non-200 answer is raised with its status in the message, which is what {@code execute} turns
     * into the "unreachable" business error the user finally sees.
     *
     * @throws IOException if the stub server cannot be started
     */
    @Test
    void fetchRaisesWhenTheMiddlewareAnswersAnError() throws IOException {
        HttpServer server = startMiddlewareStub(HttpURLConnection.HTTP_UNAVAILABLE, "service unavailable");
        try {
            SSOProviderListActionHandler handler = new SSOProviderListActionHandler();
            String url = stubUrl(server);

            IOException failure = assertThrows(IOException.class, () -> handler.fetchAvailableProviders(url));

            assertTrue(failure.getMessage().contains(String.valueOf(HttpURLConnection.HTTP_UNAVAILABLE)));
        } finally {
            server.stop(0);
        }
    }

    /**
     * Starts a throw-away HTTP server on a free loopback port, answering the available-providers
     * path with the given status and body. Nothing outside the machine is contacted.
     *
     * @param status the HTTP status to answer
     * @param body   the response body, never empty so the content length is always explicit
     * @return the started server; the caller stops it
     * @throws IOException if the server cannot be bound
     */
    private HttpServer startMiddlewareStub(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(SSOProviderListActionHandler.AVAILABLE_PROVIDERS_PATH, exchange -> {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    /**
     * Builds the URL the handler would have derived from a provider record pointing at the stub.
     *
     * @param server the started stub server
     * @return the absolute URL of the available-providers path
     */
    private String stubUrl(HttpServer server) {
        InetSocketAddress address = server.getAddress();
        return "http://" + address.getHostString() + ":" + address.getPort()
                + SSOProviderListActionHandler.AVAILABLE_PROVIDERS_PATH;
    }
}
