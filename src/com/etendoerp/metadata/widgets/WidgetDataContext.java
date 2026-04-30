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
