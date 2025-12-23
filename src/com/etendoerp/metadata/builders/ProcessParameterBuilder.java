package com.etendoerp.metadata.builders;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.service.json.DataResolvingMode;

import static com.etendoerp.metadata.builders.FieldBuilder.getListInfo;
import static com.etendoerp.metadata.builders.FieldBuilder.getSelectorInfo;
import static com.etendoerp.metadata.utils.Constants.*;

/**
 * Builder for legacy Report and Process parameters (AD_Process_Para)
 */
public class ProcessParameterBuilder extends Builder {

    private final ProcessParameter parameter;

    public ProcessParameterBuilder(ProcessParameter parameter) {
        this.parameter = parameter;
    }

    private static boolean isSelectorParameter(ProcessParameter parameter) {
        return parameter != null
                && parameter.getReference() != null
                && SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    private static boolean isListParameter(ProcessParameter parameter) {
        return parameter != null
                && parameter.getReference() != null
                && LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        // Selector (Table / Search reference)
        if (isSelectorParameter(parameter)) {
            json.put(
                    "selector",
                    getSelectorInfo(parameter.getId(), parameter.getReferenceSearchKey())
            );
        }

        // List reference
        if (isListParameter(parameter)) {
            json.put(
                    "refList",
                    getListInfo(parameter.getReferenceSearchKey(), language)
            );
        }

        // Explicit legacy flags
        json.put("isRange", parameter.isRange());
        json.put("valueFormat", parameter.getValueFormat());
        json.put("minValue", parameter.getMinValue());
        json.put("maxValue", parameter.getMaxValue());

        return json;
    }
}
