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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessResolverTest {
    private static final String PROCESS_ID = "processId";

    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void resolveMissingProcessIdReturnsError() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param(PROCESS_ID)).thenReturn(null);

        JSONObject result = new ProcessResolver().resolve(ctx);
        assertEquals("error", result.getString("status"));
        assertTrue(result.getString("message").contains(PROCESS_ID));
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveValidProcessIdReturnsSuccess() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param(PROCESS_ID)).thenReturn("proc1");

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
