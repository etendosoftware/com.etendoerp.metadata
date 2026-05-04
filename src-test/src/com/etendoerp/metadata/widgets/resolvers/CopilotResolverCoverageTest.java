/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CopilotResolverCoverageTest {

    private static final String MESSAGES = "messages";

    @FunctionalInterface
    private interface ThrowingRunnable {
        @SuppressWarnings("java:S112")
        void run() throws Exception;
    }

    private CopilotResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CopilotResolver();
    }

    /**
     * Sets up OBDal/Session/NativeQuery mocks with the given query result,
     * then executes the assertion within a MockedStatic scope.
     */
    private void withDalQueryReturning(Object queryResult, Consumer<CopilotResolver> assertion) {
        OBDal mockDal = mock(OBDal.class);
        Session mockSession = mock(Session.class);
        NativeQuery<?> mockQuery = mock(NativeQuery.class);
        when(mockDal.getSession()).thenReturn(mockSession);
        when(mockSession.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.uniqueResult()).thenReturn(queryResult);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            assertion.accept(resolver);
        }
    }

    /**
     * Configures the WidgetDataContext with OBContext user/client mocks.
     */
    private void setupCtxWithObContext(WidgetDataContext ctx, String userId, String clientId) {
        OBContext mockObContext = mock(OBContext.class);
        User mockUser = mock(User.class);
        Client mockOBClient = mock(Client.class);
        when(ctx.getObContext()).thenReturn(mockObContext);
        when(mockObContext.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(userId);
        when(mockObContext.getCurrentClient()).thenReturn(mockOBClient);
        when(mockOBClient.getId()).thenReturn(clientId);
    }

    /**
     * Sets up HTTP client mocks for a given response body and executes the
     * assertion within a MockedStatic scope for HttpClients.
     */
    private void withHttpPostReturning(String responseBody, ThrowingRunnable assertion) throws Exception {
        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(
                new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
        when(mockEntity.getContentLength()).thenReturn((long) responseBody.length());

        try (MockedStatic<HttpClients> httpMock = mockStatic(HttpClients.class)) {
            httpMock.when(HttpClients::createDefault).thenReturn(mockHttpClient);
            assertion.run();
        }
    }

    @Test
    void getTypeReturnsCopilot() {
        assertEquals("COPILOT", resolver.getType());
    }

    @Test
    void isAvailableReturnsTrueWhenCopilotModuleExists() {
        withDalQueryReturning(1L, r -> assertTrue(r.isAvailable()));
    }

    @Test
    void isAvailableReturnsFalseWhenCountIsZero() {
        withDalQueryReturning(0L, r -> assertFalse(r.isAvailable()));
    }

    @Test
    void isAvailableReturnsFalseWhenCountIsNull() {
        withDalQueryReturning(null, r -> assertFalse(r.isAvailable()));
    }

    @Test
    void resolveWithNullUrlReturnsEmptyMessages() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn(null);

        JSONObject result = resolver.resolve(ctx);
        assertTrue(result.has(MESSAGES));
        assertEquals(0, result.getJSONArray(MESSAGES).length());
    }

    @Test
    void resolveWithValidUrlSendsPostAndReturnsJsonResponse() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("http://copilot.example.com/api/suggest");
        when(ctx.getBearerToken()).thenReturn("Bearer test-token-123");
        setupCtxWithObContext(ctx, "user-1", "client-1");

        String responseBody = "{\"messages\":[{\"role\":\"assistant\",\"content\":\"Hello\"}]}";

        withHttpPostReturning(responseBody, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertTrue(result.has(MESSAGES));
            assertEquals(1, result.getJSONArray(MESSAGES).length());
            assertEquals("Hello",
                    result.getJSONArray(MESSAGES).getJSONObject(0).getString("content"));
        });
    }

    @Test
    void resolveWithValidUrlAndNullTokenOmitsAuthHeader() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("http://copilot.example.com/api/suggest");
        when(ctx.getBearerToken()).thenReturn(null);
        setupCtxWithObContext(ctx, "user-2", "client-2");

        String responseBody = "{\"status\":\"ok\"}";

        withHttpPostReturning(responseBody, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals("ok", result.getString("status"));
        });
    }
}
