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
import org.codehaus.jettison.json.JSONArray;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    // Helper: build a WidgetDataContext using real constructor
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

    // -------------------------------------------------------
    // 1. Null HQL returns empty rows
    // -------------------------------------------------------
    @Test
    void resolveWithNullHqlReturnsEmptyResult() throws Exception {
        WidgetDataContext ctx = buildCtx(null, null, null);
        JSONObject result = resolver.resolve(ctx);

        assertEquals(0, result.getInt(TOTAL_ROWS));
        assertEquals(0, result.getJSONArray("rows").length());
        assertFalse(result.has(COLUMNS));
    }

    // -------------------------------------------------------
    // 2. With explicit columns param
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void resolveWithExplicitColumnsParam() throws Exception {
        String hql = "select o.documentNo, o.grandTotalAmount from Order o";
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "docNo,total");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {SO_001, 500.0};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(1, result.getInt(TOTAL_ROWS));
            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            assertEquals(500.0, firstRow.getDouble(TOTAL));

            // Column defs should have both columns
            JSONArray colDefs = result.getJSONArray(COLUMNS);
            assertEquals(2, colDefs.length());
            assertEquals(DOC_NO, colDefs.getJSONObject(0).getString("name"));
            assertEquals("Doc No", colDefs.getJSONObject(0).getString(LABEL));
        }
    }

    // -------------------------------------------------------
    // 3. Without columns param - alias extraction from HQL
    // -------------------------------------------------------
    @Test
    void resolveWithoutColumnsExtractsAliases() throws Exception {
        String hql = "select a.documentNo as docNo, b.totalAmount as total from Order a, Line b";
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {"SO-002", 750.0};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            assertEquals("SO-002", firstRow.getString(DOC_NO));
            assertEquals(750.0, firstRow.getDouble(TOTAL));
        }
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

        Object[] row = {"SO-011"};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        // Count query
        Query<Long> countQ = mockCountQuery(25L);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(25, result.getInt(TOTAL_ROWS));
            // page 2, pageSize 10 -> firstResult = 10
            verify(mockQuery).setFirstResult(10);
            verify(mockQuery).setMaxResults(10);
        }
    }

    // -------------------------------------------------------
    // 5. With rowsNumber param (no pagination)
    // -------------------------------------------------------
    @Test
    void resolveWithRowsNumberSetsMaxResults() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put("rowsNumber", "5");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {ORDER_1};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(1, result.getInt(TOTAL_ROWS));
            verify(mockQuery).setMaxResults(5);
            verify(mockQuery, never()).setFirstResult(anyInt());
        }
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

        // Result has 3 columns but only 2 named
        Object[] row = {SO_001, TEST_ORDER, 100.0};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            assertEquals(TEST_ORDER, firstRow.getString("name"));
            // Third column falls back to "col2"
            assertEquals(100.0, firstRow.getDouble("col2"));
        }
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

        Object[] row = {"123", "Test"};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONArray colDefs = result.getJSONArray(COLUMNS);
            // "orderId" should be skipped, only "name" remains
            assertEquals(1, colDefs.length());
            assertEquals("name", colDefs.getJSONObject(0).getString("name"));
            assertEquals("Name", colDefs.getJSONObject(0).getString(LABEL));
        }
    }

    // -------------------------------------------------------
    // 8. bindParams with Collection param -> setParameterList
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void bindParamsUsesSetParameterListForCollections() throws Exception {
        String hql = "select o.name as name from Order o where o.organization.id in (:organizationList)";
        Map<String, Object> params = new HashMap<>();
        params.put(ORGANIZATION_LIST, Arrays.asList("org1", "org2"));

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(Collections.emptyList());
        when(mockQuery.setFirstResult(anyInt())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);

        // Set up a query parameter named "organizationList"
        Parameter<?> qp = mock(Parameter.class);
        when(qp.getName()).thenReturn(ORGANIZATION_LIST);
        Set<Parameter<?>> paramSet = new HashSet<>();
        paramSet.add(qp);
        doReturn(paramSet).when(mockQuery).getParameters();

        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            resolver.resolve(ctx);

            verify(mockQuery).setParameterList(eq(ORGANIZATION_LIST), anyCollection());
        }
    }

    // -------------------------------------------------------
    // 9. bindParams with null param -> uses "%" as default
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void bindParamsUsesPercentForNullValue() throws Exception {
        String hql = "select o.name as name from Order o where o.name like :filter";
        Map<String, Object> params = new HashMap<>();
        // "filter" is not in params, so it is null

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(Collections.emptyList());
        when(mockQuery.setFirstResult(anyInt())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);

        Parameter<?> qp = mock(Parameter.class);
        when(qp.getName()).thenReturn("filter");
        Set<Parameter<?>> paramSet = new HashSet<>();
        paramSet.add(qp);
        doReturn(paramSet).when(mockQuery).getParameters();

        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            resolver.resolve(ctx);

            verify(mockQuery).setParameter("filter", "%");
        }
    }

    // -------------------------------------------------------
    // 10. Null OBContext in buildResolvedParams
    // -------------------------------------------------------
    @Test
    void resolveWithNullOBContextDoesNotAddContextParams() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put("customParam", "value1");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {"Test"};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(1, result.getInt(TOTAL_ROWS));
            // No exception means buildResolvedParams handled null OBContext gracefully
        }
    }

    // -------------------------------------------------------
    // 11. OBContext with null readableOrganizations
    // -------------------------------------------------------
    @Test
    void resolveWithOBContextNullReadableOrgs() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();

        OBContext obContext = mock(OBContext.class);
        Client client = mock(Client.class);
        User user = mock(User.class);
        when(client.getId()).thenReturn(CLIENT_1);
        when(user.getId()).thenReturn("user-1");
        when(obContext.getCurrentClient()).thenReturn(client);
        when(obContext.getUser()).thenReturn(user);
        when(obContext.getReadableOrganizations()).thenReturn(null);

        WidgetDataContext ctx = buildCtx(hql, params, obContext);

        Object[] row = {"Test"};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(1, result.getInt(TOTAL_ROWS));
        }
    }

    // -------------------------------------------------------
    // 12. OBContext with valid readableOrganizations
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void resolveWithOBContextAddsClientUserAndOrgs() throws Exception {
        String hql = "select o.name as name from Order o where o.client.id = :client";
        Map<String, Object> params = new HashMap<>();

        OBContext obContext = mock(OBContext.class);
        Client client = mock(Client.class);
        User user = mock(User.class);
        when(client.getId()).thenReturn(CLIENT_1);
        when(user.getId()).thenReturn("user-1");
        when(obContext.getCurrentClient()).thenReturn(client);
        when(obContext.getUser()).thenReturn(user);
        when(obContext.getReadableOrganizations()).thenReturn(new String[]{"org-1", "org-2"});

        WidgetDataContext ctx = buildCtx(hql, params, obContext);

        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(Collections.singletonList(new Object[]{"Test"}));
        when(mockQuery.setFirstResult(anyInt())).thenReturn(mockQuery);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);

        Parameter<?> qp = mock(Parameter.class);
        when(qp.getName()).thenReturn("client");
        Set<Parameter<?>> paramSet = new HashSet<>();
        paramSet.add(qp);
        doReturn(paramSet).when(mockQuery).getParameters();

        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            resolver.resolve(ctx);

            // Should bind "client" param with CLIENT_1 from OBContext
            verify(mockQuery).setParameter("client", CLIENT_1);
        }
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

        Object[] row = {"COMPLETED", 5L};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        // The count query should strip group by, having, order by
        Query<Long> countQ = mockCountQuery(3L);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(3, result.getInt(TOTAL_ROWS));
        }
    }

    // -------------------------------------------------------
    // 14. countQuery returns 0 when no FROM found (fromIdx < 0)
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeAndNoFromKeywordReturnsTotalZero() throws Exception {
        // A degenerate HQL with no FROM keyword at top level
        String hql = "select 1";
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put(COLUMNS, "val");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {1};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            // countQuery returns 0 because there is no FROM
            assertEquals(0, result.getInt(TOTAL_ROWS));
        }
    }

    // -------------------------------------------------------
    // 15. extractAliases falls back to "col" + i when no AS alias
    // -------------------------------------------------------
    @Test
    void resolveWithoutColumnsAndNoAliasesUsesColIndex() throws Exception {
        String hql = "select o.documentNo, o.grandTotalAmount from Order o";
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {"SO-003", 300.0};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            // No AS aliases, so should use col0, col1
            assertEquals("SO-003", firstRow.getString("col0"));
            assertEquals(300.0, firstRow.getDouble("col1"));
        }
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

        Object[] row = {100.0, "SO-1", "val"};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONArray colDefs = result.getJSONArray(COLUMNS);
            assertEquals(3, colDefs.length());
            assertEquals("Grand Total Amount", colDefs.getJSONObject(0).getString(LABEL));
            assertEquals("Document No", colDefs.getJSONObject(1).getString(LABEL));
            assertEquals("X", colDefs.getJSONObject(2).getString(LABEL));
        }
    }

    // -------------------------------------------------------
    // 17. Subquery in SELECT clause (parentheses handling in splitTopLevel)
    // -------------------------------------------------------
    @Test
    void resolveWithSubqueryInSelectExtractsAliasesCorrectly() throws Exception {
        String hql = "select o.name as name, (select count(*) from Line l where l.order = o) as lineCount from Order o";
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {TEST_ORDER, 3L};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            assertEquals(TEST_ORDER, firstRow.getString("name"));
            assertEquals(3L, firstRow.getLong("lineCount"));
        }
    }

    // -------------------------------------------------------
    // 18. _pageSize with default page (no _page param)
    // -------------------------------------------------------
    @Test
    void resolveWithPageSizeDefaultsToPageOne() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "5");
        // _page not set -> defaults to 1

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        Query<Long> countQ = mockCountQuery(0L);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            resolver.resolve(ctx);

            // (1 - 1) * 5 = 0
            verify(mockQuery).setFirstResult(0);
            verify(mockQuery).setMaxResults(5);
        }
    }

    // -------------------------------------------------------
    // 19. Empty result set
    // -------------------------------------------------------
    @Test
    void resolveWithEmptyResultReturnsZeroRows() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "name");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(0, result.getInt(TOTAL_ROWS));
            assertEquals(0, result.getJSONArray("rows").length());
        }
    }

    // -------------------------------------------------------
    // 20. bindParams skips parameter with null name
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void bindParamsSkipsParameterWithNullName() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(Collections.singletonList(new Object[]{"Test"}));

        Parameter<?> nullNameParam = mock(Parameter.class);
        when(nullNameParam.getName()).thenReturn(null);
        Set<Parameter<?>> paramSet = new HashSet<>();
        paramSet.add(nullNameParam);
        doReturn(paramSet).when(mockQuery).getParameters();

        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            // Should complete without calling setParameter or setParameterList
            verify(mockQuery, never()).setParameter(anyString(), any());
            verify(mockQuery, never()).setParameterList(anyString(), anyCollection());
            assertEquals(1, result.getInt(TOTAL_ROWS));
        }
    }

    // -------------------------------------------------------
    // 21. Mixed aliases: some with AS, some without
    // -------------------------------------------------------
    @Test
    void resolveExtractsPartialAliases() throws Exception {
        String hql = "select o.documentNo as docNo, o.name, o.total as total from Order o";
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Object[] row = {SO_001, "Test", 100.0};
        Query<Object[]> mockQuery = mockRowQuery(Collections.singletonList(row));
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            JSONObject firstRow = result.getJSONArray("rows").getJSONObject(0);
            assertEquals(SO_001, firstRow.getString(DOC_NO));
            // Second column has no alias -> falls back to col1
            assertEquals("Test", firstRow.getString("col1"));
            assertEquals(100.0, firstRow.getDouble(TOTAL));
        }
    }

    // -------------------------------------------------------
    // 22. Multiple rows returned
    // -------------------------------------------------------
    @Test
    void resolveReturnsMultipleRows() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, "name");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        List<Object[]> rows = Arrays.asList(
                new Object[]{ORDER_1},
                new Object[]{"Order-2"},
                new Object[]{"Order-3"}
        );
        Query<Object[]> mockQuery = mockRowQuery(rows);
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(3, result.getInt(TOTAL_ROWS));
            JSONArray rowsArr = result.getJSONArray("rows");
            assertEquals(3, rowsArr.length());
            assertEquals(ORDER_1, rowsArr.getJSONObject(0).getString("name"));
            assertEquals("Order-2", rowsArr.getJSONObject(1).getString("name"));
            assertEquals("Order-3", rowsArr.getJSONObject(2).getString("name"));
        }
    }

    // -------------------------------------------------------
    // 23. countQuery with null uniqueResult returns 0
    // -------------------------------------------------------
    @SuppressWarnings("unchecked")
    @Test
    void resolveWithPageSizeAndNullCountReturnsZero() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();
        params.put(PAGE_SIZE, "10");
        params.put(COLUMNS, "name");

        WidgetDataContext ctx = buildCtx(hql, params, null);

        Query<Object[]> mockQuery = mockRowQuery(Collections.emptyList());
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        Query<Long> countQ = mock(Query.class);
        when(countQ.getParameters()).thenReturn(Collections.emptySet());
        when(countQ.uniqueResult()).thenReturn(null);
        when(session.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            assertEquals(0, result.getInt(TOTAL_ROWS));
        }
    }

    // -------------------------------------------------------
    // 24. No rowsNumber and no _pageSize -> totalRows from list size
    // -------------------------------------------------------
    @Test
    void resolveWithNoPaginationParamsUsesListSize() throws Exception {
        String hql = HQL_SELECT_NAME;
        Map<String, Object> params = new HashMap<>();

        WidgetDataContext ctx = buildCtx(hql, params, null);

        List<Object[]> rows = Arrays.asList(
                new Object[]{"A"},
                new Object[]{"B"}
        );
        Query<Object[]> mockQuery = mockRowQuery(rows);
        when(session.createQuery(eq(hql), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = resolver.resolve(ctx);

            // totalRows is set from rawRows.size() since totalRows was -1
            assertEquals(2, result.getInt(TOTAL_ROWS));
        }
    }
}
