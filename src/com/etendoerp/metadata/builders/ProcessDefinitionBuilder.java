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
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.service.json.DataResolvingMode;

public class ProcessDefinitionBuilder extends Builder {
    private final Process process;

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

        processJSON.put("parameters", parameters);
        processJSON.put("onLoad", process.getEtmetaOnload());
        processJSON.put("onProcess", process.getEtmetaOnprocess());

        return processJSON;
    }
}
