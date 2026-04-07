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


    private static boolean isButtonListParameter(Parameter parameter) {
        return parameter != null && parameter.getReference() != null &&
               BUTTON_LIST_REFERENCE_ID.equals(parameter.getReference().getId());
    }

    private static boolean isWindowReference(Parameter parameter) {
        return parameter != null && parameter.getReference() != null && WINDOW_REFERENCE_ID.equals(
            parameter.getReference().getId());
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        addReadOnlyLogicExpression(json, parameter);
        addDisplayLogicExpression(json, parameter);
        addSelectorInfo(json, parameter);
        addListInfo(json, parameter);
        addButtonListInfo(json, parameter);
        addWindowInfo(json, parameter);

        return json;
    }

    private void addReadOnlyLogicExpression(JSONObject json, Parameter parameter) {
        try {
            String readOnlyLogic = parameter.getReadOnlyLogic();
            if (readOnlyLogic != null && !readOnlyLogic.isBlank()) {
                DynamicExpressionParser parser = new DynamicExpressionParser(readOnlyLogic, parameter, true);
                json.put("readOnlyLogicExpression", parser.getJSExpression());
            }
        } catch (Exception e) {
            logger.warn("Error building readOnlyLogic for parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }

    private void addDisplayLogicExpression(JSONObject json, Parameter parameter) {
        try {
            String displayLogic = parameter.getDisplayLogic();
            if (displayLogic != null && !displayLogic.isBlank()) {
                DynamicExpressionParser parser = new DynamicExpressionParser(displayLogic, parameter, false);
                json.put("displayLogicExpression", parser.getJSExpression());
            }
        } catch (Exception e) {
            logger.warn("Error building displayLogic for parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }

    private void addSelectorInfo(JSONObject json, Parameter parameter) {
        try {
            if (isSelectorParameter(parameter)) {
                json.put("selector", getSelectorInfo(parameter.getId(), parameter.getReferenceSearchKey()));
            }
        } catch (Exception e) {
            logger.warn("Error building selector info for parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }

    private void addListInfo(JSONObject json, Parameter parameter) {
        try {
            if (isListParameter(parameter)) {
                json.put("refList", getListInfo(parameter.getReferenceSearchKey(), language));
            }
        } catch (Exception e) {
            logger.warn("Error building refList for parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }

    private void addButtonListInfo(JSONObject json, Parameter parameter) {
        try {
            if (isButtonListParameter(parameter) && parameter.getReferenceSearchKey() != null) {
                json.put("refList", getListInfo(parameter.getReferenceSearchKey(), language));
            }
        } catch (Exception e) {
            logger.warn("Error building refList for BUTTON_LIST parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }

    private void addWindowInfo(JSONObject json, Parameter parameter) {
        try {
            if (isWindowReference(parameter)) {
                Reference referenceSearchKey = parameter.getReferenceSearchKey();
                List<RefWindow> refWindows = referenceSearchKey.getOBUIAPPRefWindowList();
                if (!refWindows.isEmpty()) {
                    json.put("window", new WindowBuilder(refWindows.get(0).getWindow().getId()).toJSON());
                }
            }
        } catch (Exception e) {
            logger.warn("Error building window info for parameter {}: {}", parameter.getId(), e.getMessage(), e);
        }
    }
}
