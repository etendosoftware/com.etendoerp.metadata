/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.ReportDefinition;
import org.openbravo.service.json.DataResolvingMode;

import java.util.List;

/**
 * Builds a JSON representation of an OBUIAPP process definition and its parameters.
 */
public class ProcessDefinitionBuilder extends Builder {
    private static final String PARAMETERS = "parameters";
    private static final String REPORT = "report";
    /** Key auto-emitted by DataToJsonConverter from the ORM property name (legacy casing). */
    private static final String ETMETA_ONLOAD_RAW = "eTMETAOnload";
    /** Key the converter may emit for the custom-component flag with the legacy ETMETA casing. */
    private static final String ETMETA_CUSTOM_COMPONENT_RAW = "eTMETACustomComponent";
    /** Public, normalized key for the onLoad hook in the JSON response. */
    private static final String ETMETA_ONLOAD = "etmetaOnload";
    /** Public key for the onProcess hook in the JSON response. */
    private static final String ETMETA_ONPROCESS = "etmetaOnprocess";
    /** Public key for the onRefresh hook in the JSON response. */
    private static final String ETMETA_ON_REFRESH = "etmetaOnRefresh";
    /** Public key for the shared payscript module body in the JSON response. */
    private static final String ETMETA_PAYSCRIPT_LOGIC = "etmetaPayscriptLogic";
    /** Public, normalized key for the custom-component flag in the JSON response. */
    private static final String ETMETA_CUSTOM_COMPONENT = "etmetaCustomComponent";

    private final Process process;

    /**
     * Creates a new ProcessDefinitionBuilder for the given process definition.
     *
     * @param process the OBUIAPP process definition to build JSON for
     */
    public ProcessDefinitionBuilder(Process process) {
        this.process = process;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONObject parameters = new JSONObject();

        for (Parameter param : process.getOBUIAPPParameterList()) {
            parameters.put(param.getDBColumnName(), new ParameterBuilder(param).toJSON());
        }

        processJSON.put(PARAMETERS, parameters);
        // Drop the converter's legacy-cased keys; the JS-hook columns are emitted
        // explicitly below.
        processJSON.remove(ETMETA_ONLOAD_RAW);
        processJSON.remove(ETMETA_CUSTOM_COMPONENT_RAW);
        // Emit the JS-hook columns directly from the entity so they are always present
        // regardless of the role's derived-read access to OBUIAPP_Process: the converter
        // skips non-derived-readable properties for business roles, which would otherwise
        // strip these hooks from the payload.
        putValueOrNull(processJSON, ETMETA_ONLOAD, process.getETMETAOnload());
        putValueOrNull(processJSON, ETMETA_ONPROCESS, process.getEtmetaOnprocess());
        putValueOrNull(processJSON, ETMETA_ON_REFRESH, process.getEtmetaOnRefresh());
        putValueOrNull(processJSON, ETMETA_PAYSCRIPT_LOGIC, process.getEtmetaPayscriptLogic());
        putValueOrNull(processJSON, ETMETA_CUSTOM_COMPONENT, process.isEtmetaCustomComponent());

        List<ReportDefinition> reports = process.getOBUIAPPReportList();
        if (!reports.isEmpty()) {
            processJSON.put(REPORT, new ReportDefinitionBuilder(reports.get(0)).toJSON());
        }

        return processJSON;
    }
}
