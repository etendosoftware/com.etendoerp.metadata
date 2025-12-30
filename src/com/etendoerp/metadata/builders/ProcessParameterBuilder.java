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
 * @author Futit Services S.L.
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
        ProcessParameter currentParameter = this.parameter;
        JSONObject json = converter.toJsonObject(currentParameter, DataResolvingMode.FULL_TRANSLATABLE);

        // Selector (Table / Search reference)
        if (isSelectorParameter(currentParameter)) {
            json.put(
                    "selector",
                    getSelectorInfo(currentParameter.getId(), currentParameter.getReferenceSearchKey())
            );
        }

        // List reference
        if (isListParameter(currentParameter)) {
            json.put(
                    "refList",
                    getListInfo(currentParameter.getReferenceSearchKey(), language)
            );
        }

        // Explicit legacy flags
        json.put("isRange", currentParameter.isRange());
        json.put("valueFormat", currentParameter.getValueFormat());
        json.put("minValue", currentParameter.getMinValue());
        json.put("maxValue", currentParameter.getMaxValue());

        return json;
    }
}
