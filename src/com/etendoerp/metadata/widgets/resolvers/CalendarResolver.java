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
    private static final String PARAM_DATE_FROM = "dateFrom";
    private static final String PARAM_DATE_TO = "dateTo";
    private static final String PARAM_CLIENT = "client";

    private static final String CURRENT_PERIOD_HQL =
        "select p.id, p.name, p.startingDate, p.endingDate, p.openClose " +
        "from FinancialMgmtPeriod p " +
        "where p.client.id        = :client " +
        "  and p.startingDate    <= :today " +
        "  and p.endingDate      >= :today " +
        "  and p.periodType       = 'S' " +
        "  and p.active           = true " +
        "order by p.startingDate desc";

    private static final String PERIODS_HQL =
        "select p.id, p.name, p.startingDate, p.endingDate, p.openClose " +
        "from FinancialMgmtPeriod p " +
        "where p.client.id        = :client " +
        "  and p.endingDate      >= :dateFrom " +
        "  and p.startingDate    <= :dateTo " +
        "  and p.periodType       = 'S' " +
        "  and p.active           = true " +
        "order by p.startingDate";

    private static final String NBD_HQL =
        "select n.name, n.nonBusinessDayDate " +
        "from FinancialMgmtNonBusinessDay n " +
        "where n.client.id              = :client " +
        "  and n.nonBusinessDayDate    >= :dateFrom " +
        "  and n.nonBusinessDayDate    <= :dateTo " +
        "  and n.active                 = true " +
        "order by n.nonBusinessDayDate";

    @Override
    public String getType() { return "CALENDAR"; }

    @Override
    public boolean isAvailable() {
        try {
            org.openbravo.dal.service.OBDal.getInstance().getSession()
                .createQuery("select 1 from FinancialMgmtPeriod p where 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        String client  = ctx.getObContext().getCurrentClient().getId();
        Date   today   = new Date();

        // ── resolve date range (defaults: current month in server timezone)
        String fromStr = ctx.param(PARAM_DATE_FROM);
        String toStr   = ctx.param(PARAM_DATE_TO);
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
                .put(PARAM_DATE_FROM,      sdf.format(dateFrom))
                .put(PARAM_DATE_TO,        sdf.format(dateTo));
    }

    // ── internal helpers ────────────────────────────────────────────────────

    private JSONObject queryCurrentPeriod(String client, Date today,
                                          SimpleDateFormat sdf) throws Exception {
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(CURRENT_PERIOD_HQL, Object[].class);
        q.setParameter(PARAM_CLIENT, client);
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
            q.setParameter(PARAM_CLIENT,   client);
            q.setParameter(PARAM_DATE_FROM, dateFrom);
            q.setParameter(PARAM_DATE_TO,   dateTo);
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
            q.setParameter(PARAM_CLIENT,   client);
            q.setParameter(PARAM_DATE_FROM, dateFrom);
            q.setParameter(PARAM_DATE_TO,   dateTo);
            for (Object[] row : q.list()) {
                nbdEntries.add(new JSONObject()
                        .put("type", "NON_BUSINESS_DAY")
                        .put("name", row[0])
                        .put("date", sdf.format((Date) row[1])));
            }
        }

        return mergeSortedEntries(periodEntries, nbdEntries);
    }

    /** Merges two pre-sorted lists, placing PERIOD entries before NBD entries on the same date. */
    private JSONArray mergeSortedEntries(List<JSONObject> periodEntries,
                                         List<JSONObject> nbdEntries) throws Exception {
        JSONArray result = new JSONArray();
        int pi = 0;
        int ni = 0;
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
