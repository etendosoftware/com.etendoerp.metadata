package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.widgets.WidgetDataResolver;
import com.etendoerp.metadata.widgets.WidgetResolverRegistryHolder;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * GET /meta/widget/classes — returns all active ETMETA_WIDGET_CLASS records with their params.
 */
public class WidgetClassesService extends MetadataService {

    private static final String CLASS_HQL =
        "select wc.id, wc.name, wc.type, wc.title, wc.description, " +
        "wc.defaultWidth, wc.defaultHeight, wc.refreshInterval " +
        "from etmeta_Widget_Class wc where wc.active = true order by wc.name";

    private static final String PARAM_HQL =
        "select p.name, p.displayName, p.type, p.isRequired, p.isFixed, " +
        "p.defaultValue, p.listValues " +
        "from etmeta_Widget_Param p where p.widgetClass.id = :classId " +
        "and p.active = true order by p.sequence";

    /**
     * Creates a new WidgetClassesService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public WidgetClassesService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            JSONArray classes = new JSONArray();
            Query<Object[]> q = OBDal.getInstance().getSession()
                    .createQuery(CLASS_HQL, Object[].class);
            for (Object[] row : q.list()) {
                String type = (String) row[2];
                WidgetDataResolver resolver = WidgetResolverRegistryHolder.getInstance().getResolver(type);
                boolean available = resolver == null || resolver.isAvailable();
                JSONObject cls = new JSONObject()
                        .put("widgetClassId", row[0])
                        .put("name",          row[1])
                        .put("type",          type)
                        .put("title",         row[3])
                        .put("description",   row[4])
                        .put("defaultWidth",  row[5])
                        .put("defaultHeight", row[6])
                        .put("refreshInterval", row[7])
                        .put("available",     available)
                        .put("params", buildParams((String) row[0]));
                classes.put(cls);
            }
            write(new JSONObject().put("classes", classes));
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONArray buildParams(String classId) throws Exception {
        JSONArray params = new JSONArray();
        Query<Object[]> pq = OBDal.getInstance().getSession()
                .createQuery(PARAM_HQL, Object[].class);
        pq.setParameter("classId", classId);
        for (Object[] p : pq.list()) {
            JSONObject param = new JSONObject()
                    .put("name",         p[0])
                    .put("displayName",  p[1])
                    .put("type",         p[2])
                    .put("required",     Boolean.TRUE.equals(p[3]))
                    .put("fixed",        Boolean.TRUE.equals(p[4]))
                    .put("defaultValue", p[5]);
            if (p[6] != null) {
                param.put("listValues", parseListValues((String) p[6]));
            }
            params.put(param);
        }
        return params;
    }

    private JSONArray parseListValues(String raw) throws Exception {
        // Format: "value1:label1,value2:label2"
        JSONArray arr = new JSONArray();
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) {
                arr.put(new JSONObject().put("value", parts[0].trim()).put("label", parts[1].trim()));
            }
        }
        return arr;
    }
}
