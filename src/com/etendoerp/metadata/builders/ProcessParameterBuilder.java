/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
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

    /**
     * Constructs a ProcessParameterBuilder with the specified process parameter.
     * Initializes the builder to convert the given parameter to JSON format.
     *
     * @param parameter The process parameter entity to build JSON representation for
     */
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

    private static boolean isButtonParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null &&
               BUTTON_REFERENCE_ID.equals(parameter.getReference().getId());
    }

    private static boolean isButtonListParameter(ProcessParameter parameter) {
        return parameter != null && parameter.getReference() != null &&
               "FF80818132F94B500132F9575619000A".equals(parameter.getReference().getId());
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
        if ((isListParameter(currentParameter) || isButtonListParameter(currentParameter) || isButtonParameter(currentParameter)) && currentParameter.getReferenceSearchKey() != null) {
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
