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

    private static boolean isSelectorParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null &&
                SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    private static boolean isListParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null &&
                LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    public static JSONObject getFieldProcess(Field field, Process process) throws JSONException {
        if (process == null) {
            return new JSONObject();
        }

        JSONObject processJson = new ProcessActionBuilder(process).toJSON();

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", field.getColumn().getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        processJson.put("buttonText", field.getColumn().getName());
        processJson.put("fieldName", field.getName());
        processJson.put("reference", field.getColumn().getReference().getId());
        processJson.put("manualURL", Utility.getTabURL(field.getTab(), null, false));

        return processJson;
    }

    private JSONObject buildParameterJSON(ProcessParameter param) throws JSONException {
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
