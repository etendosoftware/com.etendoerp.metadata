package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.I18NComponent;

public class LabelsBuilder extends Builder {
  @Override
  public JSONObject toJSON() throws JSONException {
    JSONObject result = new JSONObject();

    for (I18NComponent.Label label : WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class).getLabels()) {
      result.put(label.getKey(), label.getValue());
    }

    return result;
  }
}
