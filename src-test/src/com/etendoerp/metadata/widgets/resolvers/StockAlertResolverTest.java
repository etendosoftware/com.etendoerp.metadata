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

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAlertResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void getType_returnsSTOCK_ALERT() {
        assertEquals("STOCK_ALERT", new StockAlertResolver().getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolve_returnsItemsWithLowStock() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param("rowsNumber")).thenReturn(null);

        Object[] row = { "Producto A", "prod-1", new BigDecimal("10"), new BigDecimal("3") };
        Query<Object[]> mockQuery = mock(Query.class);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Collections.singletonList(row));
        when(session.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new StockAlertResolver().resolve(ctx);
            assertEquals(1, result.getJSONArray("items").length());
            assertEquals("Producto A", result.getJSONArray("items").getJSONObject(0).getString("productName"));
        }
    }
}
