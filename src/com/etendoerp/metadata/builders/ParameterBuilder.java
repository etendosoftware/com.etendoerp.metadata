package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.utils.Constants.LIST_REFERENCE_ID;
import static com.etendoerp.metadata.utils.Constants.SELECTOR_REFERENCES;

import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.ForeignKeyDomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.util.Check;
import org.openbravo.client.application.Parameter;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.metadata.data.ReferenceSelectors;
import com.etendoerp.metadata.utils.Constants;

/**
 * @author luuchorocha
 */
public class ParameterBuilder extends Builder {
    private final Parameter parameter;

    public ParameterBuilder(Parameter parameter) {
        this.parameter = parameter;
    }

    public static JSONObject getSelectorInfo(String fieldId, Reference ref) throws JSONException {
        ReferenceSelectors result = getReferenceSelectors(ref);

        if (result.selector != null) {
            return addSelectorInfo(fieldId, result.selector);
        } else if (result.treeSelector != null) {
            return addTreeSelectorInfo(fieldId, result.treeSelector);
        } else {
            return addComboTableSelectorInfo(fieldId);
        }
    }

    private static ReferenceSelectors getReferenceSelectors(Reference ref) {
        Selector selector = null;
        ReferencedTree treeSelector = null;

        if (ref != null) {
            if (!ref.getOBUISELSelectorList().isEmpty()) {
                selector = ref.getOBUISELSelectorList().get(0);
            }
            if (!ref.getADReferencedTreeList().isEmpty()) {
                treeSelector = ref.getADReferencedTreeList().get(0);
            }
        }

        return new ReferenceSelectors(selector, treeSelector);
    }

    private static JSONObject addComboTableSelectorInfo(String fieldId) throws JSONException {
        JSONObject selectorInfo = new JSONObject();

        selectorInfo.put(Constants.SELECTOR_DEFINITION_PROPERTY, (Object) null);
        selectorInfo.put(JsonConstants.SORTBY_PARAMETER, JsonConstants.IDENTIFIER);
        selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, JsonConstants.TEXTMATCH_SUBSTRING);
        selectorInfo.put(Constants.DATASOURCE_PROPERTY, Constants.TABLE_DATASOURCE);
        selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
        selectorInfo.put(Constants.FIELD_ID_PROPERTY, fieldId);
        selectorInfo.put(Constants.DISPLAY_FIELD_PROPERTY, JsonConstants.IDENTIFIER);
        selectorInfo.put(Constants.VALUE_FIELD_PROPERTY, JsonConstants.ID);
        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, JsonConstants.ID);
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, JsonConstants.ID + ",");

        return selectorInfo;
    }

    private static JSONObject addTreeSelectorInfo(String fieldId, ReferencedTree treeSelector) throws JSONException {
        JSONObject selectorInfo = new JSONObject();

        selectorInfo.put(Constants.DATASOURCE_PROPERTY, Constants.TREE_DATASOURCE);
        selectorInfo.put(Constants.SELECTOR_DEFINITION_PROPERTY, treeSelector.getId());
        selectorInfo.put("treeReferenceId", treeSelector.getId());
        if (treeSelector.getDisplayfield() != null) {
            selectorInfo.put(JsonConstants.SORTBY_PARAMETER, treeSelector.getDisplayfield().getProperty());
            selectorInfo.put(Constants.DISPLAY_FIELD_PROPERTY, treeSelector.getDisplayfield().getProperty());
        }
        selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, JsonConstants.TEXTMATCH_SUBSTRING);
        selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
        selectorInfo.put(Constants.FIELD_ID_PROPERTY, fieldId);
        selectorInfo.put(Constants.VALUE_FIELD_PROPERTY, treeSelector.getValuefield().getProperty());
        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, JsonConstants.ID);
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, JsonConstants.ID + ",");

        return selectorInfo;
    }

    private static JSONObject addSelectorInfo(String fieldId, Selector selector) throws JSONException {
        String dataSourceId;
        JSONObject selectorInfo = new JSONObject();

        if (selector.getObserdsDatasource() != null) {
            dataSourceId = selector.getObserdsDatasource().getId();
        } else if (selector.isCustomQuery()) {
            dataSourceId = Constants.CUSTOM_QUERY_DS;
        } else {
            dataSourceId = selector.getTable().getName();
        }

        selectorInfo.put(Constants.DATASOURCE_PROPERTY, dataSourceId);
        selectorInfo.put(Constants.SELECTOR_DEFINITION_PROPERTY, selector.getId());
        selectorInfo.put("filterClass", "org.openbravo.userinterface.selector.SelectorDataSourceFilter");

        if (selector.getDisplayfield() != null) {
            selectorInfo.put(JsonConstants.SORTBY_PARAMETER,
                selector.getDisplayfield().getDisplayColumnAlias() != null ? selector.getDisplayfield().getDisplayColumnAlias() : selector.getDisplayfield().getProperty());
        } else {
            selectorInfo.put(JsonConstants.SORTBY_PARAMETER, JsonConstants.IDENTIFIER);
        }
        selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
        selectorInfo.put(Constants.FIELD_ID_PROPERTY, fieldId);
        // For now we only support suggestion style search (only drop down)
        selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, selector.getSuggestiontextmatchstyle());

        setSelectorProperties(selector.getOBUISELSelectorFieldList(), selector.getDisplayfield(),
            selector.getValuefield(), selectorInfo);

        selectorInfo.put("extraSearchFields", getExtraSearchFields(selector));
        selectorInfo.put(Constants.DISPLAY_FIELD_PROPERTY, getDisplayField(selector));
        selectorInfo.put(Constants.VALUE_FIELD_PROPERTY, getValueField(selector));

        return selectorInfo;
    }

    private static void setSelectorProperties(List<SelectorField> fields, SelectorField displayField,
        SelectorField valueField, JSONObject selectorInfo) throws JSONException {
        String valueFieldProperty = valueField != null ? getValueField(
            valueField.getObuiselSelector()) : JsonConstants.IDENTIFIER;
        String displayFieldProperty = displayField != null ? getDisplayField(
            displayField.getObuiselSelector()) : JsonConstants.IDENTIFIER;

        StringBuilder selectedProperties = new StringBuilder(JsonConstants.ID);
        StringBuilder derivedProperties = new StringBuilder();
        StringBuilder extraProperties = new StringBuilder(valueFieldProperty);

        // get extra properties
        if (displayField != null && !JsonConstants.IDENTIFIER.equals(displayFieldProperty)) {
            extraProperties.append(",").append(displayFieldProperty);
            selectedProperties.append(",").append(displayFieldProperty);
        }

        // get selected and derived properties
        for (SelectorField field : fields) {
            String fieldName = getPropertyOrDataSourceField(field);

            if (fieldName.equals(JsonConstants.ID) || fieldName.equals(JsonConstants.IDENTIFIER)) {
                continue;
            }

            if (fieldName.contains(JsonConstants.FIELD_SEPARATOR)) {
                if (derivedProperties.length() == 0) {
                    derivedProperties = new StringBuilder(fieldName);
                } else {
                    derivedProperties.append(',').append(fieldName);
                }
            } else {
                selectedProperties.append(",").append(fieldName);
            }

            if ((!field.isOutfield() || (displayField == null || fieldName.equals(
                displayFieldProperty))) && (valueField == null || fieldName.equals(valueFieldProperty))) {
                if (field.isOutfield()) {
                    extraProperties.append(",").append(fieldName);
                }
            }
        }

        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selectedProperties.toString());
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, extraProperties + "," + derivedProperties);
    }

    public static JSONArray getListInfo(Reference refList) throws JSONException {
        JSONArray refListValues = new JSONArray();

        for (org.openbravo.model.ad.domain.List listValue : refList.getADListList()) {
            JSONObject jsonListValue = new JSONObject();
            jsonListValue.put("id", listValue.getId());
            jsonListValue.put("label", listValue.getName());
            jsonListValue.put("value", listValue.getSearchKey());
            refListValues.put(jsonListValue);
        }
        return refListValues;
    }

    public static String getExtraSearchFields(Selector selector) {
        final String displayField = getDisplayField(selector);
        final StringBuilder sb = new StringBuilder();
        for (SelectorField selectorField : selector.getOBUISELSelectorFieldList().stream().filter(
            SelectorField::isActive).collect(Collectors.toList())) {
            String fieldName = getPropertyOrDataSourceField(selectorField);
            if (fieldName.equals(displayField)) {
                continue;
            }
            // prevent booleans as search fields, they don't work
            if (selectorField.isSearchinsuggestionbox() && !isBoolean(selectorField)) {

                // handle the case that the field is a foreign key
                // in that case always show the identifier
                final DomainType domainType = getDomainType(selectorField);
                if (domainType instanceof ForeignKeyDomainType) {
                    fieldName = fieldName + DalUtil.FIELDSEPARATOR + JsonConstants.IDENTIFIER;
                }

                if (!(sb.length() == 0)) {
                    sb.append(",");
                }
                sb.append(fieldName);
            }
        }
        return sb.toString();
    }

    public static String getDisplayField(Selector selector) {
        if (selector.getDisplayfield() != null) {
            return getPropertyOrDataSourceField(selector.getDisplayfield());
        }

        // try to be intelligent when there is a datasource
        if (selector.getObserdsDatasource() != null) {
            final DataSource dataSource = selector.getObserdsDatasource();
            // a complete manual datasource which does not have a table
            // and which has a field defined
            if (dataSource.getTable() == null && !dataSource.getOBSERDSDatasourceFieldList().isEmpty()) {
                final DatasourceField dsField = dataSource.getOBSERDSDatasourceFieldList().get(0);
                return dsField.getName().replace(DalUtil.DOT, DalUtil.FIELDSEPARATOR);
            }
        }

        // in all other cases use an identifier
        return JsonConstants.IDENTIFIER;
    }

    public static String getValueField(Selector selector) {
        if (selector.getValuefield() != null) {
            final String valueField = getPropertyOrDataSourceField(selector.getValuefield());
            if (!selector.isCustomQuery()) {
                final DomainType domainType = getDomainType(selector.getValuefield());
                if (domainType instanceof ForeignKeyDomainType) {
                    return valueField + DalUtil.FIELDSEPARATOR + JsonConstants.ID;
                }
            }
            return valueField;
        }

        if (selector.getObserdsDatasource() != null) {
            final DataSource dataSource = selector.getObserdsDatasource();
            // a complete manual datasource which does not have a table
            // and which has a field defined
            if (dataSource.getTable() == null && !dataSource.getOBSERDSDatasourceFieldList().isEmpty()) {
                final DatasourceField dsField = dataSource.getOBSERDSDatasourceFieldList().get(0);
                return dsField.getName();
            }
        }

        return JsonConstants.ID;
    }

    private static boolean isBoolean(SelectorField selectorField) {
        final DomainType domainType = getDomainType(selectorField);
        if (domainType instanceof PrimitiveDomainType) {
            PrimitiveDomainType primitiveDomainType = (PrimitiveDomainType) domainType;
            return boolean.class == primitiveDomainType.getPrimitiveType() || Boolean.class == primitiveDomainType.getPrimitiveType();
        }
        return false;
    }

    private static DomainType getDomainType(SelectorField selectorField) {
        if (selectorField.getObuiselSelector().getTable() != null && selectorField.getProperty() != null) {
            final String entityName = selectorField.getObuiselSelector().getTable().getName();
            final Entity entity = ModelProvider.getInstance().getEntity(entityName);
            final Property property = DalUtil.getPropertyFromPath(entity, selectorField.getProperty());
            Check.isNotNull(property, "Property " + selectorField.getProperty() + " not found in Entity " + entity);
            return property.getDomainType();
        } else if (selectorField.getObuiselSelector().getTable() != null && selectorField.getObuiselSelector().isCustomQuery() && selectorField.getReference() != null) {
            return getDomainType(selectorField.getReference().getId());
        } else if (selectorField.getObserdsDatasourceField().getReference() != null) {
            return getDomainType(selectorField.getObserdsDatasourceField().getReference().getId());
        }
        return null;
    }

    private static DomainType getDomainType(String referenceId) {
        final org.openbravo.base.model.Reference reference = ModelProvider.getInstance().getReference(referenceId);
        Check.isNotNull(reference, "No reference found for referenceid " + referenceId);
        return reference.getDomainType();
    }

    private static String getPropertyOrDataSourceField(SelectorField selectorField) {
        final String result;
        if (selectorField.getProperty() != null) {
            result = selectorField.getProperty();
        } else if (selectorField.getDisplayColumnAlias() != null) {
            result = selectorField.getDisplayColumnAlias();
        } else if (selectorField.getObserdsDatasourceField() != null) {
            result = selectorField.getObserdsDatasourceField().getName();
        } else {
            throw new IllegalStateException(
                "Selector field " + selectorField + " has a null datasource and a null property");
        }
        return result.replace(DalUtil.DOT, DalUtil.FIELDSEPARATOR);
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
        JSONObject json = converter.toJsonObject(parameter, DataResolvingMode.FULL_TRANSLATABLE);

        if (isSelectorParameter(parameter)) {
            json.put("selector", getSelectorInfo(parameter.getId(), parameter.getReferenceSearchKey()));
        }

        if (isListParameter(parameter)) {
            json.put("refList", getListInfo(parameter.getReferenceSearchKey()));
        }

        return json;
    }

}
