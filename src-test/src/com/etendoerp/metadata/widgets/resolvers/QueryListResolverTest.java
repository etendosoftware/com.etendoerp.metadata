package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryListResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void getType_returnsQUERY_LIST() {
        assertEquals("QUERY_LIST", new QueryListResolver().getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolve_returnsRowsAndColumns() throws Exception {
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
