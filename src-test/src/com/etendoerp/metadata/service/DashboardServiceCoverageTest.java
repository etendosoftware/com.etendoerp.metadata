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

package com.etendoerp.metadata.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.widgets.DashboardLayoutResolver;
import com.etendoerp.metadata.widgets.WidgetResolverRegistry;
import com.etendoerp.metadata.widgets.WidgetResolverRegistryHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceCoverageTest {

    private static final String CLIENT1 = "client1";
    private static final String HQL_CLASS_ID = "dw.widgetClass.id = :classId";
    private static final String PATH_LAYOUT = "/dashboard/layout";
    private static final String WIDGET_CLASS_ID = "widgetClassId";
    private static final String INSTANCE_ID = "instanceId";
    private static final String HEIGHT = "height";
    private static final String WIDTH = "width";
    private static final String WIDGETS = "widgets";
    private static final String PATH_WIDGET = "/dashboard/widget";
    private static final String CREATED = "created";
    private static final String PARAMETERS = "parameters";
    private static final String METHOD_DELETE = "DELETE";
    private static final String HQL_SELECT_LAYER = "select dw.layer";
    private static final String METHOD_PATCH = "PATCH";
    private static final String VALUE = "value";
    private static final String HQL_PARAMS_JSON = "dw.parametersJSON = :params";
    private static final String HQL_ID = "dw.id = :id";
    private static final String HQL_COL_ROW = "dw.columnPosition, dw.rowPosition";

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock OBContext obContext;
    @Mock OBDal obDal;
    @Mock Session session;
    @Mock Role role;
    @Mock User user;
    @Mock Client client;

    // --- helpers ---

    private void stubCommon(MockedStatic<OBContext> ctxStatic, MockedStatic<OBDal> dalStatic) {
        ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
        dalStatic.when(OBDal::getInstance).thenReturn(obDal);
        when(obDal.getSession()).thenReturn(session);

        when(obContext.getUser()).thenReturn(user);
        when(obContext.getRole()).thenReturn(role);
        when(obContext.getCurrentClient()).thenReturn(client);
        when(user.getId()).thenReturn("user1");
        when(role.getId()).thenReturn("role1");
        when(client.getId()).thenReturn(CLIENT1);
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> mockEnrichQuery() {
        Query<Object[]> enrichQuery = mock(Query.class);
        when(session.createQuery((String) argThat(s -> s != null && ((String) s).contains("etmeta_Widget_Class")), eq(Object[].class)))
                .thenReturn(enrichQuery);
        when(enrichQuery.setParameter(anyString(), any())).thenReturn(enrichQuery);
        when(enrichQuery.uniqueResult()).thenReturn(null);
        return enrichQuery;
    }

    @SuppressWarnings("unchecked")
    private Query<Object> mockHqlQuery() {
        Query<Object> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.executeUpdate()).thenReturn(1);
        return q;
    }

    @SuppressWarnings("unchecked")
    private NativeQuery<Object> mockNativeQuery() {
        NativeQuery<Object> nq = mock(NativeQuery.class);
        doReturn(nq).when(session).createNativeQuery(anyString());
        when(nq.setParameter(anyString(), any())).thenReturn(nq);
        when(nq.executeUpdate()).thenReturn(1);
        return nq;
    }

    @SuppressWarnings("unchecked")
    private void mockDeleteShadowQuery() {
        Query<Object> deleteQ = mock(Query.class);
        doReturn(deleteQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("delete from etmeta_Dashboard_Widget")));
        when(deleteQ.setParameter(anyString(), any())).thenReturn(deleteQ);
        when(deleteQ.executeUpdate()).thenReturn(0);
    }

    @SuppressWarnings("unchecked")
    private void mockExistsCountQuery(long count) {
        Query<Long> existsQ = mock(Query.class);
        doReturn(existsQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("select count(dw)")),
                eq(Long.class));
        when(existsQ.setParameter(anyString(), any())).thenReturn(existsQ);
        when(existsQ.uniqueResult()).thenReturn(count);
    }

    private void mockPostWidgetQueries(long existsCount) {
        mockDeleteShadowQuery();
        mockExistsCountQuery(existsCount);
    }

    @SuppressWarnings("unchecked")
    private void mockUpdateLayoutQuery(int updateCount) {
        Query<Object> updateQ = mock(Query.class);
        doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("update etmeta_Dashboard_Widget dw set")
                && ((String) s).contains("dw.columnPosition")));
        when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
        when(updateQ.executeUpdate()).thenReturn(updateCount);
    }

    @SuppressWarnings("unchecked")
    private void mockLookupSourceQuery(Object[] result) {
        Query<Object[]> lookupQ = mock(Query.class);
        doReturn(lookupQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("select dw.widgetClass.id, dw.client.id, dw.sequence, dw.parametersJSON")),
                eq(Object[].class));
        when(lookupQ.setParameter(anyString(), any())).thenReturn(lookupQ);
        when(lookupQ.uniqueResult()).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void mockUserOverrideQuery(int updateCount) {
        Query<Object> userUpdateQ = mock(Query.class);
        doReturn(userUpdateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("dw.layer = 'USER'")
                && ((String) s).contains(HQL_CLASS_ID)
                && ((String) s).contains("dw.columnPosition")));
        when(userUpdateQ.setParameter(anyString(), any())).thenReturn(userUpdateQ);
        when(userUpdateQ.executeUpdate()).thenReturn(updateCount);
    }

    private void setRequestBody(String json) throws Exception {
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(json)));
    }

    private StringWriter prepareWriter() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        return sw;
    }

    // --- 1. GET /dashboard/layout ---

    @Test
    void getLayoutReturnsWidgetsWithClassData() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONArray widgets = new JSONArray();
        widgets.put(new JSONObject().put(INSTANCE_ID, "id1").put(WIDGET_CLASS_ID, "cls1"));

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class);
             MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class);
             MockedConstruction<DashboardLayoutResolver> resMock =
                     mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                             when(m.resolve()).thenReturn(widgets))) {

            stubCommon(ctxS, dalS);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(null);

            Query<Object[]> enrichQ = mockEnrichQuery();
            Object[] classRow = new Object[]{"MyWidget", "custom", "My Title", 30};
            when(enrichQ.uniqueResult()).thenReturn(classRow);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            String output = sw.toString();
            assertTrue(output.contains("id1"));
            assertTrue(output.contains("My Title"));
        }
    }

    @Test
    void getLayoutWithRegistryResolver() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONArray widgets = new JSONArray();
        widgets.put(new JSONObject().put(INSTANCE_ID, "id2").put(WIDGET_CLASS_ID, "cls2"));

        WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
        when(registry.getResolver(anyString())).thenReturn(null);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class);
             MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class);
             MockedConstruction<DashboardLayoutResolver> resMock =
                     mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                             when(m.resolve()).thenReturn(widgets))) {

            stubCommon(ctxS, dalS);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            mockEnrichQuery();

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            String output = sw.toString();
            assertTrue(output.contains("id2"));
            assertTrue(output.contains("\"available\":true"));
        }
    }

    // --- 2. PUT /dashboard/layout ---

    @SuppressWarnings("unchecked")
    @Test
    void putLayoutAdminUpdatesDirectly() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("PUT");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONObject body = new JSONObject();
        JSONArray widgetsArr = new JSONArray();
        widgetsArr.put(new JSONObject()
                .put(INSTANCE_ID, "w1")
                .put("col", 1).put("row", 2)
                .put(WIDTH, 3).put(HEIGHT, 4)
                .put("isVisible", true));
        body.put(WIDGETS, widgetsArr);
        setRequestBody(body.toString());

        when(role.isClientAdmin()).thenReturn(true);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("update etmeta_Dashboard_Widget")));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(1);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void putLayoutNonAdminFallsBackToUpsert() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("PUT");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONObject body = new JSONObject();
        JSONArray widgetsArr = new JSONArray();
        widgetsArr.put(new JSONObject()
                .put(INSTANCE_ID, "w2")
                .put("col", 0).put("row", 0)
                .put(WIDTH, 2).put(HEIGHT, 1)
                .put("isVisible", true));
        body.put(WIDGETS, widgetsArr);
        setRequestBody(body.toString());

        when(role.isClientAdmin()).thenReturn(false);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(new Object[]{"cls1", CLIENT1, 10, "{}"});
            mockUserOverrideQuery(0);
            mockNativeQuery();

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void putLayoutNonAdminUpsertFindsExistingUserOverride() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("PUT");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONObject body = new JSONObject();
        JSONArray widgetsArr = new JSONArray();
        widgetsArr.put(new JSONObject().put(INSTANCE_ID, "w3"));
        body.put(WIDGETS, widgetsArr);
        setRequestBody(body.toString());

        when(role.isClientAdmin()).thenReturn(false);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(new Object[]{"cls1", CLIENT1, 10, null});
            mockUserOverrideQuery(1);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void putLayoutUpsertSourceNullReturnsEarly() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn("PUT");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        JSONObject body = new JSONObject();
        JSONArray widgetsArr = new JSONArray();
        widgetsArr.put(new JSONObject().put(INSTANCE_ID, "w4"));
        body.put(WIDGETS, widgetsArr);
        setRequestBody(body.toString());

        when(role.isClientAdmin()).thenReturn(false);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(null);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    // --- 3. POST /dashboard/widget ---

    @Nested
    class PostWidgetTests {

        @SuppressWarnings("unchecked")
        @Test
        void userLayerCreatesNewWidget() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put("col", 0).put("row", 0).put(WIDTH, 2).put(HEIGHT, 1);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                String output = sw.toString();
                assertTrue(output.contains(CREATED));
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void clientLayerAdminCreatesWidget() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls2");
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(true);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains(CREATED));
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void alreadyExistsReturnsExistsStatus() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(1L);

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains("exists"));
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void withParametersValidHttpsUrl() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("url", "https://example.com/data");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains(CREATED));
            }
        }

        @Test
        void withNonHttpsUrlThrowsUnprocessableContent() throws Exception {
            prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("url", "http://evil.com/data");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                InternalServerException ex = assertThrows(InternalServerException.class, svc::process);
                assertTrue(ex.getMessage().contains("only https://"));
            }
        }

        @Test
        void withMalformedUrlThrowsUnprocessableContent() throws Exception {
            prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("url", "https://exam ple.com/bad path");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                InternalServerException ex = assertThrows(InternalServerException.class, svc::process);
                assertTrue(ex.getMessage().contains("malformed URL"));
            }
        }

        @Test
        void withAtInUserinfoPassesValidation() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("url", "https://user@evil.com/path");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains(CREATED));
            }
        }

        @Test
        void withParamNonUrlColonPassesValidation() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("title", "no-url-here");
            params.put("count", 42);

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains(CREATED));
            }
        }

        @Test
        void javascriptProtocolThrowsException() throws Exception {
            prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("src", "javascript:alert(1)");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                InternalServerException ex = assertThrows(InternalServerException.class, svc::process);
                assertTrue(ex.getMessage().contains("only https://"));
            }
        }

        @Test
        void emptyStringParamPassesValidation() throws Exception {
            StringWriter sw = prepareWriter();
            when(request.getMethod()).thenReturn("POST");
            when(request.getPathInfo()).thenReturn(PATH_WIDGET);

            JSONObject params = new JSONObject();
            params.put("note", "");

            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, "cls1");
            body.put(PARAMETERS, params);
            setRequestBody(body.toString());

            when(role.isClientAdmin()).thenReturn(false);

            try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
                 MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

                stubCommon(ctxS, dalS);
                mockPostWidgetQueries(0L);
                mockNativeQuery();

                DashboardService svc = new DashboardService(request, response);
                svc.process();

                assertTrue(sw.toString().contains(CREATED));
            }
        }
    }

    // --- 4. DELETE /dashboard/widget/{id} ---

    @SuppressWarnings("unchecked")
    @Test
    void deleteWidgetUserLayerDeletesRecord() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_DELETE);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/abc123");

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // getInstanceLayer returns "USER"
            Query<String> layerQ = mock(Query.class);
            doReturn(layerQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_SELECT_LAYER)),
                    eq(String.class));
            when(layerQ.setParameter(anyString(), any())).thenReturn(layerQ);
            when(layerQ.uniqueResult()).thenReturn("USER");

            // Delete query
            Query<Object> deleteQ = mock(Query.class);
            doReturn(deleteQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("delete from etmeta_Dashboard_Widget dw where dw.id")));
            when(deleteQ.setParameter(anyString(), any())).thenReturn(deleteQ);
            when(deleteQ.executeUpdate()).thenReturn(1);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("deleted"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteWidgetClientLayerInsertsShadowRecord() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_DELETE);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/abc456");

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // getInstanceLayer returns "CLIENT"
            Query<String> layerQ = mock(Query.class);
            doReturn(layerQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_SELECT_LAYER)),
                    eq(String.class));
            when(layerQ.setParameter(anyString(), any())).thenReturn(layerQ);
            when(layerQ.uniqueResult()).thenReturn("CLIENT");

            // getInstanceClassId
            Query<String> classIdQ = mock(Query.class);
            doReturn(classIdQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("select dw.widgetClass.id")),
                    eq(String.class));
            when(classIdQ.setParameter(anyString(), any())).thenReturn(classIdQ);
            when(classIdQ.uniqueResult()).thenReturn("wclass1");

            mockNativeQuery();

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("deleted"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteWidgetNullLayerThrowsNotFoundException() throws Exception {
        prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_DELETE);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/missing");

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            Query<String> layerQ = mock(Query.class);
            doReturn(layerQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_SELECT_LAYER)),
                    eq(String.class));
            when(layerQ.setParameter(anyString(), any())).thenReturn(layerQ);
            when(layerQ.uniqueResult()).thenReturn(null);

            DashboardService svc = new DashboardService(request, response);
            assertThrows(InternalServerException.class, svc::process);
        }
    }

    // --- 5. PATCH /dashboard/widget/{id}/params ---

    @SuppressWarnings("unchecked")
    @Test
    void patchWidgetParamsDirectUpdateSuccess() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_PATCH);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/inst1/params");

        JSONObject body = new JSONObject();
        body.put(PARAMETERS, new JSONObject().put("key", VALUE));
        setRequestBody(body.toString());

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // Direct update succeeds
            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains("dw.layer = 'USER'")));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(1);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void patchWidgetParamsFallbackToUpsertWithNativeInsert() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_PATCH);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/inst2/params");

        JSONObject body = new JSONObject();
        body.put(PARAMETERS, new JSONObject().put("key", VALUE));
        setRequestBody(body.toString());

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // Direct update returns 0
            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains(HQL_ID)));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(0);

            // Lookup query in upsertUserLayerOverrideWithParams
            Query<Object[]> lookupQ = mock(Query.class);
            doReturn(lookupQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_COL_ROW)),
                    eq(Object[].class));
            when(lookupQ.setParameter(anyString(), any())).thenReturn(lookupQ);
            when(lookupQ.uniqueResult()).thenReturn(new Object[]{"cls1", CLIENT1, 10, 0, 0, 2, 1});

            // User override update in upsertUserLayerOverrideWithParams returns 0 -> native insert
            Query<Object> userOverrideQ = mock(Query.class);
            doReturn(userOverrideQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains(HQL_CLASS_ID)));
            when(userOverrideQ.setParameter(anyString(), any())).thenReturn(userOverrideQ);
            when(userOverrideQ.executeUpdate()).thenReturn(0);

            mockNativeQuery();

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void patchWidgetParamsSourceNotFoundThrowsNotFoundException() throws Exception {
        prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_PATCH);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/missing/params");

        JSONObject body = new JSONObject();
        body.put(PARAMETERS, new JSONObject().put("key", VALUE));
        setRequestBody(body.toString());

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // Direct update returns 0
            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains(HQL_ID)));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(0);

            // Lookup returns null -> NotFoundException
            Query<Object[]> lookupQ = mock(Query.class);
            doReturn(lookupQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_COL_ROW)),
                    eq(Object[].class));
            when(lookupQ.setParameter(anyString(), any())).thenReturn(lookupQ);
            when(lookupQ.uniqueResult()).thenReturn(null);

            DashboardService svc = new DashboardService(request, response);
            assertThrows(InternalServerException.class, svc::process);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void patchWidgetParamsFallbackUpsertFindsExistingUserOverride() throws Exception {
        StringWriter sw = prepareWriter();
        when(request.getMethod()).thenReturn(METHOD_PATCH);
        when(request.getPathInfo()).thenReturn("/dashboard/widget/inst3/params");

        JSONObject body = new JSONObject();
        body.put(PARAMETERS, new JSONObject().put("key", VALUE));
        setRequestBody(body.toString());

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            // Direct update returns 0
            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains(HQL_ID)));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(0);

            // Lookup query returns source
            Query<Object[]> lookupQ = mock(Query.class);
            doReturn(lookupQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_COL_ROW)),
                    eq(Object[].class));
            when(lookupQ.setParameter(anyString(), any())).thenReturn(lookupQ);
            when(lookupQ.uniqueResult()).thenReturn(new Object[]{"cls1", CLIENT1, 10, 0, 0, 2, 1});

            // User override update returns >0 -> no native insert
            Query<Object> userOverrideQ = mock(Query.class);
            doReturn(userOverrideQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                    && ((String) s).contains(HQL_CLASS_ID)));
            when(userOverrideQ.setParameter(anyString(), any())).thenReturn(userOverrideQ);
            when(userOverrideQ.executeUpdate()).thenReturn(1);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            assertTrue(sw.toString().contains("ok"));
        }
    }

    // --- 6. Unknown route ---

    @Test
    void unknownRouteThrowsInternalServerException() throws Exception {
        prepareWriter();
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/dashboard/unknown");

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            DashboardService svc = new DashboardService(request, response);
            assertThrows(InternalServerException.class, svc::process);
        }
    }

    @Test
    void unknownMethodThrowsInternalServerException() throws Exception {
        prepareWriter();
        when(request.getMethod()).thenReturn("HEAD");
        when(request.getPathInfo()).thenReturn(PATH_LAYOUT);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {

            stubCommon(ctxS, dalS);

            DashboardService svc = new DashboardService(request, response);
            assertThrows(InternalServerException.class, svc::process);
        }
    }
}
