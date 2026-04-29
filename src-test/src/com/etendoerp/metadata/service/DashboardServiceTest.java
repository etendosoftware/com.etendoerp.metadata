package com.etendoerp.metadata.service;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.etendoerp.metadata.widgets.DashboardLayoutResolver;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock OBContext obContext;
    @Mock OBDal     obDal;
    @Mock Session   session;
    @Mock Query<Object[]> enrichQuery;

    @Test
    void getLayoutReturnsWidgetArray() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/dashboard/layout");

        JSONArray mockWidgets = new JSONArray();
        mockWidgets.put(new JSONObject().put("instanceId", "id1").put("widgetClassId", "cls1"));

        // enrichWithClassData query returns null (no class data needed for this assertion)
        when(session.createQuery(argThat(s -> s != null && s.contains("EtmetaWidgetClass")), eq(Object[].class)))
                .thenReturn(enrichQuery);
        when(enrichQuery.uniqueResult()).thenReturn(null);

        try (MockedStatic<OBContext> ctxStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal>     dalStatic = mockStatic(OBDal.class);
             MockedConstruction<DashboardLayoutResolver> resolverMock =
                     mockConstruction(DashboardLayoutResolver.class, (m, ctx) ->
                             when(m.resolve()).thenReturn(mockWidgets))) {
            ctxStatic.when(OBContext::getOBContext).thenReturn(obContext);
            dalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            DashboardService svc = new DashboardService(request, response);
            svc.process();

            String output = sw.toString();
            assertTrue(output.contains("id1"));
        }
    }
}
