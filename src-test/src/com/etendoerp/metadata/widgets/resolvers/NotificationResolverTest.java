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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.UsedByLink;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.alert.AlertRule;

import com.etendoerp.metadata.widgets.WidgetDataContext;

/**
 * Tests the alert-based {@link NotificationResolver}: rule lookup, filter clauses, merge, order and limit.
 */
class NotificationResolverTest {

    private static final String RULE_A = "RULE_A";
    private static final String RULE_B = "RULE_B";
    private static final String ITEMS = "items";
    private static final String TOTAL = "totalCount";
    private static final String BROKEN_CLAUSE = "AD_ORG_ID = @BROKEN@";

    private MockedStatic<OBDal> dalStatic;
    private MockedStatic<RequestContext> requestStatic;
    private Session session;
    private WidgetDataContext ctx;
    private NativeQuery<Object> itemsQuery;
    private NativeQuery<Object> countQuery;

    /**
     * Mocks the DAL session, the current user and the alert queries.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        OBDal obDal = mock(OBDal.class);
        when(obDal.getSession()).thenReturn(session);
        dalStatic = mockStatic(OBDal.class);
        dalStatic.when(OBDal::getInstance).thenReturn(obDal);

        RequestContext requestContext = mock(RequestContext.class);
        requestStatic = mockStatic(RequestContext.class);
        requestStatic.when(RequestContext::get).thenReturn(requestContext);

        OBContext obContext = mock(OBContext.class);
        User user = mock(User.class);
        Role role = mock(Role.class);
        when(user.getId()).thenReturn("USER");
        when(role.getId()).thenReturn("ROLE");
        when(obContext.getUser()).thenReturn(user);
        when(obContext.getRole()).thenReturn(role);
        when(obContext.getReadableClients()).thenReturn(new String[] { "0" });
        when(obContext.getReadableOrganizations()).thenReturn(new String[] { "0" });
        ctx = mock(WidgetDataContext.class);
        when(ctx.getObContext()).thenReturn(obContext);

        itemsQuery = mock(NativeQuery.class, RETURNS_SELF);
        countQuery = mock(NativeQuery.class, RETURNS_SELF);
        when(session.createNativeQuery(startsWith(NotificationResolver.ITEMS_SQL))).thenReturn(itemsQuery);
        when(session.createNativeQuery(startsWith(NotificationResolver.COUNT_SQL))).thenReturn(countQuery);
    }

    /**
     * Releases the static mocks.
     */
    @AfterEach
    void tearDown() {
        requestStatic.close();
        dalStatic.close();
    }

    @SuppressWarnings("unchecked")
    private void givenRules(AlertRule... rules) {
        Query<AlertRule> rulesQuery = mock(Query.class, RETURNS_SELF);
        when(rulesQuery.list()).thenReturn(Arrays.asList(rules));
        when(session.createQuery(eq(NotificationResolver.RULES_HQL), eq(AlertRule.class))).thenReturn(rulesQuery);
    }

    /** Makes the conversion of {@link #BROKEN_CLAUSE} fail, as a clause with an unknown variable does. */
    private static MockedConstruction<UsedByLink> brokenFilterClauses() {
        return mockConstruction(UsedByLink.class, (link, context) -> when(
            link.getWhereClause(any(), anyString(), eq(BROKEN_CLAUSE))).thenThrow(new ServletException("broken")));
    }

    private static AlertRule rule(String id, String filterClause) {
        AlertRule rule = mock(AlertRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getName()).thenReturn(id + " name");
        when(rule.getFilterClause()).thenReturn(filterClause);
        return rule;
    }

    @Test
    void reportsTheNotificationTypeAndIsAlwaysAvailable() {
        NotificationResolver resolver = new NotificationResolver();
        assertEquals("NOTIFICATION", resolver.getType());
        assertTrue(resolver.isAvailable());
    }

    /**
     * @throws Exception if the JSON cannot be built
     */
    @Test
    void returnsAnEmptyListWhenTheUserReceivesNoAlertRule() throws Exception {
        givenRules();

        JSONObject result = new NotificationResolver().resolve(ctx);

        assertEquals(0, result.getJSONArray(ITEMS).length());
        assertEquals(0, result.getLong(TOTAL));
        verify(session, never()).createNativeQuery(anyString());
    }

    /**
     * @throws Exception if the JSON cannot be built
     */
    @Test
    void listsPendingAlertsWithTheirRuleNameNewestFirst() throws Exception {
        givenRules(rule(RULE_A, null));
        Date older = new Date(1_000L);
        Date newer = new Date(2_000L);
        when(itemsQuery.list()).thenReturn(Arrays.<Object>asList(
            new Object[] { "Old alert", RULE_A, older }, new Object[] { "New alert", RULE_A, newer }));
        when(countQuery.uniqueResult()).thenReturn(5L);

        JSONObject result = new NotificationResolver().resolve(ctx);
        JSONArray items = result.getJSONArray(ITEMS);

        assertEquals(2, items.length());
        assertEquals("New alert", items.getJSONObject(0).getString("text"));
        assertEquals(RULE_A + " name", items.getJSONObject(0).getString("title"));
        assertEquals(NotificationResolver.NORMAL_PRIORITY, items.getJSONObject(0).getString("priority"));
        assertEquals(newer.toInstant().toString(), items.getJSONObject(0).getString("time"));
        assertEquals(5, result.getLong(TOTAL));
    }

    /**
     * @throws Exception if the JSON cannot be built
     */
    @Test
    void hidesTheAlertsOfARuleWhoseFilterClauseCannotBeConverted() throws Exception {
        givenRules(rule(RULE_A, null), rule(RULE_B, BROKEN_CLAUSE));
        when(itemsQuery.list()).thenReturn(Collections.<Object>singletonList(new Object[] { "Alert", RULE_A, null }));
        when(countQuery.uniqueResult()).thenReturn(1L);

        try (MockedConstruction<UsedByLink> ignored = brokenFilterClauses()) {
            JSONObject result = new NotificationResolver().resolve(ctx);

            assertEquals(1, result.getJSONArray(ITEMS).length());
            assertEquals(1, result.getLong(TOTAL));
        }
    }

    @Test
    void keepsTheRequestedNumberOfRowsOrFallsBackToTheDefault() {
        assertEquals(3, NotificationResolver.parseRows("3"));
        assertEquals(NotificationResolver.DEFAULT_ROWS, NotificationResolver.parseRows(null));
        assertEquals(NotificationResolver.DEFAULT_ROWS, NotificationResolver.parseRows("many"));
    }

    @Test
    void addsNoConditionForRulesWithoutFilterClause() {
        assertEquals("", new NotificationResolver().toFilterSql(""));
    }

    @Test
    void returnsNullForAFilterClauseThatCannotBeConverted() {
        try (MockedConstruction<UsedByLink> ignored = brokenFilterClauses()) {
            assertNull(new NotificationResolver().toFilterSql(BROKEN_CLAUSE));
        }
    }

    /**
     * @throws Exception if the JSON cannot be built
     */
    @Test
    void limitsTheListToTheRequestedRows() throws Exception {
        givenRules(rule(RULE_A, null));
        when(ctx.param(NotificationResolver.PARAM_ROWS)).thenReturn("1");
        List<Object> rows = Arrays.<Object>asList(new Object[] { "One", RULE_A, new Date(1L) },
            new Object[] { "Two", RULE_A, new Date(2L) });
        when(itemsQuery.list()).thenReturn(rows);
        when(countQuery.uniqueResult()).thenReturn(2L);

        JSONObject result = new NotificationResolver().resolve(ctx);

        assertEquals(1, result.getJSONArray(ITEMS).length());
        assertEquals(2, result.getLong(TOTAL));
    }
}
