package com.etendoerp.metadata.widgets;

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
import org.openbravo.model.ad.system.Client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardLayoutResolverTest {

    private static final String USER_123 = USER_123;

    @Mock OBContext mockContext;
    @Mock OBDal mockOBDal;
    @Mock Session mockSession;
    @Mock User mockUser;
    @Mock Client mockClient;

    @SuppressWarnings("unchecked")
    @Test
    void resolveUserLayerOverridesSystem() throws Exception {
        // SYSTEM row for widget class "wc1"
        Object[] systemRow = { "sys-instance-id", "wc1", "SYSTEM", null, 0, 0, 2, 1, "Y", 10, null };
        // USER row for same widget class
        Object[] userRow   = { "usr-instance-id", "wc1", "USER",   USER_123, 1, 0, 2, 1, "Y", 10, null };

        Query<Object[]> mockQuery = mock(Query.class);
        when(mockSession.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Arrays.asList(systemRow, userRow));

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(USER_123);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockClient.getId()).thenReturn("client-123");

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(mockContext);
            dalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            DashboardLayoutResolver resolver = new DashboardLayoutResolver();
            JSONArray result = resolver.resolve();

            // Only 1 entry per widget class — USER layer wins
            assertEquals(1, result.length());
            JSONObject entry = result.getJSONObject(0);
            assertEquals("usr-instance-id", entry.getString("instanceId"));
            assertEquals("USER", entry.getString("layer"));
        }
    }

    @Test
    void resolveSystemRowUsedWhenNoOverride() throws Exception {
        Object[] systemRow = { "sys-id", "wc1", "SYSTEM", null, 0, 0, 2, 1, "Y", 10, null };

        @SuppressWarnings("unchecked")
        Query<Object[]> mockQuery = mock(Query.class);
        when(mockSession.createQuery(anyString(), eq(Object[].class))).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Collections.singletonList(systemRow));

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(USER_123);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockClient.getId()).thenReturn("client-123");

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(mockContext);
            dalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            DashboardLayoutResolver resolver = new DashboardLayoutResolver();
            JSONArray result = resolver.resolve();

            assertEquals(1, result.length());
            assertEquals("sys-id", result.getJSONObject(0).getString("instanceId"));
        }
    }
}
