package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.widgets.*;
import com.etendoerp.metadata.widgets.resolvers.ProxyResolver;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.query.Query;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * GET /meta/widget/{instanceId}/data
 *
 * 1. Load ETMETA_DASHBOARD_WIDGET row by instanceId.
 * 2. Load ETMETA_WIDGET_CLASS row for the widget type.
 * 3. Merge class default params + instance PARAMETERS_JSON.
 * 4. Look up resolver in registry by type; fallback to ProxyResolver if EXTERNAL_DATA_URL set.
 * 5. Execute resolver, wrap result in WidgetDataResponse envelope.
 */
public class WidgetDataService extends MetadataService {

    private static final String INSTANCE_HQL =
        "select dw.id, dw.widgetClass.id, dw.parametersJSON " +
        "from etmeta_Dashboard_Widget dw where dw.id = :id and dw.active = true";

    private static final String CLASS_HQL =
        "select wc.id, wc.type, wc.resolverClass, wc.externalDataURL, wc.hQLQuery " +
        "from etmeta_Widget_Class wc where wc.id = :id";

    private static final String PARAM_DEFAULTS_HQL =
        "select p.name, p.defaultValue from etmeta_Widget_Param p " +
        "where p.widgetClass.id = :classId and p.active = true and p.isFixed = false";

    // Injected in tests; in production resolved via the static holder
    private WidgetResolverRegistry registry;

    public WidgetDataService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        this.registry = WidgetResolverRegistryHolder.getInstance();
    }

    /** Package-visible setter for unit tests. */
    void setRegistry(WidgetResolverRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void process() throws IOException {
        String instanceId = extractInstanceId(getRequest().getPathInfo());
        if (instanceId == null) throw new NotFoundException();

        try {
            OBContext.setAdminMode(true);
            Object[] instanceRow = loadInstance(instanceId);
            if (instanceRow == null) throw new NotFoundException();

            String classId    = (String) instanceRow[1];
            String paramsJson = (String) instanceRow[2];

            Object[] classRow = loadClass(classId);
            if (classRow == null) throw new NotFoundException();

            String type            = (String) classRow[1];
            String externalDataUrl = (String) classRow[3];
            Map<String, Object> params = mergeParams(classId, paramsJson);
            String pageParam     = getRequest().getParameter("page");
            String pageSizeParam = getRequest().getParameter("pageSize");
            if (pageParam     != null) params.put("_page",     pageParam);
            if (pageSizeParam != null) params.put("_pageSize", pageSizeParam);

            // Filter params from query string override class/instance defaults
            getRequest().getParameterMap().forEach((key, values) -> {
                if (!"page".equals(key) && !"pageSize".equals(key) && values.length > 0) {
                    params.put(key, values[0]);
                }
            });

            OBContext ctx = OBContext.getOBContext();
            String bearerToken = getRequest().getHeader("Authorization");
            WidgetDataContext wdCtx = new WidgetDataContext(
                    instanceId,
                    toMap(instanceRow),
                    toMap(classRow),
                    params,
                    ctx,
                    bearerToken);

            WidgetDataResolver resolver = registry != null ? registry.getResolver(type) : null;
            if (resolver == null && (externalDataUrl != null || "PROXY".equals(type))) {
                resolver = new ProxyResolver();
            }
            if (resolver == null) throw new InternalServerException("No resolver for type: " + type);

            if (!resolver.isAvailable()) {
                write(WidgetDataResponse.unavailable(instanceId, type));
                return;
            }

            JSONObject data     = resolver.resolve(wdCtx);
            JSONObject envelope = WidgetDataResponse.build(instanceId, type, data);
            write(envelope);

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private Object[] loadInstance(String instanceId) {
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(INSTANCE_HQL, Object[].class);
        q.setParameter("id", instanceId);
        return q.uniqueResult();
    }

    private Object[] loadClass(String classId) {
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(CLASS_HQL, Object[].class);
        q.setParameter("id", classId);
        return q.uniqueResult();
    }

    private Map<String, Object> mergeParams(String classId, String instanceParamsJson) throws Exception {
        Map<String, Object> merged = new HashMap<>();
        Query<Object[]> q = OBDal.getInstance().getSession()
                .createQuery(PARAM_DEFAULTS_HQL, Object[].class);
        q.setParameter("classId", classId);
        for (Object[] row : q.list()) {
            if (row[1] != null) merged.put((String) row[0], row[1]);
        }
        if (instanceParamsJson != null && !instanceParamsJson.isBlank()) {
            JSONObject overrides = new JSONObject(instanceParamsJson);
            java.util.Iterator<?> keys = overrides.keys();
            while (keys.hasNext()) {
                String k = (String) keys.next();
                try { merged.put(k, overrides.get(k)); } catch (Exception ignored) {}
            }
        }
        return merged;
    }

    private Map<String, Object> toMap(Object[] row) {
        Map<String, Object> m = new HashMap<>();
        if (row != null) {
            for (int i = 0; i < row.length; i++) m.put(String.valueOf(i), row[i]);
        }
        return m;
    }

    private String extractInstanceId(String pathInfo) {
        if (pathInfo == null) return null;
        // /widget/{instanceId}/data
        String[] parts = pathInfo.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("widget".equals(parts[i]) && !"classes".equals(parts[i + 1])) {
                return parts[i + 1];
            }
        }
        return null;
    }
}
