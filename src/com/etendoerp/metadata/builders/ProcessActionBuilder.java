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

import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;
import static com.etendoerp.metadata.utils.Constants.LIST_REFERENCE_ID;
import static com.etendoerp.metadata.utils.Constants.SELECTOR_REFERENCES;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.json.DataResolvingMode;

public class ProcessActionBuilder extends Builder {
    private final Process process;

    public ProcessActionBuilder(Process process) {
        this.process = process;
    }

    protected static boolean isSelectorParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null && SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    protected static boolean isListParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null && LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    public static JSONObject getFieldProcess(Field field, Process process) throws JSONException {
        if (process == null) {
            return new JSONObject();
        }

        JSONObject processJson = new ProcessActionBuilder(process).toJSON();

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", field.getColumn().getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        String displayLogic = field.getDisplayLogic();
        if (displayLogic != null && !displayLogic.isBlank()) {
            try {
                org.openbravo.client.application.DynamicExpressionParser parser = new org.openbravo.client.application.DynamicExpressionParser(
                        displayLogic, field.getTab(), field);
                processJson.put("displayLogicExpression", parser.getJSExpression());
            } catch (Exception e) {
                // Ignore error and log
            }
        }
        processJson.put("buttonText", field.getColumn().getName());
        processJson.put("fieldName", field.getName());
        processJson.put("reference", field.getColumn().getReference().getId());
        processJson.put("manualURL", Utility.getTabURL(field.getTab(), null, false));

        return processJson;
    }

    protected JSONObject buildParameterJSON(ProcessParameter param) throws JSONException {
        JSONObject paramJSON = converter.toJsonObject(param, DataResolvingMode.FULL_TRANSLATABLE);

        if (isSelectorParameter(param)) {
            paramJSON.put("selector", getSelectorInfo(param.getId(), param.getReferenceSearchKey()));
        }
        if (isListParameter(param)) {
            paramJSON.put("refList",
                    getListInfo(param.getReferenceSearchKey(), OBContext.getOBContext().getLanguage()));
        }

        return paramJSON;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONArray parameters = new JSONArray();

        for (ProcessParameter param : process.getADProcessParameterList()) {
            if (param != null) {
                parameters.put(buildParameterJSON(param));
            }
        }

        processJSON.put("parameters", parameters);

        return processJSON;
    }
}
