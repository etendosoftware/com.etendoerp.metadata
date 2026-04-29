package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryListResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void getTypeReturnsQueryList() {
        assertEquals("QUERY_LIST", new QueryListResolver().getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveReturnsRowsAndColumns() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("4")).thenReturn("select o.documentNo, o.grandTotalAmount from Order o");
        when(ctx.param("columns")).thenReturn("docNo,total");
        when(ctx.param("rowsNumber")).thenReturn(null);
        when(ctx.getParams()).thenReturn(new HashMap<>());

        Object[] row = { "SO-001", 500.0 };
        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.list()).thenReturn(Collections.singletonList(row));
        when(session.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new QueryListResolver().resolve(ctx);
            assertEquals(1, result.getInt("totalRows"));
            assertEquals("SO-001", result.getJSONArray("rows").getJSONObject(0).getString("docNo"));
        }
    }
}
