package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoritesResolverTest {
    @Mock OBDal    obDal;
    @Mock Session  session;
    @Mock OBContext obContext;
    @Mock User     mockUser;

    @SuppressWarnings("unchecked")
    @Test
    void resolve_returnsItemsArray() throws Exception {
        when(obContext.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn("userId1");

        Object[] row = { "Sales Orders", null, "window", "win-1" };
        Query<Object[]> query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.list()).thenReturn(Collections.singletonList(row));
        when(session.createQuery(anyString(), eq(Object[].class))).thenReturn(query);

        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.getObContext()).thenReturn(obContext);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new FavoritesResolver().resolve(ctx);
            assertTrue(result.has("items"));
            assertEquals(1, result.getJSONArray("items").length());
        }
    }
}
