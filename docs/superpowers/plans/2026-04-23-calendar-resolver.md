# CalendarResolver Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `CalendarResolver` — a widget data resolver that returns ERP fiscal calendar data (accounting periods + non-business days) in a unified `entries[]` format.

**Architecture:** Single `CalendarResolver.java` following the same pattern as all other resolvers in `resolvers/`. Two HQL queries (periods in range + non-business days in range) plus one for the current period. Results merged into a chronologically sorted `entries[]` array. No new tables, no new services.

**Tech Stack:** Java 11, Hibernate HQL, jettison `JSONObject`/`JSONArray`, JUnit 5 + Mockito 5 (with `MockedStatic` for `OBDal`).

**Spec:** `docs/superpowers/specs/2026-04-23-calendar-resolver-design.md`

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| **Create** | `src/com/etendoerp/metadata/widgets/resolvers/CalendarResolver.java` | Resolver logic: 3 HQL queries, merge + sort entries, build JSON response |
| **Create** | `src-test/src/com/etendoerp/metadata/widgets/resolvers/CalendarResolverTest.java` | Unit tests (Mockito, no real DB) |

No other files need to change. CDI auto-discovers the new class via `bean-discovery-mode="all"` in `beans.xml`. The SQL INSERT for `etmeta_widget_class` is done as a separate DB step at the end.

---

## Task 1: Write the failing tests

**Files:**
- Create: `src-test/src/com/etendoerp/metadata/widgets/resolvers/CalendarResolverTest.java`

- [ ] **Step 1.1: Create the test file with all 8 test cases**

```java
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
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.list()).thenReturn(Arrays.asList(rows));
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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

        when(session.createQuery(contains("startingDate <= :today"), eq(Object[].class)))
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
```

- [ ] **Step 1.2: Run tests to confirm they all fail (CalendarResolver doesn't exist yet)**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.widgets.resolvers.CalendarResolverTest" 2>&1 | tail -20
```

Expected: `FAILED` — compilation error "cannot find symbol: CalendarResolver".

---

## Task 2: Implement CalendarResolver

**Files:**
- Create: `src/com/etendoerp/metadata/widgets/resolvers/CalendarResolver.java`

- [ ] **Step 2.1: Create the resolver**

```java
package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Returns ERP-native fiscal calendar data for a date range:
 * accounting periods (C_Period) and non-business days (C_NonBusinessDay).
 *
 * All dates are computed in server timezone.
 *
 * Output: { currentPeriod, entries[], dateFrom, dateTo }
 * Each entry has: type (PERIOD | NON_BUSINESS_DAY), name, and type-specific fields.
 * On equal dates, PERIOD entries precede NON_BUSINESS_DAY entries.
 *
 * NOTE — future "events" mode:
 * The classic OBCalendarWidget also supported scheduling events via a configurable
 * Action Handler parameter (calendarDataActionHandler). A future CALENDAR_EVENTS
 * resolver type could proxy to such an action handler and return normalized event
 * entries { type:"EVENT", title, start, end }. Deferred: no standard events table
 * exists in the base install and the new web UI does not require it yet.
 * See: docs/superpowers/specs/2026-04-23-calendar-resolver-design.md — Future Work.
 */
public class CalendarResolver implements WidgetDataResolver {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static final String CURRENT_PERIOD_HQL =
        "select p.id, p.name, p.startingDate, p.endingDate, p.openClose " +
        "from C_Period p " +
        "where p.client.id        = :client " +
        "  and p.startingDate    <= :today " +
        "  and p.endingDate      >= :today " +
        "  and p.periodType       = 'S' " +
        "  and p.active           = true " +
        "order by p.startingDate desc";

    private static final String PERIODS_HQL =
        "select p.id, p.name, p.startingDate, p.endingDate, p.openClose " +
        "from C_Period p " +
        "where p.client.id        = :client " +
        "  and p.endingDate      >= :dateFrom " +
        "  and p.startingDate    <= :dateTo " +
        "  and p.periodType       = 'S' " +
        "  and p.active           = true " +
        "order by p.startingDate";

    private static final String NBD_HQL =
        "select n.name, n.nonBusinessDayDate " +
        "from C_NonBusinessDay n " +
        "where n.client.id              = :client " +
        "  and n.nonBusinessDayDate    >= :dateFrom " +
        "  and n.nonBusinessDayDate    <= :dateTo " +
        "  and n.active                 = true " +
        "order by n.nonBusinessDayDate";

    @Override
    public String getType() { return "CALENDAR"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        String client  = ctx.getObContext().getCurrentClient().getId();
        Date   today   = new Date();

        // ── resolve date range (defaults: current month in server timezone)
        String fromStr = ctx.param("dateFrom");
        String toStr   = ctx.param("dateTo");
        Date dateFrom  = (fromStr != null) ? sdf.parse(fromStr) : firstDayOfMonth(today);
        Date dateTo    = (toStr   != null) ? sdf.parse(toStr)   : lastDayOfMonth(today);

        // ── query flags (default true)
        boolean includePeriods = !"false".equalsIgnoreCase(ctx.param("includePeriods"));
        boolean includeNbd     = !"false".equalsIgnoreCase(ctx.param("includeNonBusinessDays"));

        // ── current period
        JSONObject currentPeriod = queryCurrentPeriod(client, today, sdf);

        // ── entries
        JSONArray entries = buildEntries(client, dateFrom, dateTo, includePeriods, includeNbd, sdf);

        return new JSONObject()
                .put("currentPeriod", currentPeriod != null ? currentPeriod : JSONObject.NULL)
                .put("entries",       entries)
                .put("dateFrom",      sdf.format(dateFrom))
                .put("dateTo",        sdf.format(dateTo));
    }

    // ── internal helpers ────────────────────────────────────────────────────

    private JSONObject queryCurrentPeriod(String client, Date today,
                                          SimpleDateFormat sdf) throws Exception {
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(CURRENT_PERIOD_HQL, Object[].class);
        q.setParameter("client", client);
        q.setParameter("today",  today);
        q.setMaxResults(1);
        List<Object[]> rows = q.list();
        if (rows.isEmpty()) return null;
        return periodToJson(rows.get(0), sdf);
    }

    private JSONArray buildEntries(String client, Date dateFrom, Date dateTo,
                                   boolean includePeriods, boolean includeNbd,
                                   SimpleDateFormat sdf) throws Exception {
        // ── collect period entries
        List<JSONObject> periodEntries = new ArrayList<>();
        if (includePeriods) {
            Query<Object[]> q = OBDal.getInstance().getSession()
                    .createQuery(PERIODS_HQL, Object[].class);
            q.setParameter("client",   client);
            q.setParameter("dateFrom", dateFrom);
            q.setParameter("dateTo",   dateTo);
            for (Object[] row : q.list()) {
                JSONObject e = periodToJson(row, sdf);
                e.put("type", "PERIOD");
                periodEntries.add(e);
            }
        }

        // ── collect non-business-day entries
        List<JSONObject> nbdEntries = new ArrayList<>();
        if (includeNbd) {
            Query<Object[]> q = OBDal.getInstance().getSession()
                    .createQuery(NBD_HQL, Object[].class);
            q.setParameter("client",   client);
            q.setParameter("dateFrom", dateFrom);
            q.setParameter("dateTo",   dateTo);
            for (Object[] row : q.list()) {
                nbdEntries.add(new JSONObject()
                        .put("type", "NON_BUSINESS_DAY")
                        .put("name", row[0])
                        .put("date", sdf.format((Date) row[1])));
            }
        }

        // ── merge: iterate both sorted lists, PERIOD before NBD on same date
        JSONArray result = new JSONArray();
        int pi = 0, ni = 0;
        while (pi < periodEntries.size() || ni < nbdEntries.size()) {
            if (pi >= periodEntries.size()) {
                result.put(nbdEntries.get(ni++));
            } else if (ni >= nbdEntries.size()) {
                result.put(periodEntries.get(pi++));
            } else {
                String pDate = periodEntries.get(pi).getString("start");
                String nDate = nbdEntries.get(ni).getString("date");
                if (pDate.compareTo(nDate) <= 0) {
                    result.put(periodEntries.get(pi++));
                } else {
                    result.put(nbdEntries.get(ni++));
                }
            }
        }
        return result;
    }

    /** Maps a period query row [id, name, startDate, endDate, openClose] to a JSONObject. */
    private JSONObject periodToJson(Object[] row, SimpleDateFormat sdf) throws Exception {
        return new JSONObject()
                .put("id",        row[0])
                .put("name",      row[1])
                .put("start",     sdf.format((Date) row[2]))
                .put("end",       sdf.format((Date) row[3]))
                .put("openClose", row[4]);
    }

    private Date firstDayOfMonth(Date ref) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ref);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private Date lastDayOfMonth(Date ref) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(ref);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }
}
```

- [ ] **Step 2.2: Run the tests**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.widgets.resolvers.CalendarResolverTest" 2>&1 | tail -30
```

Expected: all 8 tests PASS. If any fail, read the error message carefully — the most likely issues are:
- Mock `when()` selector not matching the HQL strings → adjust the `contains(...)` fragment
- `JSONObject.NULL` not matching `isNull()` → ensure `put("currentPeriod", JSONObject.NULL)` is used

- [ ] **Step 2.3: Commit**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
echo -n "Feature ETP-3745: add CalendarResolver for fiscal calendar data" | wc -c
# must be ≤ 80
git add src/com/etendoerp/metadata/widgets/resolvers/CalendarResolver.java \
        src-test/src/com/etendoerp/metadata/widgets/resolvers/CalendarResolverTest.java
git commit -m "Feature ETP-3745: add CalendarResolver for fiscal calendar data"
```

---

## Task 3: Register in etmeta_widget_class

- [ ] **Step 3.1: Insert the widget class record**

```sql
INSERT INTO etmeta_widget_class (
    etmeta_widget_class_id,
    ad_client_id,
    ad_org_id,
    isactive,
    created, createdby,
    updated, updatedby,
    name,
    type,
    title,
    description,
    defaultwidth,
    defaultheight,
    refreshinterval,
    ad_module_id
) VALUES (
    upper(replace(gen_random_uuid()::text, '-', '')),
    '0',  -- system client
    '0',  -- system org
    'Y',
    now(), '0',
    now(), '0',
    'fiscal-calendar',
    'CALENDAR',
    'Fiscal Calendar',
    'Shows accounting periods and non-business days from the ERP fiscal calendar',
    4,
    4,
    0,
    '51E67C9184F6439595409B46040FC572'  -- com.etendoerp.metadata module ID
);
```

Run via psql:

```bash
PGPASSWORD=tad psql -h localhost -p 5432 -U tad -d etendo_26 -c "<paste SQL above>"
```

- [ ] **Step 3.2: Verify the record was inserted**

```bash
PGPASSWORD=tad psql -h localhost -p 5432 -U tad -d etendo_26 \
  -c "SELECT etmeta_widget_class_id, name, type FROM etmeta_widget_class WHERE type='CALENDAR';"
```

Expected: one row with `name=fiscal-calendar`, `type=CALENDAR`.

- [ ] **Step 3.3: Note on deferred export**

The `etmeta_widget_class` table is managed by the AD module. The INSERT above is a runtime
state change; it will be captured as XML sourcedata by `./gradlew export.database` — but that
requires a running Tomcat with the module active.

No commit is needed here. The `export.database` + sourcedata XML commit is deferred to the
next session (same policy as the other `etmeta_widget_class` records already in the DB).

---

## Task 4: Run the full test suite

- [ ] **Step 4.1: Run all resolver tests to confirm no regressions**

```bash
cd /Users/santiagoalaniz/Dev/Work/etendo_26/modules/com.etendoerp.metadata
./gradlew test --tests "com.etendoerp.metadata.widgets.resolvers.*" 2>&1 | tail -20
```

Expected: all tests PASS.

- [ ] **Step 4.2: Run the complete module test suite**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

---

## Done

After all tasks complete:
- `CalendarResolver.java` exists and all 8 tests pass
- `etmeta_widget_class` has a `CALENDAR` record for `fiscal-calendar`
- `export.database` and sourcedata XML commit are deferred to next session (Tomcat restart required)
