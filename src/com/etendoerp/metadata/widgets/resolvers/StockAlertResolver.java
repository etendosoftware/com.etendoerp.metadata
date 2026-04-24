package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
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
            org.openbravo.dal.service.OBDal.getInstance().getSession()
                .getSessionFactory().getMetamodel().entity("M_StorageDetail");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String HQL =
        "select p.name, p.id, p.minimumstock, sum(sd.quantityOnHand) " +
        "from M_StorageDetail sd join sd.product p " +
        "where sd.isactive = 'Y' " +
        "group by p.name, p.id, p.minimumstock " +
        "having sum(sd.quantityOnHand) < p.minimumstock " +
        "order by (p.minimumstock - sum(sd.quantityOnHand)) desc";

    @Override
    public JSONObject resolve(WidgetDataContext ctx) throws Exception {
        try {
            int limit = parseIntParam(ctx.param("rowsNumber"), 5);

            Query<Object[]> q = OBDal.getInstance().getSession()
                    .createQuery(HQL, Object[].class);
            q.setMaxResults(limit);
            List<Object[]> rows = q.list();

            JSONArray items = new JSONArray();
            for (Object[] row : rows) {
                BigDecimal current  = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
                BigDecimal minStock = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
                items.put(new JSONObject()
                        .put("productName",    row[0])
                        .put("productId",      row[1])
                        .put("currentStock",   current.intValue())
                        .put("estimatedStock", minStock.intValue())
                        .put("unit",           "Unidades"));
            }
            return new JSONObject().put("items", items);
        } catch (Exception e) {
            // M_StorageDetail may not be mapped if the warehouse module is not installed
            return new JSONObject().put("items", new JSONArray());
        }
    }

    private int parseIntParam(String val, int def) {
        try { return val != null ? Integer.parseInt(val) : def; } catch (Exception e) { return def; }
    }
}
