package com.etendoerp.metadata.service;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.hibernate.Session;
import org.hibernate.query.Query;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WidgetClassesServiceTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext            obContext;
    @Mock OBDal                obDal;
    @Mock Session              session;
    @Mock Query<Object[]>      query;

    @Test
    void getClasses_returnsClassesArray() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        Object[] classRow = { "classId1", "my-widget", "KPI", "My Widget",
                              "A test widget", 2, 1, 30 };
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetClass")), eq(Object[].class)))
                .thenReturn(query);
        when(query.list()).thenReturn(Collections.singletonList(classRow));

        // params query returns empty list
        Query<Object[]> paramQuery = mock();
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetParam")), eq(Object[].class)))
                .thenReturn(paramQuery);
        when(paramQuery.list()).thenReturn(Collections.emptyList());

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal>     dalStatic = mockStatic(OBDal.class)) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            WidgetClassesService svc = new WidgetClassesService(request, response);
            svc.process();

            String output = sw.toString();
            JSONObject result = new JSONObject(output);
            assertTrue(result.has("classes"));
            assertEquals(1, result.getJSONArray("classes").length());
            assertEquals("KPI", result.getJSONArray("classes").getJSONObject(0).getString("type"));
        }
    }
}
