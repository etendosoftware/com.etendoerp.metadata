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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void resolve_missingProcessId_returnsError() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param("processId")).thenReturn(null);

        JSONObject result = new ProcessResolver().resolve(ctx);
        assertEquals("error", result.getString("status"));
        assertTrue(result.getString("message").contains("processId"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolve_validProcessId_returnsSuccess() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param("processId")).thenReturn("proc1");

        Object[] row = { "proc1", "My Process" };
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.uniqueResult()).thenReturn(row);
        when(session.createQuery(anyString(), eq(Object[].class))).thenReturn(q);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new ProcessResolver().resolve(ctx);
            assertEquals("success", result.getString("status"));
            assertEquals("My Process", result.getJSONObject("result").getString("name"));
        }
    }
}
