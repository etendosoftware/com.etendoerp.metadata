# CalendarResolver Design

**Date:** 2026-04-23
**Feature:** ETP-3745 — Widget System
**Status:** Approved

---

## Context

The classic Etendo (`org.openbravo.client.myob`) `CalendarWidgetProvider` rendered an
`OBMultiCalendar` SmartClient component whose data came from a configurable Action Handler
parameter. It was a scheduling/internal-events calendar — not connected to Google Calendar.

The Google Calendar widget (`OBGCalWidget`) was a separate, iframe-only embed of a public
Google Calendar URL. It has **no OAuth or API integration** and is already covered by the
`URLResolver` (a `google-calendar` entry exists in `etmeta_widget_class` of type `URL`).

This resolver covers a third space: **ERP-native fiscal calendar data** — accounting periods
(`C_Period`) and non-business days (`C_NonBusinessDay`) registered in Etendo's fiscal calendar.

---

## Decision

Use **Option C — Unified entries model**: the resolver always returns a flat `entries[]` array
where each entry has a `type` field (`PERIOD` | `NON_BUSINESS_DAY`), regardless of the data
source. A convenience `currentPeriod` object is included at the top level.

This keeps the frontend contract simple: it iterates `entries` without knowing internal modes.

---

## Input Parameters

Configured via `ETMETA_WIDGET_PARAM` rows (or overridden per-instance via `PARAMETERS_JSON`):

| Parameter              | Type    | Default               | Description                         |
|------------------------|---------|-----------------------|-------------------------------------|
| `dateFrom`             | String  | First day of current month | Range start (yyyy-MM-dd)       |
| `dateTo`               | String  | Last day of current month  | Range end (yyyy-MM-dd)         |
| `includeNonBusinessDays` | Boolean | `true`              | Include `C_NonBusinessDay` entries  |
| `includePeriods`       | Boolean | `true`                | Include `C_Period` entries          |

---

## Output Contract

```json
{
  "currentPeriod": {
    "id": "<c_period_id>",
    "name": "March 2026",
    "start": "2026-03-01",
    "end": "2026-03-31",
    "openClose": "O"
  },
  "entries": [
    {
      "type": "PERIOD",
      "id": "<c_period_id>",
      "name": "March 2026",
      "start": "2026-03-01",
      "end": "2026-03-31",
      "openClose": "O"
    },
    {
      "type": "NON_BUSINESS_DAY",
      "name": "Easter",
      "date": "2026-04-03"
    }
  ],
  "dateFrom": "2026-03-01",
  "dateTo": "2026-03-31"
}
```

- `currentPeriod` is `null` if no period covers `today`.
- `openClose` on periods reflects the `C_Period.OpenClose` column (processing flag): `"O"` = Open,
  `"C"` = Closed. This is **not** the aggregate accounting status computed from `C_PeriodControl`
  (which additionally has `"P"` = Permanently Closed, `"N"` = Never Opened, `"M"` = Mixed).
  The computed status cannot be queried via HQL — if a future consumer needs it, a native SQL
  subquery against `C_PeriodControl` is required.
- `entries` is sorted chronologically. When a `NON_BUSINESS_DAY` falls on the same date as a
  `PERIOD` start, `PERIOD` entries come first.
- Date defaults (`dateFrom`/`dateTo`) are computed in **server timezone**.

---

## Data Sources

### Current period

```hql
select p.id, p.name, p.startingDate, p.endingDate, p.openClose
from C_Period p
where p.client.id        = :client
  and p.startingDate    <= :today
  and p.endingDate      >= :today
  and p.periodType       = 'S'
  and p.active           = true
order by p.startingDate desc
```

Returns at most one row (first result).

### Periods in range

```hql
select p.id, p.name, p.startingDate, p.endingDate, p.openClose
from C_Period p
where p.client.id        = :client
  and p.endingDate      >= :dateFrom
  and p.startingDate    <= :dateTo
  and p.periodType       = 'S'
  and p.active           = true
order by p.startingDate
```

### Non-business days in range

```hql
select n.name, n.nonBusinessDayDate
from C_NonBusinessDay n
where n.client.id              = :client
  and n.nonBusinessDayDate    >= :dateFrom
  and n.nonBusinessDayDate    <= :dateTo
  and n.active                 = true
order by n.nonBusinessDayDate
```

(`nonBusinessDayDate` maps to column `Date1` in `C_NonBusinessDay`.)

All queries use `OBContext.getCurrentClient().getId()` for `:client`.

---

## File Structure

```
src/com/etendoerp/metadata/widgets/resolvers/
    CalendarResolver.java

src-test/src/com/etendoerp/metadata/widgets/resolvers/
    CalendarResolverTest.java
```

No new tables, no new services. Follows the same pattern as `KPIResolver` and `QueryListResolver`.

---

## etmeta_widget_class Registration

One SQL `INSERT` into `etmeta_widget_class`:

| Field           | Value              |
|-----------------|--------------------|
| `name`          | `fiscal-calendar`  |
| `type`          | `CALENDAR`         |
| `title`         | `Fiscal Calendar`  |
| `defaultWidth`  | 4                  |
| `defaultHeight` | 4                  |

No fixed params. All four input parameters are optional with defaults.

---

## WidgetResolverRegistry

No manual registration is needed. The module's `beans.xml` uses `bean-discovery-mode="all"`,
so Weld automatically discovers `CalendarResolver` as a CDI bean and injects it into
`WidgetResolverRegistry.setResolvers(Instance<WidgetDataResolver>)` at startup. No annotation
(`@ApplicationScoped`, etc.) is required — the same pattern as all existing resolvers.

`WidgetResolverRegistry.register()` exists only for unit tests.

---

## Future Work

### Events mode (not implemented)

The classic `OBCalendarWidget` supported a scheduling calendar driven by a configurable
Action Handler (`calendarDataActionHandler` parameter). A future `CALENDAR_EVENTS` type (or
`mode=events` param on this resolver) could proxy to that action handler and return normalized
event entries `{ type: "EVENT", title, start, end, description, color }`.

This was deliberately deferred because:
1. No standard Etendo events table exists in the base install.
2. The new web UI does not currently need scheduling/appointment events.
3. Implementing it correctly requires agreeing on the Action Handler contract first.

When implemented, it should reuse the same `entries[]` output shape so the frontend requires
no changes to consume event entries alongside fiscal entries.

---

## Testing

`CalendarResolverTest` covers:

1. `getType()` returns `"CALENDAR"`.
2. `currentPeriod` is populated when a period covers today.
3. `currentPeriod` is `null` when no period covers today.
4. `entries` contains `PERIOD` entries (with `id`) when `includePeriods=true`.
5. `entries` contains `NON_BUSINESS_DAY` entries when `includeNonBusinessDays=true`.
6. `entries` is empty when both flags are `false`.
7. Default date range falls back to current month (server timezone) when params are absent.
8. Entries are sorted chronologically; on same date, `PERIOD` entries precede `NON_BUSINESS_DAY`.
