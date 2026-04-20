package com.etendoerp.metadata.service;

import com.etendoerp.metadata.widgets.*;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WidgetDataServiceTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext obContext;
    @Mock OBDal     obDal;
    @Mock Session   session;

    @SuppressWarnings("unchecked")
    @Test
    void process_delegatesToRegistryResolver() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(request.getPathInfo()).thenReturn("/widget/some-instance-id/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");

        // Instance row: [id, classId, paramsJson]
        Object[] instanceRow = { "some-instance-id", "cls1", null };
        // Class row: [id, type, resolverClass, externalDataUrl, hqlQuery]
        Object[] classRow    = { "cls1", "KPI", null, null, null };

        Query<Object[]> instanceQuery = mock(Query.class);
        Query<Object[]> classQuery    = mock(Query.class);
        Query<Object[]> paramsQuery   = mock(Query.class);

        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.uniqueResult()).thenReturn(instanceRow);

        when(classQuery.setParameter(anyString(), any())).thenReturn(classQuery);
        when(classQuery.uniqueResult()).thenReturn(classRow);

        when(paramsQuery.setParameter(anyString(), any())).thenReturn(paramsQuery);
        when(paramsQuery.list()).thenReturn(Collections.emptyList());

        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaDashboardWidget")), eq(Object[].class)))
                .thenReturn(instanceQuery);
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetClass")), eq(Object[].class)))
                .thenReturn(classQuery);
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetParam")), eq(Object[].class)))
                .thenReturn(paramsQuery);

        WidgetDataResolver mockResolver = mock(WidgetDataResolver.class);
        JSONObject mockData = new JSONObject().put("value", 95);
        when(mockResolver.getType()).thenReturn("KPI");
        when(mockResolver.resolve(any())).thenReturn(mockData);

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal>     dalStatic = mockStatic(OBDal.class)) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            WidgetDataService svc = new WidgetDataService(request, response);
            WidgetResolverRegistry registry = new WidgetResolverRegistry();
            registry.register(mockResolver);
            svc.setRegistry(registry);
            svc.process();

            String out = sw.toString();
            assertTrue(out.contains("widgetInstanceId"));
            assertTrue(out.contains("95"));
        }
    }
}
