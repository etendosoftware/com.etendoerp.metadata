package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.RefWindow;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.service.json.DataResolvingMode;

import java.util.List;

import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;
import static com.etendoerp.metadata.utils.Constants.*;

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

    private static boolean isWindowReference(Parameter parameter) {
        return parameter != null && parameter.getReference() != null && WINDOW_REFERENCE_ID.equals(
            parameter.getReference().getId());
    }


    private String getReadOnlyLogic(Parameter parameter) {
        String readOnlyLogic = parameter.getReadOnlyLogic();

        if (readOnlyLogic != null && !readOnlyLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(readOnlyLogic, parameter, true);
            return parser.getJSExpression();
        }

        return null;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        if (hasReadOnlyLogic(parameter)) {
            json.put("readOnlyLogicExpression", getReadOnlyLogic(parameter));
        }

        if (isSelectorParameter(parameter)) {
            json.put("selector", getSelectorInfo(parameter.getId(), parameter.getReferenceSearchKey()));
        }

        if (isListParameter(parameter)) {
            json.put("refList", getListInfo(parameter.getReferenceSearchKey(), language));
        }

        if (isWindowReference(parameter)) {
            json.put("window", getWindowInfo(parameter.getReferenceSearchKey()));
        }

        return json;
    }

    private boolean hasReadOnlyLogic(Parameter parameter) {
        return parameter.getReadOnlyLogic() != null && !parameter.getReadOnlyLogic().isBlank();
    }

    private JSONObject getWindowInfo(Reference referenceSearchKey) {
        List<RefWindow> refWindows = referenceSearchKey.getOBUIAPPRefWindowList();

        if (!refWindows.isEmpty()) {
            return new WindowBuilder(refWindows.get(0).getWindow().getId()).toJSON();
        }

        return null;
    }
}
