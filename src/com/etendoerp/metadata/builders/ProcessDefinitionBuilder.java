package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.service.json.DataResolvingMode;

import static com.etendoerp.metadata.utils.Constants.LIST_REFERENCE_ID;
import static com.etendoerp.metadata.utils.Constants.SELECTOR_REFERENCES;
import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;

public class ProcessDefinitionBuilder extends Builder {
    private final Process process;

    public ProcessDefinitionBuilder(Process process) {
        this.process = process;
    }

    private static boolean isSelectorParameter(Parameter parameter) {
        return parameter != null && parameter.getReference() != null &&
               SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    private static boolean isListParameter(Parameter parameter) {
        return parameter != null && parameter.getReference() != null &&
               LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    public static JSONObject getFieldProcess(Field field) throws JSONException {
        Process process = field.getColumn().getOBUIAPPProcess();

        if (process == null) {
            return new JSONObject();
        }

        JSONObject processJson = new ProcessDefinitionBuilder(process).toJSON();

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", field.getColumn().getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        processJson.put("buttonText", field.getColumn().getName());
        processJson.put("fieldName", field.getName());
        processJson.put("reference", field.getColumn().getReference().getId());
        processJson.put("manualURL", Utility.getTabURL(field.getTab(), null, false));

        return processJson;
    }

    private JSONObject buildParameterJSON(Parameter param) throws JSONException {
        JSONObject paramJSON = converter.toJsonObject(param, DataResolvingMode.FULL_TRANSLATABLE);

        if (isSelectorParameter(param)) {
            paramJSON.put("selector", getSelectorInfo(param.getId(), param.getReferenceSearchKey()));
        }
        if (isListParameter(param)) {
            paramJSON.put("refList", getListInfo(param.getReferenceSearchKey()));
        }

        return paramJSON;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONArray parameters = new JSONArray();

        for (Parameter param : process.getOBUIAPPParameterList()) {
            if (param != null) {
                parameters.put(buildParameterJSON(param));
            }
        }

        processJSON.put("parameters", parameters);

        return processJSON;
    }
}
