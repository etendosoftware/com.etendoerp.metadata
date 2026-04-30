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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationResolverTest {
    @Mock OBDal    obDal;
    @Mock Session  session;
    @Mock OBContext obContext;
    @Mock User     mockUser;

    @Test
    void getTypeReturnsNotification() {
        assertEquals("NOTIFICATION", new NotificationResolver().getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveReturnsMappedItems() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.getObContext()).thenReturn(obContext);
        when(obContext.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn("user-1");
        when(ctx.param("rowsNumber")).thenReturn(null);

        Object[] row = { "Se ha calculado los costes", "normal", new java.util.Date() };

        Query<Object[]> itemsQuery = mock(Query.class);
        when(itemsQuery.setParameter(anyString(), any())).thenReturn(itemsQuery);
        when(itemsQuery.setMaxResults(anyInt())).thenReturn(itemsQuery);
        when(itemsQuery.list()).thenReturn(Collections.singletonList(row));

        Query<Long> countQuery = mock(Query.class);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.uniqueResult()).thenReturn(7L);

        when(session.createQuery(contains("AN_Note"), eq(Object[].class))).thenReturn(itemsQuery);
        when(session.createQuery(contains("count("), eq(Long.class))).thenReturn(countQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new NotificationResolver().resolve(ctx);
            JSONArray items = result.getJSONArray("items");
            assertEquals(1, items.length());
            assertEquals(7, result.getInt("totalCount"));
        }
    }
}
