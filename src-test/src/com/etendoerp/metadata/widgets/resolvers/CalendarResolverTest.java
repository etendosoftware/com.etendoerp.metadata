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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarResolverTest {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATE_FROM = "dateFrom";
    private static final String DATE_TO = "dateTo";
    private static final String QUERY_TODAY = ":today";
    private static final String QUERY_DATE_TO = ":dateTo";
    private static final String QUERY_NBD = "nonBusinessDayDate";
    private static final String MARCH_2026 = "March 2026";
    private static final String DATE_2026_03_01 = "2026-03-01";
    private static final String DATE_2026_03_31 = "2026-03-31";
    private static final String CURRENT_PERIOD = "currentPeriod";
    private static final String FALSE_STR = "false";
    private static final String ENTRIES = "entries";
    private static final String DATE_2026_04_03 = "2026-04-03";
    private static final String EASTER = "Easter";
    private static final String DATE_2026_04_30 = "2026-04-30";

    @Mock OBDal    obDal;
    @Mock Session  session;
    @Mock OBContext obContext;
    @Mock Client   mockClient;

    // helper: build a period row: [id, name, startDate, endDate, openClose]
    private Object[] periodRow(String id, String name, String start, String end, String oc)
            throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return new Object[]{ id, name, sdf.parse(start), sdf.parse(end), oc };
    }

    // helper: build a non-business-day row: [name, date]
    private Object[] nbdRow(String name, String date) throws Exception {
        return new Object[]{ name, new SimpleDateFormat(DATE_FORMAT).parse(date) };
    }

    // helper: mock a Query<Object[]> that returns given rows
    @SuppressWarnings("unchecked")
    private Query<Object[]> mockQuery(Object[]... rows) {
        Query<Object[]> q = mock(Query.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.list()).thenReturn(Arrays.asList(rows));
        return q;
    }

    // helper: mock a single-result Query<Object[]>
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

    // shared context mock
    private WidgetDataContext ctxWithParams(String dateFrom, String dateTo,
                                            String includePeriods, String includeNbd) {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.getObContext()).thenReturn(obContext);
        when(obContext.getCurrentClient()).thenReturn(mockClient);
        when(mockClient.getId()).thenReturn("clientId1");
        lenient().when(ctx.param(DATE_FROM)).thenReturn(dateFrom);
        lenient().when(ctx.param(DATE_TO)).thenReturn(dateTo);
        lenient().when(ctx.param("includePeriods")).thenReturn(includePeriods);
        lenient().when(ctx.param("includeNonBusinessDays")).thenReturn(includeNbd);
        return ctx;
    }

    @Test
    void getTypeReturnsCalendar() {
        assertEquals("CALENDAR", new CalendarResolver().getType());
    }

    @Test
    void currentPeriodPopulatedWhenPeriodCoversToday() throws Exception {
        Object[] pRow = periodRow("pid1", MARCH_2026, DATE_2026_03_01, DATE_2026_03_31, "O");
        Query<Object[]> currentQ = mockQuerySingle(pRow);
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams(DATE_2026_03_01, DATE_2026_03_31, "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertFalse(result.isNull(CURRENT_PERIOD));
            assertEquals(MARCH_2026, result.getJSONObject(CURRENT_PERIOD).getString("name"));
            assertEquals("O", result.getJSONObject(CURRENT_PERIOD).getString("openClose"));
        }
    }

    @Test
    void currentPeriodNullWhenNoPeriodCoversToday() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams(DATE_2026_03_01, DATE_2026_03_31, "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertTrue(result.isNull(CURRENT_PERIOD));
        }
    }

    @Test
    void entriesContainsPeriodEntriesWithId() throws Exception {
        Object[] pRow = periodRow("pid1", MARCH_2026, DATE_2026_03_01, DATE_2026_03_31, "O");
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams(DATE_2026_03_01, DATE_2026_03_31, "true", FALSE_STR);

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray(ENTRIES);
            assertEquals(1, entries.length());
            JSONObject e = entries.getJSONObject(0);
            assertEquals("PERIOD", e.getString("type"));
            assertEquals("pid1",   e.getString("id"));
            assertEquals(MARCH_2026, e.getString("name"));
        }
    }

    @Test
    void entriesContainsNonBusinessDayEntries() throws Exception {
        Object[] nbdRow = nbdRow(EASTER, DATE_2026_04_03);
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery(nbdRow);

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-04-01", DATE_2026_04_30, FALSE_STR, "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray(ENTRIES);
            assertEquals(1, entries.length());
            JSONObject e = entries.getJSONObject(0);
            assertEquals("NON_BUSINESS_DAY", e.getString("type"));
            assertEquals(EASTER, e.getString("name"));
        }
    }

    @Test
    void entriesEmptyWhenBothFlagsAreFalse() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);

        WidgetDataContext ctx = ctxWithParams(DATE_2026_03_01, DATE_2026_03_31, FALSE_STR, FALSE_STR);

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertEquals(0, result.getJSONArray(ENTRIES).length());
        }
    }

    @Test
    void entriesSortedChronologicallyPeriodBeforeNbdOnSameDate() throws Exception {
        Object[] pRow  = periodRow("pid1", "April 2026", DATE_2026_04_03, DATE_2026_04_30, "O");
        Object[] nbdRow = nbdRow(EASTER, DATE_2026_04_03);

        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery(pRow);
        Query<Object[]> nbdQ    = mockQuery(nbdRow);

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams("2026-04-01", DATE_2026_04_30, "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONArray entries = new CalendarResolver().resolve(ctx).getJSONArray(ENTRIES);
            assertEquals(2, entries.length());
            assertEquals("PERIOD",           entries.getJSONObject(0).getString("type"));
            assertEquals("NON_BUSINESS_DAY", entries.getJSONObject(1).getString("type"));
        }
    }

    @Test
    void defaultDateRangeFallsBackToCurrentMonth() throws Exception {
        Query<Object[]> currentQ = mockQueryEmpty();
        Query<Object[]> periodsQ = mockQuery();
        Query<Object[]> nbdQ    = mockQuery();

        when(session.createQuery(contains(QUERY_TODAY), eq(Object[].class)))
                .thenReturn(currentQ);
        when(session.createQuery(contains(QUERY_DATE_TO), eq(Object[].class)))
                .thenReturn(periodsQ);
        when(session.createQuery(contains(QUERY_NBD), eq(Object[].class)))
                .thenReturn(nbdQ);

        WidgetDataContext ctx = ctxWithParams(null, null, "true", "true");

        try (MockedStatic<OBDal> dal = mockStatic(OBDal.class)) {
            dal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);

            JSONObject result = new CalendarResolver().resolve(ctx);
            assertFalse(result.isNull(DATE_FROM));
            assertFalse(result.isNull(DATE_TO));
            assertDoesNotThrow(() -> new SimpleDateFormat(DATE_FORMAT)
                    .parse(result.getString(DATE_FROM)));
            assertDoesNotThrow(() -> new SimpleDateFormat(DATE_FORMAT)
                    .parse(result.getString(DATE_TO)));
        }
    }
}
