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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

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
            String expression = parser.getJSExpression();
            return expression;
        }

        return null;
    }

    private String getDisplayLogic(Parameter parameter) {
        String displayLogic = parameter.getDisplayLogic();

        if (displayLogic != null && !displayLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(displayLogic, parameter, false);
            String expression = parser.getJSExpression();
            return expression;
        }

        return null;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        if (hasReadOnlyLogic(parameter)) {
            json.put("readOnlyLogicExpression", getReadOnlyLogic(parameter));
        }

        if (hasDisplayLogic(parameter)) {
            json.put("displayLogicExpression", getDisplayLogic(parameter));
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

    private boolean hasDisplayLogic(Parameter parameter) {
        return parameter.getDisplayLogic() != null && !parameter.getDisplayLogic().isBlank();
    }

    private JSONObject getWindowInfo(Reference referenceSearchKey) {
        List<RefWindow> refWindows = referenceSearchKey.getOBUIAPPRefWindowList();

        if (!refWindows.isEmpty()) {
            return new WindowBuilder(refWindows.get(0).getWindow().getId()).toJSON();
        }

        return null;
    }
}
