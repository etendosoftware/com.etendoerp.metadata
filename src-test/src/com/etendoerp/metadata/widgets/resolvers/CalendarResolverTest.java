package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarResolverTest {

    @Mock OBDal    obDal;
    @Mock Session  session;
    @Mock OBContext obContext;
    @Mock Client   mockClient;

    // ── helper: build a period row: [id, name, startDate, endDate, openClose]
    private Object[] periodRow(String id, String name, String start, String end, String oc)
            throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return new Object[]{ id, name, sdf.parse(start), sdf.parse(end), oc };
    }

    // ── helper: build a non-business-day row: [name, date]
    private Object[] nbdRow(String name, String date) throws Exception {
        return new Object[]{ name, new SimpleDateFormat("yyyy-MM-dd").parse(date) };
    }

    // ── helper: mock a Query<Object[]> that returns given rows
    @SuppressWarnings("unchecked")
    private Query<Object[]> mockQuery(Object[]... rows) {
        Query<Object[]> q = mock(Query.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.list()).thenReturn(Arrays.asList(rows));
        return q;
    }

    // ── helper: mock a single-result Query<Object[]>
    @SuppressWarnings("unchecked")
    private Query<Object[]> mockQuerySingle(Object[] row) {
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.list()).thenReturn(Collections.singletonList(row));
        return q;
    }

    @SuppressWarnings("unchecked")
    private Query<Object[]> mockQueryEmpty() {
        Query<Object[]> q = mock(Query.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setMaxResults(anyInt())).thenReturn(q);
        when(q.list()).thenReturn(Collections.emptyList());
        return q;
    }

    // ── shared context mock
    private WidgetDataContext ctxWithParams(String dateFrom, String dateTo,
                                            String includePeriods, String includeNbd) {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.getObContext()).thenReturn(obContext);
        when(obContext.getCurrentClient()).thenReturn(mockClient);
        when(mockClient.getId()).thenReturn("clientId1");
        lenient().when(ctx.param("dateFrom")).thenReturn(dateFrom);
        lenient().when(ctx.param("dateTo")).thenReturn(dateTo);
        lenient().when(ctx.param("includePeriods")).thenReturn(includePeriods);
        lenient().when(ctx.param("includeNonBusinessDays")).thenReturn(includeNbd);
        return ctx;
    }

    // ─────────────────────────────────────────────────

    @Test
    void getType_returnsCALENDAR() {
        assertEquals("CALENDAR", new CalendarResolver().getType());
    }

    @Test
    void currentPeriod_populatedWhenPeriodCoversToday() throws Exception {
        Object[] pRow = periodRow("pid1", "March 2026", "2026-03-01", "2026-03-31", "O");
        // currentPeriod query returns a row; periods-in-range also returns it; nbd empty
        Query<Object[]> currentQ = mockQuerySingle(pRow);
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-03-01", "2026-03-31", "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertFalse(result.isNull("currentPeriod"));
            assertEquals("March 2026", result.getJSONObject("currentPeriod").getString("name"));
            assertEquals("O", result.getJSONObject("currentPeriod").getString("openClose"));
        }
    }

    @Test
    void currentPeriod_nullWhenNoPeriodCoversToday() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-03-01", "2026-03-31", "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertTrue(result.isNull("currentPeriod"));
        }
    }

    @Test
    void entries_containsPeriodEntriesWithId() throws Exception {
        Object[] pRow = periodRow("pid1", "March 2026", "2026-03-01", "2026-03-31", "O");
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-03-01", "2026-03-31", "true", "false");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray("entries");
            assertEquals(1, entries.length());
            JSONObject e = entries.getJSONObject(0);
            assertEquals("PERIOD", e.getString("type"));
            assertEquals("pid1",   e.getString("id"));
            assertEquals("March 2026", e.getString("name"));
        }
    }

    @Test
    void entries_containsNonBusinessDayEntries() throws Exception {
        Object[] nbdRow = nbdRow("Easter", "2026-04-03");
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery(nbdRow);

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-04-01", "2026-04-30", "false", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray("entries");
            assertEquals(1, entries.length());
            JSONObject e = entries.getJSONObject(0);
            assertEquals("NON_BUSINESS_DAY", e.getString("type"));
            assertEquals("Easter", e.getString("name"));
        }
    }

    @Test
    void entries_emptyWhenBothFlagsAreFalse() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        // periods and nbd queries should NOT be called when both flags are false

        WidgetDataContext ctx = ctxWithParams("2026-03-01", "2026-03-31", "false", "false");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertEquals(0, result.getJSONArray("entries").length());
        }
    }

    @Test
    void entries_sortedChronologicallyPeriodBeforeNbdOnSameDate() throws Exception {
        // Period starts 2026-04-03; NBD also on 2026-04-03 → PERIOD comes first
        Object[] pRow  = periodRow("pid1", "April 2026", "2026-04-03", "2026-04-30", "O");
        Object[] nbdRow = nbdRow("Easter", "2026-04-03");

        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery(nbdRow);

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-04-01", "2026-04-30", "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray("entries");
            assertEquals(2, entries.length());
            assertEquals("PERIOD",           entries.getJSONObject(0).getString("type"));
            assertEquals("NON_BUSINESS_DAY", entries.getJSONObject(1).getString("type"));
        }
    }

    @Test
    void defaultDateRange_fallsBackToCurrentMonth() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(":today"), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(":dateTo"), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains("nonBusinessDayDate"), eq(Object[].class)))
                .thenReturn(nbdQ);

        // No dateFrom/dateTo params
        WidgetDataContext ctx = ctxWithParams(null, null, "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            // dateFrom and dateTo must be present and non-null
            assertFalse(result.isNull("dateFrom"));
            assertFalse(result.isNull("dateTo"));
            // both must be valid yyyy-MM-dd strings
            assertDoesNotThrow(() -> new SimpleDateFormat("yyyy-MM-dd")
                    .parse(result.getString("dateFrom")));
            assertDoesNotThrow(() -> new SimpleDateFormat("yyyy-MM-dd")
                    .parse(result.getString("dateTo")));
        }
    }
}
