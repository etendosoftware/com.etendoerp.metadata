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
