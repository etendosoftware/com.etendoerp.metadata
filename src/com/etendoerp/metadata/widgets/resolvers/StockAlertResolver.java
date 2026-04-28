package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Returns products where current stock < M_Product.minimumstock.
 */
public class StockAlertResolver implements WidgetDataResolver {
    @Override public String getType() { return "STOCK_ALERT"; }

    @Override
    public boolean isAvailable() {
        try {
            OBDal.getInstance().getSession()
                .createNativeQuery("SELECT 1 FROM m_storage_detail WHERE 1=0")
                .list();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String SQL =
        "SELECT p.name, p.m_product_id, p.stockmin, SUM(sd.qtyonhand) " +
        "FROM m_storage_detail sd " +
        "JOIN m_product p ON p.m_product_id = sd.m_product_id " +
        "WHERE sd.isactive = 'Y' AND p.stockmin > 0 " +
        "GROUP BY p.name, p.m_product_id, p.stockmin " +
        "HAVING SUM(sd.qtyonhand) < p.stockmin " +
        "ORDER BY (p.stockmin - SUM(sd.qtyonhand)) DESC";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        int limit = parseIntParam(ctx.param("rowsNumber"), 5);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = OBDal.getInstance().getSession()
                .createNativeQuery(SQL)
                .setMaxResults(limit)
                .list();

        JSONArray items = new JSONArray();
        for (Object[] row : rows) {
            BigDecimal current  = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal minStock = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            items.put(new JSONObject()
                    .put("productName",    row[0])
                    .put("productId",      row[1])
                    .put("currentStock",   current.intValue())
                    .put("estimatedStock", minStock.intValue())
                    .put("unit",           "Unidades"));
        }
        return new JSONObject().put("items", items);
    }

    private int parseIntParam(String val, int def) {
        try { return val != null ? Integer.parseInt(val) : def; } catch (Exception e) { return def; }
    }
}
