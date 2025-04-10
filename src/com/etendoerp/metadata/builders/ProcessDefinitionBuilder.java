package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.service.json.DataResolvingMode;

public class ProcessDefinitionBuilder extends Builder {
    private final Process process;

    public ProcessDefinitionBuilder(Process process) {
        this.process = process;
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

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONArray parameters = new JSONArray();

        for (Parameter param : process.getOBUIAPPParameterList()) {
            if (param != null) {
                parameters.put(new ParameterBuilder(param).toJSON());
            }
        }

        processJSON.put("parameters", parameters);

        return processJSON;
    }
}
