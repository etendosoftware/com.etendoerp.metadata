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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KPIResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void getTypeReturnsKpi() {
        assertEquals("KPI", new KPIResolver().getType());
    }

    @Test
    void resolveReturnsValueFromHqlQuery() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("4")).thenReturn("select 95 from dual");
        when(ctx.param("label")).thenReturn("Facturas completadas");
        when(ctx.param("unit")).thenReturn("%");
        when(ctx.param("trend")).thenReturn(null);
        when(ctx.param("chartType")).thenReturn(null);
        when(ctx.getParams()).thenReturn(new java.util.HashMap<>());

        @SuppressWarnings("unchecked")
        Query<Object> mockQuery = mock(Query.class);
        when(mockQuery.uniqueResult()).thenReturn(95L);
        when(session.createQuery(anyString(), eq(Object.class))).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new KPIResolver().resolve(ctx);
            assertEquals(95, result.getInt("value"));
        }
    }
}
