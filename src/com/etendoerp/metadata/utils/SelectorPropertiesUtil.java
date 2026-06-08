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

package com.etendoerp.metadata.utils;

import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.metadata.builders.FieldBuilder;

/**
 * Utility class with helpers to build selector property metadata and grid columns
 * for custom selectors. Extracted from {@link FieldBuilder} to keep that class within
 * the project's per-class method limit; the logic and behavior are unchanged.
 *
 * @author Futit Services S.L.
 */
public final class SelectorPropertiesUtil {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SelectorPropertiesUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Configures selector properties including selected, derived, and additional
     * properties.
     * Analyzes selector fields to determine which properties should be included in
     * queries
     * and which should be available for display and searching.
     *
     * @param fields       List of selector field configurations
     * @param displayField The field used for display (can be null)
     * @param valueField   The field used for values (can be null)
     * @param selectorInfo The JSON object to populate with property configurations
     * @throws JSONException if there's an error updating the JSON structure
     */
    public static void setSelectorProperties(List<SelectorField> fields, SelectorField displayField,
            SelectorField valueField, JSONObject selectorInfo) throws JSONException {
        String valueFieldProperty = valueField != null ? FieldBuilder.getValueField(
                valueField.getObuiselSelector()) : JsonConstants.IDENTIFIER;
        String displayFieldProperty = displayField != null ? FieldBuilder.getDisplayField(
                displayField.getObuiselSelector()) : JsonConstants.IDENTIFIER;

        StringBuilder selectedProperties = new StringBuilder(JsonConstants.ID);
        StringBuilder derivedProperties = new StringBuilder();
        StringBuilder extraProperties = new StringBuilder(valueFieldProperty);

        if (displayField != null && !JsonConstants.IDENTIFIER.equals(displayFieldProperty)) {
            appendWithComma(extraProperties, displayFieldProperty);
            appendWithComma(selectedProperties, displayFieldProperty);
        }

        SelectorPropertiesBuilder propertiesBuilder = new SelectorPropertiesBuilder(
                selectedProperties, derivedProperties, extraProperties);

        for (SelectorField field : fields) {
            processSelectorField(field, displayField, valueField, displayFieldProperty, valueFieldProperty,
                    propertiesBuilder);
        }

        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selectedProperties.toString());
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, extraProperties + "," + derivedProperties);
    }

    /**
     * Processes an individual selector field and categorizes it into selected,
     * derived, or extra properties.
     *
     * @param field                The selector field to process.
     * @param displayField         The configured display field for the selector.
     * @param valueField           The configured value field for the selector.
     * @param displayFieldProperty The property name of the display field.
     * @param valueFieldProperty   The property name of the value field.
     * @param propertiesBuilder    Builder containing the StringBuilders for
     *                             selected, derived, and extra properties.
     */
    private static void processSelectorField(SelectorField field, SelectorField displayField, SelectorField valueField,
            String displayFieldProperty, String valueFieldProperty, SelectorPropertiesBuilder propertiesBuilder) {
        String fieldName = FieldBuilder.getPropertyOrDataSourceField(field);

        if (JsonConstants.ID.equals(fieldName) || JsonConstants.IDENTIFIER.equals(fieldName)) {
            return;
        }

        if (fieldName.contains(JsonConstants.FIELD_SEPARATOR)) {
            appendWithComma(propertiesBuilder.derivedProperties, fieldName);
        } else {
            appendWithComma(propertiesBuilder.selectedProperties, fieldName);
        }

        if (isExtraProperty(field, displayField, valueField, fieldName, displayFieldProperty, valueFieldProperty)) {
            appendWithComma(propertiesBuilder.extraProperties, fieldName);
        }
    }

    /**
     * Determines if a selector field should be considered an extra property.
     *
     * @param field                The selector field to check.
     * @param displayField         The configured display field.
     * @param valueField           The configured value field.
     * @param fieldName            The name of the field being checked.
     * @param displayFieldProperty The property name of the display field.
     * @param valueFieldProperty   The property name of the value field.
     * @return true if the field is an extra property, false otherwise.
     */
    private static boolean isExtraProperty(SelectorField field, SelectorField displayField, SelectorField valueField,
            String fieldName, String displayFieldProperty, String valueFieldProperty) {
        return field.isOutfield() &&
                (displayField == null || fieldName.equals(displayFieldProperty)) &&
                (valueField == null || fieldName.equals(valueFieldProperty));
    }

    /**
     * Appends a value to a StringBuilder, adding a comma separator if the
     * StringBuilder is not empty.
     *
     * @param sb    The StringBuilder to append to.
     * @param value The value to append.
     */
    private static void appendWithComma(StringBuilder sb, String value) {
        if (sb.length() > 0) {
            sb.append(",");
        }
        sb.append(value);
    }

    /**
     * Builds a grid column JSON object from a selector field.
     *
     * @param selectorField The selector field to build the column for
     * @return JSONObject with the column metadata
     * @throws JSONException if there's an error creating the JSON structure
     */
    public static JSONObject buildGridColumn(SelectorField selectorField) throws JSONException {
        JSONObject column = new JSONObject();
        column.put("id", selectorField.getId());
        column.put("header", selectorField.get(SelectorField.PROPERTY_NAME, OBContext.getOBContext().getLanguage()));
        column.put("accessorKey", FieldBuilder.getPropertyOrDataSourceField(selectorField));
        column.put("enableSorting", selectorField.isSortable());
        column.put("enableFiltering", selectorField.isFilterable());
        column.put("referenceId", resolveReferenceId(selectorField));
        column.put("sortNo", selectorField.getSortno());
        return column;
    }

    /**
     * Resolves the reference ID for a selector field by checking, in order:
     * the field's own reference, its column reference, and its datasource field reference.
     *
     * @param selectorField The selector field to resolve the reference ID for
     * @return The reference ID string, or null if none is found
     */
    private static String resolveReferenceId(SelectorField selectorField) {
        if (selectorField.getReference() != null) {
            return selectorField.getReference().getId();
        }
        if (selectorField.getColumn() != null) {
            return selectorField.getColumn().getReference().getId();
        }
        if (selectorField.getObserdsDatasourceField() != null
                && selectorField.getObserdsDatasourceField().getReference() != null) {
            return selectorField.getObserdsDatasourceField().getReference().getId();
        }
        return null;
    }

    /**
     * Helper class to group selector property builders.
     * Encapsulates the StringBuilders used to accumulate selected, derived, and
     * extra properties.
     */
    private static class SelectorPropertiesBuilder {
        final StringBuilder selectedProperties;
        final StringBuilder derivedProperties;
        final StringBuilder extraProperties;

        SelectorPropertiesBuilder(StringBuilder selectedProperties, StringBuilder derivedProperties,
                StringBuilder extraProperties) {
            this.selectedProperties = selectedProperties;
            this.derivedProperties = derivedProperties;
            this.extraProperties = extraProperties;
        }
    }
}
