package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;
import static com.etendoerp.metadata.utils.Constants.LIST_REFERENCE_ID;
import static com.etendoerp.metadata.utils.Constants.SELECTOR_REFERENCES;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.DataResolvingMode;

/**
 * @author luuchorocha
 */
public class ParameterBuilder extends Builder {
    private final Parameter parameter;

    public ParameterBuilder(Parameter parameter) {
        this.parameter = parameter;
    }

    private static boolean isSelectorParameter(Parameter parameter) {
        return parameter != null && parameter.getReference() != null && SELECTOR_REFERENCES.contains(
            parameter.getReference().getId());
    }

    private static boolean isListParameter(Parameter parameter) {
        return parameter != null && parameter.getReference() != null && LIST_REFERENCE_ID.contains(
            parameter.getReference().getId());
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        OBContext context = OBContext.getOBContext();
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        if (isSelectorParameter(parameter)) {
            json.put("selector", getSelectorInfo(parameter.getId(), parameter.getReferenceSearchKey()));
        }

        if (isListParameter(parameter)) {
            json.put("refList", getListInfo(parameter.getReferenceSearchKey(), language));
        }

        return json;
    }

}
