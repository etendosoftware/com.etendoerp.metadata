package com.etendoerp.metadata.widgets;

import org.openbravo.dal.core.OBContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Carries all context needed by a WidgetDataResolver.
 * instanceData: the ETMETA_DASHBOARD_WIDGET row fields as a map.
 * classData:    the ETMETA_WIDGET_CLASS row fields as a map.
 * params:       merged parameter values (class defaults + instance PARAMETERS_JSON).
 */
public class WidgetDataContext {
    private final String instanceId;
    private final Map<String, Object> instanceData;
    private final Map<String, Object> classData;
    private final Map<String, Object> params;
    private final OBContext obContext;
    private final String bearerToken;

    /**
     * Creates a new WidgetDataContext.
     *
     * @param instanceId   the widget instance identifier
     * @param instanceData the dashboard widget row fields
     * @param classData    the widget class row fields
     * @param params       merged parameter values
     * @param obContext     the current OBContext
     * @param bearerToken  the Authorization header value, or {@code null}
     */
    public WidgetDataContext(String instanceId,
                             Map<String, Object> instanceData,
                             Map<String, Object> classData,
                             Map<String, Object> params,
                             OBContext obContext,
                             String bearerToken) {
        this.instanceId = instanceId;
        this.instanceData = new HashMap<>(instanceData);
        this.classData    = new HashMap<>(classData);
        this.params       = new HashMap<>(params);
        this.obContext    = obContext;
        this.bearerToken  = bearerToken;
    }

    public String getInstanceId()                  { return instanceId; }
    public Map<String, Object> getInstanceData()   { return instanceData; }
    public Map<String, Object> getClassData()      { return classData; }
    public Map<String, Object> getParams()         { return params; }
    public OBContext getObContext()                 { return obContext; }
    public String getBearerToken()                 { return bearerToken; }

    /**
     * Returns a class-data field value as a String.
     *
     * @param key the field key
     * @return the value as String, or {@code null}
     */
    public String classString(String key) {
        Object v = classData.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Returns a parameter value as a String.
     *
     * @param key the parameter name
     * @return the value as String, or {@code null}
     */
    public String param(String key) {
        Object v = params.get(key);
        return v != null ? v.toString() : null;
    }
}
