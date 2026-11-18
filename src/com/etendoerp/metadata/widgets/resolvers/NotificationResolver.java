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

import static java.util.stream.Collectors.groupingBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.UsedByLink;
import org.openbravo.model.ad.alert.AlertRule;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;

/**
 * Lists the pending alerts of the current user: the same alerts the bell of the header counts.
 * <p>
 * It follows Classic's {@code AlertActionHandler}: active alert rules whose recipient is the user, or their role
 * when the recipient has no user, limited to the readable clients and organizations; then the alerts of those
 * rules still in status NEW, with the filter clause of each rule applied. Rules are grouped by filter clause, as
 * Classic does, so each clause is converted once. A clause that cannot be converted hides its alerts, like in
 * Classic.
 * <p>
 * Each item carries the alert description ({@code text}), the rule name ({@code title}), its creation time
 * ({@code time}) and {@code priority} "normal" (alerts have no priority).
 */
public class NotificationResolver implements WidgetDataResolver {

    private static final Logger log = LogManager.getLogger(NotificationResolver.class);

    static final String PARAM_ROWS = "rowsNumber";
    static final int DEFAULT_ROWS = 10;
    static final String NORMAL_PRIORITY = "normal";
    private static final String NO_FILTER = "";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_TOTAL = "totalCount";
    private static final String PARAM_CLIENTS = "clients";
    private static final String PARAM_ORGS = "orgs";
    private static final String PARAM_RULES = "rules";

    // @formatter:off
    static final String RULES_HQL =
          "select distinct(e.alertRule) from ADAlertRecipient e"
        + " where e.alertRule.active = true"
        + "   and (e.userContact.id = :userId or (e.userContact.id is null and e.role.id = :roleId))"
        + "   and e.alertRule.client.id in :clients"
        + "   and e.alertRule.organization.id in :orgs";

    /** Unaliased, like Classic's count, so the filter clause of a rule can name the AD_ALERT columns. */
    private static final String ALERTS_WHERE =
          " from AD_ALERT"
        + " where COALESCE(STATUS, 'NEW') = 'NEW'"
        + "   and AD_CLIENT_ID in :clients"
        + "   and AD_ORG_ID in :orgs"
        + "   and AD_ALERTRULE_ID in :rules";

    static final String ITEMS_SQL = "select DESCRIPTION, AD_ALERTRULE_ID, CREATED" + ALERTS_WHERE;
    static final String COUNT_SQL = "select count(*)" + ALERTS_WHERE;
    private static final String ORDER_BY = " order by CREATED desc";
    // @formatter:on

    @Override
    public String getType() {
        return "NOTIFICATION";
    }

    /** Alerts are part of the core, so the widget is always available. */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws JSONException {
        OBContext obContext = ctx.getObContext();
        List<AlertRule> rules = findRules(obContext);
        int limit = parseRows(ctx.param(PARAM_ROWS));

        List<AlertItem> items = new ArrayList<>();
        long total = 0;
        Map<String, List<AlertRule>> rulesByFilter = rules.stream()
            .collect(groupingBy(rule -> Objects.toString(rule.getFilterClause(), NO_FILTER)));
        for (Map.Entry<String, List<AlertRule>> group : rulesByFilter.entrySet()) {
            String filterSql = toFilterSql(group.getKey());
            if (filterSql == null) {
                continue;
            }
            Map<String, String> ruleNames = group.getValue().stream()
                .collect(Collectors.toMap(AlertRule::getId, AlertRule::getName, (first, second) -> first));
            items.addAll(fetchAlerts(obContext, ruleNames, filterSql, limit));
            total += countAlerts(obContext, ruleNames, filterSql);
        }

        items.sort(Comparator.comparing(AlertItem::getCreated, Comparator.nullsLast(Comparator.reverseOrder())));
        return toJson(items.subList(0, Math.min(limit, items.size())), total);
    }

    /**
     * Alert rules the current user receives, directly or through their role.
     *
     * @param obContext context of the current user
     * @return the active rules visible from the current role
     */
    List<AlertRule> findRules(OBContext obContext) {
        return OBDal.getInstance()
            .getSession()
            .createQuery(RULES_HQL, AlertRule.class)
            .setParameter("userId", obContext.getUser().getId())
            .setParameter("roleId", obContext.getRole().getId())
            .setParameterList(PARAM_CLIENTS, readableClients(obContext))
            .setParameterList(PARAM_ORGS, readableOrganizations(obContext))
            .list();
    }

    @SuppressWarnings("unchecked")
    private List<AlertItem> fetchAlerts(OBContext obContext, Map<String, String> ruleNames, String filterSql,
        int limit) {
        List<Object[]> rows = OBDal.getInstance()
            .getSession()
            .createNativeQuery(ITEMS_SQL + filterSql + ORDER_BY)
            .setParameterList(PARAM_CLIENTS, readableClients(obContext))
            .setParameterList(PARAM_ORGS, readableOrganizations(obContext))
            .setParameterList(PARAM_RULES, ruleNames.keySet())
            .setMaxResults(limit)
            .list();
        return rows.stream().map(toItem(ruleNames)).collect(Collectors.toList());
    }

    private long countAlerts(OBContext obContext, Map<String, String> ruleNames, String filterSql) {
        Number count = (Number) OBDal.getInstance()
            .getSession()
            .createNativeQuery(COUNT_SQL + filterSql)
            .setParameterList(PARAM_CLIENTS, readableClients(obContext))
            .setParameterList(PARAM_ORGS, readableOrganizations(obContext))
            .setParameterList(PARAM_RULES, ruleNames.keySet())
            .uniqueResult();
        return count == null ? 0L : count.longValue();
    }

    /**
     * SQL condition of a rule filter clause, with its context variables resolved as Classic does.
     *
     * @param filterClause the filter clause of the rules, empty when they have none
     * @return the condition to append (empty for no filter), or {@code null} when it cannot be converted
     */
    String toFilterSql(String filterClause) {
        if (filterClause.isEmpty()) {
            return NO_FILTER;
        }
        try {
            return new UsedByLink().getWhereClause(currentVars(), NO_FILTER, filterClause);
        } catch (Exception e) {
            log.error("Could not convert alert filter clause into SQL: {}", filterClause, e);
            return null;
        }
    }

    private VariablesSecureApp currentVars() {
        HttpServletRequest request = RequestContext.get().getRequest();
        return request == null ? null : new VariablesSecureApp(request);
    }

    private static Function<Object[], AlertItem> toItem(Map<String, String> ruleNames) {
        return row -> new AlertItem(Objects.toString(row[0], NO_FILTER), ruleNames.get(Objects.toString(row[1])),
            (Date) row[2]);
    }

    private static JSONObject toJson(List<AlertItem> items, long total) throws JSONException {
        JSONArray array = new JSONArray();
        for (AlertItem item : items) {
            array.put(new JSONObject().put("text", item.getText())
                .put("title", item.getRuleName())
                .put("priority", NORMAL_PRIORITY)
                .put("time", item.getCreated() == null ? null : item.getCreated().toInstant().toString()));
        }
        return new JSONObject().put(KEY_ITEMS, array).put(KEY_TOTAL, total);
    }

    private static List<String> readableClients(OBContext obContext) {
        return List.of(obContext.getReadableClients());
    }

    private static List<String> readableOrganizations(OBContext obContext) {
        return List.of(obContext.getReadableOrganizations());
    }

    static int parseRows(String value) {
        try {
            return value == null ? DEFAULT_ROWS : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_ROWS;
        }
    }

    /** One pending alert. */
    static final class AlertItem {
        private final String text;
        private final String ruleName;
        private final Date created;

        AlertItem(String text, String ruleName, Date created) {
            this.text = text;
            this.ruleName = ruleName;
            this.created = created;
        }

        String getText() {
            return text;
        }

        String getRuleName() {
            return ruleName;
        }

        Date getCreated() {
            return created;
        }
    }
}
