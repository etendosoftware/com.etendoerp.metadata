package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a multi-row HQL_QUERY. Column names are declared via the "columns" param
 * as a comma-separated list (e.g. "order,org,total,deliveryDate").
 */
public class QueryListResolver implements WidgetDataResolver {
    @Override public String getType() { return "QUERY_LIST"; }

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        String hql = ctx.classString("4"); // HQL_QUERY
        if (hql == null) return new JSONObject().put("rows", new JSONArray()).put("totalRows", 0);

        String columnsCsv = ctx.param("columns");
        String[] colNames = columnsCsv != null
                ? columnsCsv.split(",")
                : extractAliases(hql);

        Map<String, Object> resolvedParams = buildResolvedParams(ctx);

        Query<Object[]> q = OBDal.getInstance().getSession().createQuery(hql, Object[].class);
        bindParams(q, resolvedParams);

        String pageSizeStr = ctx.param("_pageSize");
        long totalRows;
        if (pageSizeStr != null) {
            int pageSize = Integer.parseInt(pageSizeStr);
            int page     = ctx.param("_page") != null ? Integer.parseInt(ctx.param("_page")) : 1;
            totalRows    = countQuery(hql, resolvedParams);
            q.setFirstResult((page - 1) * pageSize);
            q.setMaxResults(pageSize);
        } else {
            String rowsParam = ctx.param("rowsNumber");
            if (rowsParam != null) q.setMaxResults(Integer.parseInt(rowsParam));
            totalRows = -1; // filled after list()
        }

        List<Object[]> rawRows = q.list();
        if (totalRows < 0) totalRows = rawRows.size();

        JSONArray rows = new JSONArray();
        for (Object[] raw : rawRows) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < raw.length; i++) {
                String col = i < colNames.length ? colNames[i].trim() : "col" + i;
                row.put(col, raw[i]);
            }
            rows.put(row);
        }

        JSONArray colDefs = new JSONArray();
        for (String col : colNames) {
            String name = col.trim();
            if (name.endsWith("Id")) continue; // ID fields stay in rows for navigation, not displayed
            colDefs.put(new JSONObject().put("name", name).put("label", toLabel(name)));
        }

        return new JSONObject()
                .put("columns",   colDefs)
                .put("rows",      rows)
                .put("totalRows", totalRows);
    }

    private Map<String, Object> buildResolvedParams(WidgetDataContext ctx) {
        Map<String, Object> resolved = new HashMap<>(ctx.getParams());
        OBContext obc = ctx.getObContext();
        if (obc != null) {
            resolved.putIfAbsent("client", obc.getCurrentClient().getId());
            resolved.putIfAbsent("user",   obc.getUser().getId());
            String[] readableOrgs = obc.getReadableOrganizations();
            if (readableOrgs != null) {
                resolved.putIfAbsent("organizationList", Arrays.asList(readableOrgs));
            }
        }
        return resolved;
    }

    private void bindParams(Query<?> q, Map<String, Object> resolvedParams) {
        for (var param : q.getParameters()) {
            String name = param.getName();
            if (name == null) continue;
            Object val = resolvedParams.get(name);
            if (val instanceof Collection) {
                q.setParameterList(name, (Collection<?>) val);
            } else {
                q.setParameter(name, val != null ? val : "%");
            }
        }
    }

    private static final Pattern PAREN_OR_FROM  = Pattern.compile("[()]|\\bfrom\\b",     Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY       = Pattern.compile("\\border\\s+by\\b",   Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_BY       = Pattern.compile("\\bgroup\\s+by\\b",   Pattern.CASE_INSENSITIVE);
    private static final Pattern HAVING         = Pattern.compile("\\bhaving\\b",         Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIAS_AT_END   = Pattern.compile("\\bas\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);

    /** Finds the char index of the first top-level FROM (not inside parentheses). */
    private int mainFromIndex(String hql) {
        int depth = 0;
        Matcher m = PAREN_OR_FROM.matcher(hql);
        while (m.find()) {
            String t = m.group();
            if ("(".equals(t))      depth++;
            else if (")".equals(t)) depth--;
            else if (depth == 0)    return m.start();
        }
        return -1;
    }

    private long countQuery(String hql, Map<String, Object> resolvedParams) {
        int fromIdx = mainFromIndex(hql);
        if (fromIdx < 0) return 0L;

        String fromPart = hql.substring(fromIdx);
        // Strip ORDER BY, GROUP BY and HAVING — count(*) needs a single scalar result
        int cutAt = Integer.MAX_VALUE;
        Matcher m;
        m = GROUP_BY.matcher(fromPart); if (m.find()) cutAt = Math.min(cutAt, m.start());
        m = HAVING.matcher(fromPart);   if (m.find()) cutAt = Math.min(cutAt, m.start());
        m = ORDER_BY.matcher(fromPart); if (m.find()) cutAt = Math.min(cutAt, m.start());
        if (cutAt < Integer.MAX_VALUE) fromPart = fromPart.substring(0, cutAt);

        Query<Long> q = OBDal.getInstance().getSession()
                .createQuery("select count(*) " + fromPart, Long.class);
        bindParams(q, resolvedParams);
        Long result = q.uniqueResult();
        return result != null ? result : 0L;
    }

    /**
     * Extracts AS aliases from the outermost SELECT clause.
     * Handles subqueries correctly by splitting on top-level commas only.
     */
    private String[] extractAliases(String hql) {
        int fromIdx = mainFromIndex(hql);
        String selectClause = fromIdx > 0 ? hql.substring(0, fromIdx) : hql;
        selectClause = selectClause.replaceFirst("(?i)^\\s*select\\s+", "");

        List<String> items   = splitTopLevel(selectClause);
        List<String> aliases = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Matcher m = ALIAS_AT_END.matcher(items.get(i).trim());
            aliases.add(m.find() ? m.group(1) : "col" + i);
        }
        return aliases.toArray(new String[0]);
    }

    /**
     * Converts a camelCase alias to a human-friendly label.
     * e.g. "documentNo" → "Document No", "grandTotalAmount" → "Grand Total Amount"
     */
    private String toLabel(String alias) {
        if (alias == null || alias.isEmpty()) return alias;
        // Insert space before each uppercase letter, then capitalize first letter of each word
        String spaced = alias.replaceAll("([A-Z])", " $1").trim();
        String[] words = spaced.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }

    /** Splits a string by commas that are not inside parentheses. */
    private List<String> splitTopLevel(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(s.substring(start, i));
                start = i + 1;
            }
        }
        result.add(s.substring(start));
        return result;
    }
}
