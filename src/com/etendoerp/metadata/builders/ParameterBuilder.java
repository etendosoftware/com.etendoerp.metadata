package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;
import static com.etendoerp.metadata.utils.Constants.LIST_REFERENCE_ID;
import static com.etendoerp.metadata.utils.Constants.SELECTOR_REFERENCES;
import static com.etendoerp.metadata.utils.Constants.WINDOW_REFERENCE_ID;

import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.RefWindow;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
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

    private static boolean isWindowReference(Parameter parameter) {
        return parameter != null && parameter.getReference() != null && WINDOW_REFERENCE_ID.equals(
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

        if (isWindowReference(parameter)) {
            json.put("window", getWindowInfo(parameter.getReferenceSearchKey(), language));
        }

        return json;
    }

    private JSONObject getWindowInfo(Reference referenceSearchKey, Language language) {
        List<RefWindow> refWindows = referenceSearchKey.getOBUIAPPRefWindowList();

        if (!refWindows.isEmpty()) {
            return new WindowBuilder(refWindows.get(0).getWindow().getId()).toJSON();
        }

        return null;
    }
}
