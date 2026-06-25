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
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import javax.persistence.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extended coverage tests for {@link QueryListResolver}.
 * Exercises all branches in resolve, buildRows, buildColumnDefs, buildResolvedParams,
 * bindParams, countQuery, extractAliases, toLabel, splitTopLevel, and mainFromIndex.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryListResolverCoverageTest {

    private static final String TOTAL_ROWS = "totalRows";
    private static final String COLUMNS = "columns";
    private static final String SO_001 = "SO-001";
    private static final String DOC_NO = "docNo";
    private static final String TOTAL = "total";
    private static final String LABEL = "label";
    private static final String PAGE_SIZE = "_pageSize";
    private static final String HQL_SELECT_NAME = "select o.name as name from Order o";
    private static final String ORDER_1 = "Order-1";
    private static final String TEST_ORDER = "Test Order";
    private static final String ORGANIZATION_LIST = "organizationList";
    private static final String CLIENT_1 = "client-1";
    private static final String ROWS = "rows";
    private static final String NAME = "name";

    private QueryListResolver resolver;

    @Mock
    private OBDal obDal;
    @Mock
    private Session session;

    @BeforeEach
    void setUp() {
        resolver = new QueryListResolver();
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private WidgetDataContext buildCtx(String hql,
                                       Map<String, Object> params,
                                       OBContext obContext) {
        Map<String, Object> classData = new HashMap<>();
        if (hql != null) {
            classData.put("4", hql);
        }
        return new WidgetDataContext(
                "inst-1",
                Collections.emptyMap(),
                classData,
                params != null ? params : Collections.emptyMap(),
                obContext,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> mockRowQuery(List<Object[]> rows) {
        Query<Object[]> q = mock(Query.class);
        when(q.getParameters()).thenReturn(Collections.emptySet());
        when(q.list()).thenReturn(rows);
        when(q.setFirstResult(anyInt())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        return q;
    }

    @SuppressWarnings("unchecked")
    private Query<Long> mockCountQuery(long count) {
        Query<Long> q = mock(Query.class);
        when(q.getParameters()).thenReturn(Collections.emptySet());
        when(q.uniqueResult()).thenReturn(count);
        return q;
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> mockQueryWithNamedParams(List<Object[]> rows, String... paramNames) {
        Query<Object[]> q = mock(Query.class);
        when(q.list()).thenReturn(rows);
        when(q.setFirstResult(anyInt())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        Set<Parameter<?>> paramSet = new HashSet<>();
        for (String name : paramNames) {
            Parameter<?> qp = mock(Parameter.class);
            when(qp.getName()).thenReturn(name);
            paramSet.add(qp);
        }
        doReturn(paramSet).when(q).getParameters();
        return q;
    }

    /**
     * Functional interface for test logic that runs inside the OBDal mock scope.
     */
    @FunctionalInterface
    private interface ResolverTestAction {
        @SuppressWarnings("java:S112")
        void execute() throws Exception;
    }

    /**
     * Sets up the OBDal static mock, registers the row query on the session,
     * then runs the given test action inside the mock scope.
     */
    private void withMockedDal(String hql, Query<Object[]> rowQuery, ResolverTestAction action) throws Exception {
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(rowQuery);
        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);
            action.execute();
        }
    }

    /**
     * Overload that also registers a count query for pagination tests.
     */
    private void withMockedDal(String hql, Query<Object[]> rowQuery,
                               Query<Long> countQuery, ResolverTestAction action) throws Exception {
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(rowQuery);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);
            action.execute();
        }
    }

    private OBContext buildMockOBContext(String clientId, String userId, String[] readableOrgs) {
        OBContext obContext = mock(OBContext.class);
        Client client = mock(Client.class);
        User user = mock(User.class);
        when(client.getId()).thenReturn(clientId);
        when(user.getId()).thenReturn(userId);
        when(obContext.getCurrentClient()).thenReturn(client);
        when(obContext.getUser()).thenReturn(user);
        when(obContext.getReadableOrganizations()).thenReturn(readableOrgs);
        return obContext;
    }

    // -------------------------------------------------------
    // 1. Null HQL returns empty rows
    // -------------------------------------------------------
    @Test
    void resolveWithNullHqlReturnsEmptyResult() throws Exception {
        WidgetDataContext ctx = buildCtx(null, null, null);
        JSONObject result = resolver.resolve(ctx);

        assertEquals(0, result.getInt(TOTAL_ROWS));
        assertEquals(0, result.getJSONArray(ROWS).length());
        assertFalse(result.has(COLUMNS));
    }

    // -------------------------------------------------------
    // 2. With explicit columns param
    // -------------------------------------------------------
    @Test
    void resolveWithExplicitColumnsParam() throws Exception {
        String hql = "select o.documentNo, o.grandTotalAmount from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "docNo,total");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{SO_001, 500.0}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);

            assertEquals(1, result.getInt(TOTAL_ROWS));
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            assertEquals(500.0, firstRow.getDouble(TOTAL));

            var colDefs = result.getJSONArray(COLUMNS);
            assertEquals(2, colDefs.length());
            assertEquals(DOC_NO, colDefs.getJSONObject(0).getString(NAME));
            assertEquals("Doc No", colDefs.getJSONObject(0).getString(LABEL));
        });
    }

    // -------------------------------------------------------
    // 3. Without columns param - alias extraction from HQL
    // -------------------------------------------------------
    @Test
    void resolveWithoutColumnsExtractsAliases() throws Exception {
        String hql = "select a.documentNo as docNo, b.totalAmount as total from Order a, Line b";
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"SO-002", 750.0}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals("SO-002", firstRow.getString(DOC_NO));
            assertEquals(750.0, firstRow.getDouble(TOTAL));
        });
    }

    // -------------------------------------------------------
    // 4. With _pageSize param (pagination path + countQuery)
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeUsesPagination() throws Exception {
        String hql = "select o.documentNo as docNo from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put("_page", "2");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"SO-011"}));
        Query<Long> countQ = mockCountQuery(25L);

        withMockedDal(hql, mockQuery, countQ, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(25, result.getInt(TOTAL_ROWS));
            verify(mockQuery).setFirstResult(10);
            verify(mockQuery).setMaxResults(10);
        });
    }

    // -------------------------------------------------------
    // 5. With rowsNumber param (no pagination)
    // -------------------------------------------------------
    @Test
    void resolveWithRowsNumberSetsMaxResults() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rowsNumber", "5");
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{ORDER_1}));

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(1, result.getInt(TOTAL_ROWS));
            verify(mockQuery).setMaxResults(5);
            verify(mockQuery, never()).setFirstResult(anyInt());
        });
    }

    // -------------------------------------------------------
    // 6. More columns in result than in colNames -> "col" + i fallback
    // -------------------------------------------------------
    @Test
    void buildRowsFallsBackToColIndexWhenMoreColumnsThanNames() throws Exception {
        String hql = "select o.documentNo, o.name, o.total from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "docNo,name");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{SO_001, TEST_ORDER, 100.0}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            assertEquals(TEST_ORDER, firstRow.getString(NAME));
            assertEquals(100.0, firstRow.getDouble("col2"));
        });
    }

    // -------------------------------------------------------
    // 7. Column defs skip columns ending in "Id"
    // -------------------------------------------------------
    @Test
    void buildColumnDefsSkipsIdColumns() throws Exception {
        String hql = "select o.id, o.name from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "orderId,name");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"123", "Test"}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            var colDefs = result.getJSONArray(COLUMNS);
            assertEquals(1, colDefs.length());
            assertEquals(NAME, colDefs.getJSONObject(0).getString(NAME));
            assertEquals("Name", colDefs.getJSONObject(0).getString(LABEL));
        });
    }

    // -------------------------------------------------------
    // 8. bindParams with Collection param -> setParameterList
    // -------------------------------------------------------
    @Test
    void bindParamsUsesSetParameterListForCollections() throws Exception {
        String hql = "select o.name as name from Order o where o.organization.id in (:organizationList)";
        Map<String, Object> params = new HashMap<>();
        params.put(ORGANIZATION_LIST, Arrays.asList("org1", "org2"));
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockQueryWithNamedParams(Collections.emptyList(), ORGANIZATION_LIST);
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        withMockedDal(hql, mockQuery, () -> {
            resolver.resolve(ctx);
            verify(mockQuery).setParameterList(eq(ORGANIZATION_LIST), anyCollection());
        });
    }

    // -------------------------------------------------------
    // 9. bindParams with null param -> uses "%" as default
    // -------------------------------------------------------
    @Test
    void bindParamsUsesPercentForNullValue() throws Exception {
        String hql = "select o.name as name from Order o where o.name like :filter";
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockQueryWithNamedParams(Collections.emptyList(), "filter");

        withMockedDal(hql, mockQuery, () -> {
            resolver.resolve(ctx);
            verify(mockQuery).setParameter("filter", "%");
        });
    }

    // -------------------------------------------------------
    // 10. Null OBContext in buildResolvedParams
    // -------------------------------------------------------
    @Test
    void resolveWithNullOBContextDoesNotAddContextParams() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("customParam", "value1");
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"Test"}));

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(1, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 11. OBContext with null readableOrganizations
    // -------------------------------------------------------
    @Test
    void resolveWithOBContextNullReadableOrgs() throws Exception {
        OBContext obContext = buildMockOBContext(CLIENT_1, "user-1", null);
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, new HashMap<>(), obContext);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"Test"}));

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(1, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 12. OBContext with valid readableOrganizations
    // -------------------------------------------------------
    @Test
    void resolveWithOBContextAddsClientUserAndOrgs() throws Exception {
        String hql = "select o.name as name from Order o where o.client.id = :client";
        OBContext obContext = buildMockOBContext(CLIENT_1, "user-1", new String[]{"org-1", "org-2"});
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), obContext);

        Query<Object[]> mockQuery = mockQueryWithNamedParams(
                Collections.singletonList(new Object[]{"Test"}), "client");

        withMockedDal(hql, mockQuery, () -> {
            resolver.resolve(ctx);
            verify(mockQuery).setParameter("client", CLIENT_1);
        });
    }

    // -------------------------------------------------------
    // 13. countQuery strips GROUP BY, ORDER BY, HAVING
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeStripsGroupByOrderByHaving() throws Exception {
        String hql = "select o.status as status, count(o.id) as cnt from Order o "
                + "group by o.status having count(o.id) > 1 order by o.status";
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put(COLUMNS, "status,cnt");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"COMPLETED", 5L}));
        Query<Long> countQ = mockCountQuery(3L);

        withMockedDal(hql, mockQuery, countQ, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(3, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 14. countQuery returns 0 when no FROM found (fromIdx < 0)
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeAndNoFromKeywordReturnsTotalZero() throws Exception {
        String hql = "select 1";
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put(COLUMNS, "val");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{1}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(0, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 15. extractAliases falls back to "col" + i when no AS alias
    // -------------------------------------------------------
    @Test
    void resolveWithoutColumnsAndNoAliasesUsesColIndex() throws Exception {
        String hql = "select o.documentNo, o.grandTotalAmount from Order o";
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{"SO-003", 300.0}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals("SO-003", firstRow.getString("col0"));
            assertEquals(300.0, firstRow.getDouble("col1"));
        });
    }

    // -------------------------------------------------------
    // 16. toLabel tested indirectly via column defs
    // -------------------------------------------------------
    @Test
    void toLabelConvertsVariousCamelCasePatterns() throws Exception {
        String hql = "select o.x from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "grandTotalAmount,documentNo,x");
        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{100.0, "SO-1", "val"}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            var colDefs = result.getJSONArray(COLUMNS);
            assertEquals(3, colDefs.length());
            assertEquals("Grand Total Amount", colDefs.getJSONObject(0).getString(LABEL));
            assertEquals("Document No", colDefs.getJSONObject(1).getString(LABEL));
            assertEquals("X", colDefs.getJSONObject(2).getString(LABEL));
        });
    }

    // -------------------------------------------------------
    // 17. Subquery in SELECT clause (parentheses handling in splitTopLevel)
    // -------------------------------------------------------
    @Test
    void resolveWithSubqueryInSelectExtractsAliasesCorrectly() throws Exception {
        String hql = "select o.name as name, (select count(*) from Line l where l.order = o) as lineCount from Order o";
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{TEST_ORDER, 3L}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals(TEST_ORDER, firstRow.getString(NAME));
            assertEquals(3L, firstRow.getLong("lineCount"));
        });
    }

    // -------------------------------------------------------
    // 18. _pageSize with default page (no _page param)
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeDefaultsToPageOne() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "5");
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());
        Query<Long> countQ = mockCountQuery(0L);

        withMockedDal(HQL_SELECT_NAME, mockQuery, countQ, () -> {
            resolver.resolve(ctx);
            verify(mockQuery).setFirstResult(0);
            verify(mockQuery).setMaxResults(5);
        });
    }

    // -------------------------------------------------------
    // 19. Empty result set
    // -------------------------------------------------------
    @Test
    void resolveWithEmptyResultReturnsZeroRows() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, NAME);
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(0, result.getInt(TOTAL_ROWS));
            assertEquals(0, result.getJSONArray(ROWS).length());
        });
    }

    // -------------------------------------------------------
    // 20. bindParams skips parameter with null name
    // -------------------------------------------------------
    @Test
    void bindParamsSkipsParameterWithNullName() throws Exception {
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockQueryWithNamedParams(
                Collections.singletonList(new Object[]{"Test"}), (String) null);

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            verify(mockQuery, never()).setParameter(anyString(), any());
            verify(mockQuery, never()).setParameterList(anyString(), anyCollection());
            assertEquals(1, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 21. Mixed aliases: some with AS, some without
    // -------------------------------------------------------
    @Test
    void resolveExtractsPartialAliases() throws Exception {
        String hql = "select o.documentNo as docNo, o.name, o.total as total from Order o";
        WidgetDataContext ctx = buildCtx(hql, new HashMap<>(), null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(new Object[]{SO_001, "Test", 100.0}));

        withMockedDal(hql, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            JSONObject firstRow = result.getJSONArray(ROWS).getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            assertEquals("Test", firstRow.getString("col1"));
            assertEquals(100.0, firstRow.getDouble(TOTAL));
        });
    }

    // -------------------------------------------------------
    // 22. Multiple rows returned
    // -------------------------------------------------------
    @Test
    void resolveReturnsMultipleRows() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, NAME);
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        List<Object[]> rows = Arrays.asList(
                new Object[]{ORDER_1},
                new Object[]{"Order-2"},
                new Object[]{"Order-3"}
        );
        Query<Object[]> mockQuery = mockRowQuery(rows);

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(3, result.getInt(TOTAL_ROWS));
            var rowsArr = result.getJSONArray(ROWS);
            assertEquals(3, rowsArr.length());
            assertEquals(ORDER_1, rowsArr.getJSONObject(0).getString(NAME));
            assertEquals("Order-2", rowsArr.getJSONObject(1).getString(NAME));
            assertEquals("Order-3", rowsArr.getJSONObject(2).getString(NAME));
        });
    }

    // -------------------------------------------------------
    // 23. countQuery with null uniqueResult returns 0
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void resolveWithPageSizeAndNullCountReturnsZero() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put(COLUMNS, NAME);
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());

        Query<Long> countQ = mock(Query.class);
        when(countQ.getParameters()).thenReturn(Collections.emptySet());
        when(countQ.uniqueResult()).thenReturn(null);

        withMockedDal(HQL_SELECT_NAME, mockQuery, countQ, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(0, result.getInt(TOTAL_ROWS));
        });
    }

    // -------------------------------------------------------
    // 24. No rowsNumber and no _pageSize -> totalRows from list size
    // -------------------------------------------------------
    @Test
    void resolveWithNoPaginationParamsUsesListSize() throws Exception {
        WidgetDataContext ctx = buildCtx(HQL_SELECT_NAME, new HashMap<>(), null);

        List<Object[]> rows = Arrays.asList(new Object[]{"A"}, new Object[]{"B"});
        Query<Object[]> mockQuery = mockRowQuery(rows);

        withMockedDal(HQL_SELECT_NAME, mockQuery, () -> {
            JSONObject result = resolver.resolve(ctx);
            assertEquals(2, result.getInt(TOTAL_ROWS));
        });
    }
}
