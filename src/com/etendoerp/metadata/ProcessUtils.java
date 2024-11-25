package com.etendoerp.metadata;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;


public class ProcessUtils {
    private static final DataToJsonConverter converter = new DataToJsonConverter();


    public static JSONObject createProcessJSON(Process process) throws JSONException {
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONArray parameters = new JSONArray();

        for (Parameter param : process.getOBUIAPPParameterList()) {
            JSONObject paramJSON = converter.toJsonObject(param, DataResolvingMode.FULL_TRANSLATABLE);
            parameters.put(paramJSON);
        }

        processJSON.put("parameters", parameters);
        return processJSON;
    }
}
