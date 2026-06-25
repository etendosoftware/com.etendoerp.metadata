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
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ProxyResolverTest {

    @Test
    void getTypeReturnsProxy() {
        assertEquals("PROXY", new ProxyResolver().getType());
    }

    @Test
    void resolveWithNullUrlReturnsUnavailable() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn(null);

        JSONObject result = new ProxyResolver().resolve(ctx);
        assertFalse(result.getBoolean("available"));
        assertEquals("no_external_url", result.getString("reason"));
    }

    @Test
    void resolveWithValidUrlReturnsJsonResponse() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("http://example.com/api/data");
        when(ctx.getBearerToken()).thenReturn("Bearer tok123");

        String jsonBody = "{\"value\":42}";

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);

        when(mockClient.execute(any(HttpGet.class))).thenReturn(mockResponse);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(
                new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        when(mockEntity.getContentLength()).thenReturn((long) jsonBody.length());

        try (MockedStatic<HttpClients> httpMock = mockStatic(HttpClients.class)) {
            httpMock.when(HttpClients::createDefault).thenReturn(mockClient);

            JSONObject result = new ProxyResolver().resolve(ctx);
            assertEquals(42, result.getInt("value"));
        }
    }

    @Test
    void resolveWithNonJsonResponseWrapsAsResult() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("http://example.com/api/text");
        when(ctx.getBearerToken()).thenReturn(null);

        String plainBody = "plain text response";

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);

        when(mockClient.execute(any(HttpGet.class))).thenReturn(mockResponse);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getContent()).thenReturn(
                new ByteArrayInputStream(plainBody.getBytes(StandardCharsets.UTF_8)));
        when(mockEntity.getContentLength()).thenReturn((long) plainBody.length());

        try (MockedStatic<HttpClients> httpMock = mockStatic(HttpClients.class)) {
            httpMock.when(HttpClients::createDefault).thenReturn(mockClient);

            JSONObject result = new ProxyResolver().resolve(ctx);
            assertEquals(plainBody, result.getString("result"));
        }
    }
}
