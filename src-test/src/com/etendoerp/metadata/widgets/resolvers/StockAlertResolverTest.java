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
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockAlertResolverTest {
    @Mock OBDal   obDal;
    @Mock Session session;

    @Test
    void getTypeReturnsStockAlert() {
        assertEquals("STOCK_ALERT", new StockAlertResolver().getType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveReturnsItemsWithLowStock() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.param("rowsNumber")).thenReturn(null);

        Object[] row = { "Producto A", "prod-1", new BigDecimal("10"), new BigDecimal("3") };
        NativeQuery mockQuery = mock(NativeQuery.class);
        when(mockQuery.setMaxResults(anyInt())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Collections.singletonList(row));
        when(session.createNativeQuery(anyString())).thenReturn(mockQuery);

        try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new StockAlertResolver().resolve(ctx);
            assertEquals(1, result.getJSONArray("items").length());
            assertEquals("Producto A", result.getJSONArray("items").getJSONObject(0).getString("productName"));
        }
    }
}
