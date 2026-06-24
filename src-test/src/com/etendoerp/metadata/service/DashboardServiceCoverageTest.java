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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
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
    private static final String METHOD_PUT = "PUT";
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

    // --- functional interface for mocked-context lambda ---

    @FunctionalInterface
    interface MockedAction {
        /**
         * Executes the test action, allowing checked exceptions for test convenience.
         *
         * @throws Exception if the test action fails
         */
        @SuppressWarnings("java:S112")
        void run() throws Exception;
    }

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

    private void withMockedContext(MockedAction action) throws Exception {
        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class)) {
            stubCommon(ctxS, dalS);
            action.run();
        }
    }

    private void setupRequest(String method, String path) {
        when(request.getMethod()).thenReturn(method);
        when(request.getPathInfo()).thenReturn(path);
    }

    private void processAndAssertContains(StringWriter sw, String expected) throws Exception {
        DashboardService svc = new DashboardService(request, response);
        svc.process();
        assertTrue(sw.toString().contains(expected));
    }

    private void processAndExpectException(Class<? extends Exception> exClass) {
        DashboardService svc = new DashboardService(request, response);
        assertThrows(exClass, svc::process);
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
    private NativeQuery<Object> mockNativeQuery() {
        NativeQuery<Object> nq = mock(NativeQuery.class);
        doReturn(nq).when(session).createNativeQuery(anyString());
        when(nq.setParameter(anyString(), any())).thenReturn(nq);
        when(nq.executeUpdate()).thenReturn(1);
        return nq;
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

    @SuppressWarnings("unchecked")
    private Query<String> mockLayerQuery(String layerResult) {
        Query<String> layerQ = mock(Query.class);
        doReturn(layerQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_SELECT_LAYER)),
                eq(String.class));
        when(layerQ.setParameter(anyString(), any())).thenReturn(layerQ);
        when(layerQ.uniqueResult()).thenReturn(layerResult);
        return layerQ;
    }

    private JSONObject buildPutLayoutBody(String instanceId, boolean includePositions) throws Exception {
        JSONObject body = new JSONObject();
        JSONArray widgetsArr = new JSONArray();
        JSONObject widget = new JSONObject().put(INSTANCE_ID, instanceId);
        if (includePositions) {
            widget.put("col", 0).put("row", 0).put(WIDTH, 2).put(HEIGHT, 1).put("isVisible", true);
        }
        widgetsArr.put(widget);
        body.put(WIDGETS, widgetsArr);
        return body;
    }

    private StringWriter setupPutLayoutRequest(String instanceId, boolean includePositions, boolean isAdmin) throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest("PUT", PATH_LAYOUT);
        JSONObject body = buildPutLayoutBody(instanceId, includePositions);
        setRequestBody(body.toString());
        when(role.isClientAdmin()).thenReturn(isAdmin);
        return sw;
    }

    @SuppressWarnings("unchecked")
    private void mockPatchDirectUpdateQuery(String hqlContains, int updateCount) {
        Query<Object> updateQ = mock(Query.class);
        doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                && ((String) s).contains(hqlContains)));
        when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
        when(updateQ.executeUpdate()).thenReturn(updateCount);
    }

    @SuppressWarnings("unchecked")
    private void mockPatchLookupQuery(Object[] result) {
        Query<Object[]> lookupQ = mock(Query.class);
        doReturn(lookupQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_COL_ROW)),
                eq(Object[].class));
        when(lookupQ.setParameter(anyString(), any())).thenReturn(lookupQ);
        when(lookupQ.uniqueResult()).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void mockPatchUserOverrideQuery(int updateCount) {
        Query<Object> userOverrideQ = mock(Query.class);
        doReturn(userOverrideQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains(HQL_PARAMS_JSON)
                && ((String) s).contains(HQL_CLASS_ID)));
        when(userOverrideQ.setParameter(anyString(), any())).thenReturn(userOverrideQ);
        when(userOverrideQ.executeUpdate()).thenReturn(updateCount);
    }

    private StringWriter setupPatchRequest(String instanceId) throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest(METHOD_PUT, "/dashboard/widget/" + instanceId + "/params");
        JSONObject body = new JSONObject();
        body.put(PARAMETERS, new JSONObject().put("key", VALUE));
        setRequestBody(body.toString());
        return sw;
    }

    // --- 1. GET /dashboard/layout ---

    @Test
    void getLayoutReturnsWidgetsWithClassData() throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest("GET", PATH_LAYOUT);

        JSONArray widgets = new JSONArray();
        widgets.put(new JSONObject().put(INSTANCE_ID, "id1").put(WIDGET_CLASS_ID, "cls1"));

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class);
             MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class);
             var resMock = mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                     when(m.resolve()).thenReturn(widgets))) {

            stubCommon(ctxS, dalS);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(null);

            Query<Object[]> enrichQ = mockEnrichQuery();
            Object[] classRow = new Object[]{"MyWidget", "custom", "My Title", 30};
            when(enrichQ.uniqueResult()).thenReturn(classRow);

            processAndAssertContains(sw, "My Title");
        }
    }

    @Test
    void getLayoutWithRegistryResolver() throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest("GET", PATH_LAYOUT);

        JSONArray widgets = new JSONArray();
        widgets.put(new JSONObject().put(INSTANCE_ID, "id2").put(WIDGET_CLASS_ID, "cls2"));

        WidgetResolverRegistry registry = mock(WidgetResolverRegistry.class);
        when(registry.getResolver(anyString())).thenReturn(null);

        try (MockedStatic<OBContext> ctxS = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalS = mockStatic(OBDal.class);
             MockedStatic<WidgetResolverRegistryHolder> regS = mockStatic(WidgetResolverRegistryHolder.class);
             var resMock = mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                     when(m.resolve()).thenReturn(widgets))) {

            stubCommon(ctxS, dalS);
            regS.when(WidgetResolverRegistryHolder::getInstance).thenReturn(registry);
            mockEnrichQuery();

            processAndAssertContains(sw, "\"available\":true");
        }
    }

    // --- 2. PUT /dashboard/layout ---

    @SuppressWarnings("unchecked")
    @Test
    void putLayoutAdminUpdatesDirectly() throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest("PUT", PATH_LAYOUT);

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

        withMockedContext(() -> {
            Query<Object> updateQ = mock(Query.class);
            doReturn(updateQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("update etmeta_Dashboard_Widget")));
            when(updateQ.setParameter(anyString(), any())).thenReturn(updateQ);
            when(updateQ.executeUpdate()).thenReturn(1);

            processAndAssertContains(sw, "ok");
        });
    }

    @Test
    void putLayoutNonAdminFallsBackToUpsert() throws Exception {
        StringWriter sw = setupPutLayoutRequest("w2", true, false);

        withMockedContext(() -> {
            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(new Object[]{"cls1", CLIENT1, 10, "{}"});
            mockUserOverrideQuery(0);
            mockNativeQuery();

            processAndAssertContains(sw, "ok");
        });
    }

    @Test
    void putLayoutNonAdminUpsertFindsExistingUserOverride() throws Exception {
        StringWriter sw = setupPutLayoutRequest("w3", false, false);

        withMockedContext(() -> {
            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(new Object[]{"cls1", CLIENT1, 10, null});
            mockUserOverrideQuery(1);

            processAndAssertContains(sw, "ok");
        });
    }

    @Test
    void putLayoutUpsertSourceNullReturnsEarly() throws Exception {
        StringWriter sw = setupPutLayoutRequest("w4", false, false);

        withMockedContext(() -> {
            mockUpdateLayoutQuery(0);
            mockLookupSourceQuery(null);

            processAndAssertContains(sw, "ok");
        });
    }

    // --- 3. POST /dashboard/widget ---

    @Nested
    class PostWidgetTests {

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

        private StringWriter setupPostWidget(String classId, JSONObject params, boolean isAdmin) throws Exception {
            StringWriter sw = prepareWriter();
            setupRequest("POST", PATH_WIDGET);
            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, classId);
            if (params != null) {
                body.put(PARAMETERS, params);
            }
            setRequestBody(body.toString());
            when(role.isClientAdmin()).thenReturn(isAdmin);
            return sw;
        }

        private StringWriter setupPostWidgetWithPositions(String classId, boolean isAdmin) throws Exception {
            StringWriter sw = prepareWriter();
            setupRequest("POST", PATH_WIDGET);
            JSONObject body = new JSONObject();
            body.put(WIDGET_CLASS_ID, classId);
            body.put("col", 0).put("row", 0).put(WIDTH, 2).put(HEIGHT, 1);
            setRequestBody(body.toString());
            when(role.isClientAdmin()).thenReturn(isAdmin);
            return sw;
        }

        @Test
        void userLayerCreatesNewWidget() throws Exception {
            StringWriter sw = setupPostWidgetWithPositions("cls1", false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }

        @Test
        void clientLayerAdminCreatesWidget() throws Exception {
            StringWriter sw = setupPostWidget("cls2", null, true);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }

        @Test
        void alreadyExistsReturnsExistsStatus() throws Exception {
            StringWriter sw = setupPostWidget("cls1", null, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(1L);
                processAndAssertContains(sw, "exists");
            });
        }

        @Test
        void withParametersValidHttpsUrl() throws Exception {
            JSONObject params = new JSONObject().put("url", "https://example.com/data");
            StringWriter sw = setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }

        @Test
        void withNonHttpsUrlThrowsUnprocessableContent() throws Exception {
            JSONObject params = new JSONObject().put("url", "http://evil.com/data");
            setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndExpectException(InternalServerException.class);
            });
        }

        @Test
        void withMalformedUrlThrowsUnprocessableContent() throws Exception {
            JSONObject params = new JSONObject().put("url", "https://exam ple.com/bad path");
            setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndExpectException(InternalServerException.class);
            });
        }

        @Test
        void withAtInUserinfoPassesValidation() throws Exception {
            JSONObject params = new JSONObject().put("url", "https://user@evil.com/path");
            StringWriter sw = setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }

        @Test
        void withParamNonUrlColonPassesValidation() throws Exception {
            JSONObject params = new JSONObject();
            params.put("title", "no-url-here");
            params.put("count", 42);
            StringWriter sw = setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }

        @Test
        void javascriptProtocolThrowsException() throws Exception {
            JSONObject params = new JSONObject().put("src", "javascript:alert(1)");
            setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndExpectException(InternalServerException.class);
            });
        }

        @Test
        void emptyStringParamPassesValidation() throws Exception {
            JSONObject params = new JSONObject().put("note", "");
            StringWriter sw = setupPostWidget("cls1", params, false);

            withMockedContext(() -> {
                mockPostWidgetQueries(0L);
                mockNativeQuery();
                processAndAssertContains(sw, CREATED);
            });
        }
    }

    // --- 4. DELETE /dashboard/widget/{id} ---

    @Test
    void deleteWidgetUserLayerDeletesRecord() throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest(METHOD_DELETE, "/dashboard/widget/abc123");

        withMockedContext(() -> {
            mockLayerQuery("USER");

            @SuppressWarnings("unchecked")
            Query<Object> deleteQ = mock(Query.class);
            doReturn(deleteQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("delete from etmeta_Dashboard_Widget dw where dw.id")));
            when(deleteQ.setParameter(anyString(), any())).thenReturn(deleteQ);
            when(deleteQ.executeUpdate()).thenReturn(1);

            processAndAssertContains(sw, "deleted");
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteWidgetClientLayerInsertsShadowRecord() throws Exception {
        StringWriter sw = prepareWriter();
        setupRequest(METHOD_DELETE, "/dashboard/widget/abc456");

        withMockedContext(() -> {
            mockLayerQuery("CLIENT");

            Query<String> classIdQ = mock(Query.class);
            doReturn(classIdQ).when(session).createQuery((String) argThat(s -> s != null && ((String) s).contains("select dw.widgetClass.id")),
                    eq(String.class));
            when(classIdQ.setParameter(anyString(), any())).thenReturn(classIdQ);
            when(classIdQ.uniqueResult()).thenReturn("wclass1");

            mockNativeQuery();

            processAndAssertContains(sw, "deleted");
        });
    }

    @Test
    void deleteWidgetNullLayerThrowsNotFoundException() throws Exception {
        prepareWriter();
        setupRequest(METHOD_DELETE, "/dashboard/widget/missing");

        withMockedContext(() -> {
            mockLayerQuery(null);
            processAndExpectException(InternalServerException.class);
        });
    }

    // --- 5. PUT /dashboard/widget/{id}/params ---

    @Test
    void patchWidgetParamsDirectUpdateSuccess() throws Exception {
        StringWriter sw = setupPatchRequest("inst1");

        withMockedContext(() -> {
            mockPatchDirectUpdateQuery("dw.layer = 'USER'", 1);
            processAndAssertContains(sw, "ok");
        });
    }

    @Test
    void patchWidgetParamsFallbackToUpsertWithNativeInsert() throws Exception {
        StringWriter sw = setupPatchRequest("inst2");

        withMockedContext(() -> {
            mockPatchDirectUpdateQuery(HQL_ID, 0);
            mockPatchLookupQuery(new Object[]{"cls1", CLIENT1, 10, 0, 0, 2, 1});
            mockPatchUserOverrideQuery(0);
            mockNativeQuery();

            processAndAssertContains(sw, "ok");
        });
    }

    @Test
    void patchWidgetParamsSourceNotFoundThrowsNotFoundException() throws Exception {
        setupPatchRequest("missing");

        withMockedContext(() -> {
            mockPatchDirectUpdateQuery(HQL_ID, 0);
            mockPatchLookupQuery(null);
            processAndExpectException(InternalServerException.class);
        });
    }

    @Test
    void patchWidgetParamsFallbackUpsertFindsExistingUserOverride() throws Exception {
        StringWriter sw = setupPatchRequest("inst3");

        withMockedContext(() -> {
            mockPatchDirectUpdateQuery(HQL_ID, 0);
            mockPatchLookupQuery(new Object[]{"cls1", CLIENT1, 10, 0, 0, 2, 1});
            mockPatchUserOverrideQuery(1);

            processAndAssertContains(sw, "ok");
        });
    }

    // --- 6. Unknown route ---

    @Test
    void unknownRouteThrowsInternalServerException() throws Exception {
        prepareWriter();
        setupRequest("GET", "/dashboard/unknown");

        withMockedContext(() -> processAndExpectException(InternalServerException.class));
    }

    @Test
    void unknownMethodThrowsInternalServerException() throws Exception {
        prepareWriter();
        setupRequest("HEAD", PATH_LAYOUT);

        withMockedContext(() -> processAndExpectException(InternalServerException.class));
    }
}
