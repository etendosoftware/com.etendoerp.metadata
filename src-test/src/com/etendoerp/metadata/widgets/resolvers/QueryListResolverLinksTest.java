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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.metadata.widgets.WidgetDataContext;

/**
 * Tests for the "links" fixed param of {@link QueryListResolver}: the columns that navigate to a record of another
 * entity, resolved to the window and the header tab of that entity.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryListResolverLinksTest {

    private static final String HQL = "select p.name as product, p.id as pid from Product p";
    private static final String COLUMNS_PARAM = "product,pid";
    private static final String REFERENCED_WINDOW_ID = "referencedWindowId";
    private static final String REFERENCED_TAB_ID = "referencedTabId";
    private static final String LINKS = "links";
    private static final String COLUMNS = "columns";
    private static final String ROWS = "rows";
    private static final String PRODUCT = "product";
    private static final String PRODUCT_ID = "p-1";
    private static final String TAB_ID = "tab-1";
    private static final String WINDOW_ID = "win-1";
    private static final String LINK_FOR_PRODUCT = "{\"product\":{\"idCol\":\"pid\",\"entity\":\"Product\"}}";

    private final QueryListResolver resolver = new QueryListResolver();

    @Mock
    private OBDal obDal;
    @Mock
    private Session session;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private Entity entity;

    @BeforeEach
    void setUp() {
        when(entity.getTableName()).thenReturn("M_Product");
        when(modelProvider.getEntity(anyString())).thenReturn(entity);
    }

    private WidgetDataContext ctx(String links) {
        Map<String, Object> params = new HashMap<>();
        params.put(COLUMNS, COLUMNS_PARAM);
        if (links != null) {
            params.put(LINKS, links);
        }
        Map<String, Object> classData = new HashMap<>();
        classData.put("4", HQL);
        return new WidgetDataContext("inst-1", Collections.emptyMap(), classData, params, null, null);
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> rowQuery() {
        Query<Object[]> query = mock(Query.class);
        when(query.getParameters()).thenReturn(Collections.emptySet());
        when(query.list()).thenReturn(Collections.singletonList(new Object[] { "Widget", PRODUCT_ID }));
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        return query;
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> tabQuery(Object[] result) {
        Query<Object[]> query = mock(Query.class);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(result);
        return query;
    }

    @FunctionalInterface
    private interface Action {
        @SuppressWarnings("java:S112")
        void run() throws Exception;
    }

    /** Runs the action with OBDal and ModelProvider mocked; the tab lookup answers with {@code tabQuery}. */
    private void withMocks(Query<Object[]> tabQuery, Action action) throws Exception {
        Query<Object[]> rows = rowQuery();
        when(session.createQuery(eq(HQL), eq(Object[].class))).thenReturn(rows);
        when(session.createQuery(org.mockito.ArgumentMatchers.contains("ADTab"), eq(Object[].class)))
                .thenReturn(tabQuery);
        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class);
             MockedStatic<ModelProvider> models = mockStatic(ModelProvider.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);
            models.when(ModelProvider::getInstance).thenReturn(modelProvider);
            action.run();
        }
    }

    @Test
    void aLinkedColumnCarriesTheWindowAndTabOfItsEntity() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject result = resolver.resolve(ctx(LINK_FOR_PRODUCT));

            JSONObject column = result.getJSONArray(COLUMNS).getJSONObject(0);
            assertEquals(PRODUCT, column.getString("name"));
            assertEquals(WINDOW_ID, column.getString(REFERENCED_WINDOW_ID));
            assertEquals(TAB_ID, column.getString(REFERENCED_TAB_ID));
        });
    }

    @Test
    void theRowKeepsTheIdOfTheLinkedRecord() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject row = resolver.resolve(ctx(LINK_FOR_PRODUCT)).getJSONArray(ROWS).getJSONObject(0);

            assertEquals(PRODUCT_ID, row.getString("product_id"));
            assertEquals("Widget", row.getString(PRODUCT));
        });
    }

    @Test
    void theIdColumnItselfIsNotListedAsAColumn() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONArray columns = resolver.resolve(ctx(LINK_FOR_PRODUCT)).getJSONArray(COLUMNS);

            assertEquals(1, columns.length());
        });
    }

    @Test
    void anEntityWithoutHeaderTabGetsNoLink() throws Exception {
        withMocks(tabQuery(null), () -> {
            JSONObject result = resolver.resolve(ctx(LINK_FOR_PRODUCT));

            JSONObject column = result.getJSONArray(COLUMNS).getJSONObject(0);
            assertFalse(column.has(REFERENCED_WINDOW_ID));
            assertFalse(result.getJSONArray(ROWS).getJSONObject(0).has("product_id"));
        });
    }

    @Test
    void anUnknownEntityGetsNoLink() throws Exception {
        when(modelProvider.getEntity(anyString())).thenThrow(new IllegalArgumentException("Unknown entity"));

        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject column = resolver.resolve(ctx(LINK_FOR_PRODUCT)).getJSONArray(COLUMNS).getJSONObject(0);

            assertFalse(column.has(REFERENCED_TAB_ID));
        });
    }

    @Test
    void aMalformedLinksParamIsIgnored() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject result = resolver.resolve(ctx("{not json"));

            assertFalse(result.getJSONArray(COLUMNS).getJSONObject(0).has(REFERENCED_WINDOW_ID));
            assertEquals(1, result.getJSONArray(ROWS).length());
        });
    }

    @Test
    void aLinkMissingItsIdColumnIsIgnored() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject result = resolver.resolve(ctx("{\"product\":{\"entity\":\"Product\"}}"));

            assertFalse(result.getJSONArray(COLUMNS).getJSONObject(0).has(REFERENCED_WINDOW_ID));
        });
    }

    @Test
    void withoutLinksTheColumnsAreAsBefore() throws Exception {
        withMocks(tabQuery(new Object[] { TAB_ID, WINDOW_ID }), () -> {
            JSONObject result = resolver.resolve(ctx(null));

            assertFalse(result.getJSONArray(COLUMNS).getJSONObject(0).has(REFERENCED_WINDOW_ID));
            assertTrue(result.getJSONArray(ROWS).getJSONObject(0).has(PRODUCT));
        });
    }
}
