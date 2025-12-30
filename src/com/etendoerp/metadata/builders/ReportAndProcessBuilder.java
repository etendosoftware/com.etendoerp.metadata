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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.json.DataResolvingMode;

/**
 * Builder for legacy Report and Process entities (AD_Process).
 * Converts Process entities and their parameters to JSON format for API consumption.
 *
 * @author Futit Services S.L.
 */
public class ReportAndProcessBuilder extends Builder {

    private final Process process;

    /**
     * Constructs a ReportAndProcessBuilder with the specified process.
     * Initializes the builder to convert the given process and its parameters to JSON format.
     *
     * @param process The process entity to build JSON representation for
     */
    public ReportAndProcessBuilder(Process process) {
        this.process = process;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        // Base metadata (same strategy as ProcessDefinitionBuilder)
        JSONObject processJSON =
                converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);

        // Parameters
        JSONObject parameters = new JSONObject();
        for (ProcessParameter param : process.getADProcessParameterList()) {
            parameters.put(param.getDBColumnName(), new ProcessParameterBuilder(param).toJSON());
        }

        processJSON.put("parameters", parameters);

        return processJSON;
    }
}
