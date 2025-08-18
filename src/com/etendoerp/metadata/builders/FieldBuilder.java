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

import static com.etendoerp.metadata.utils.Utils.getReferencedTab;

import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.ForeignKeyDomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.util.Check;
import org.openbravo.client.application.ApplicationConstants;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.data.ReferenceSelectors;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;
import com.etendoerp.metadata.utils.LegacyUtils;

/**
 * @author Futit Services S.L.
 */
public abstract class FieldBuilder extends Builder {
    protected final Field field;
    protected final FieldAccess fieldAccess;
    protected final JSONObject json;
    protected final Language language;

    public FieldBuilder(Field field, FieldAccess fieldAccess) {
        this.field = field;
        this.fieldAccess = fieldAccess;
        this.json = converter.toJsonObject(field, DataResolvingMode.FULL_TRANSLATABLE);
        this.language = OBContext.getOBContext().getLanguage();
    }

    public static boolean isProcessField(Field field) {
        Process processAction = field.getColumn().getProcess();
        org.openbravo.client.application.Process processDefinition = field.getColumn().getOBUIAPPProcess();

        return (processAction != null || processDefinition != null);
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

    public static ReferenceSelectors getReferenceSelectors(Reference ref) {
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

    public static JSONArray getListInfo(Reference refList, Language language) throws JSONException {
        JSONArray result = new JSONArray();

        for (org.openbravo.model.ad.domain.List list : refList.getADListList()) {
            JSONObject listJson = new JSONObject();

            listJson.put("id", list.getId());
            listJson.put("value", list.getSearchKey());
            listJson.put("label", list.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, list.getId()));

            result.put(listJson);
        }

        return result;
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

    public static DomainType getDomainType(String referenceId) {
        final org.openbravo.base.model.Reference reference = ModelProvider.getInstance().getReference(referenceId);
        Check.isNotNull(reference, "No reference found for referenceid " + referenceId);
        return reference.getDomainType();
    }

    public static String getPropertyOrDataSourceField(SelectorField selectorField) {
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

    protected static String getInputName(Column column) {
        return DataSourceUtils.getInpName(column);
    }

    protected static String getHqlName(Field field) {
        try {
            Column fieldColumn = field.getColumn();
            String dbTableName = fieldColumn.getTable().getDBTableName();
            String dbColumnName = fieldColumn.getDBColumnName();
            String[] names = DataSourceUtils.getHQLColumnName(true, dbTableName, dbColumnName);

            if (names.length > 0) {
                return names[0];
            }
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }

        return field.getName();
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        addAccessProperties(fieldAccess);
        addBasicProperties(field);
        addReferencedProperty(field);
        addReferencedTableInfo(field);
        addDisplayLogic(field);
        addReadOnlyLogic(field);
        addProcessInfo(field);
        addSelectorReferenceList(field);
        addComboSelectInfo(field);

        return json;
    }

    protected void addAccessProperties(FieldAccess access) throws JSONException {
        boolean checkOnSave = access != null ? access.isCheckonsave() : Constants.DEFAULT_CHECKON_SAVE;
        boolean editableField = access != null ? access.isEditableField() : Constants.DEFAULT_EDITABLE_FIELD;
        boolean fieldIsReadOnly = field.isReadOnly();
        boolean isColUpdatable = true; // Default to true, as we don't have a column to check against
        boolean readOnly = fieldIsReadOnly || (access != null && !access.isEditableField());

        json.put("checkOnSave", checkOnSave);
        json.put("isEditable", editableField);
        json.put("isReadOnly", readOnly);
        json.put("isUpdatable", isColUpdatable);
    }

    protected void addBasicProperties(Field field) throws JSONException {
        boolean isParentRecordProperty = isParentRecordProperty(field, field.getTab());
        String hqlName = getHqlName(field);

        json.put("hqlName", hqlName);
        json.put("isParentRecordProperty", isParentRecordProperty);
    }

    protected void addReferencedProperty(Field field) throws JSONException {
        return;
    }

    protected boolean isParentRecordProperty(Field field, Tab tab) {
        return false; // Default to false, as we don't have a column to check against
    }

    protected void addReferencedTableInfo(Field field) throws JSONException {
        return;
    }

    protected void addComboSelectInfo(Field field) throws JSONException {
        return;
    }

    protected void addSelectorReferenceList(Field field) throws JSONException {
        return;
    }

    protected void addProcessInfo(Field field) throws JSONException {
        return;
    }

    private void addDisplayLogic(Field field) throws JSONException {
        String displayLogic = field.getDisplayLogic();

        if (displayLogic != null && !displayLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(displayLogic, field.getTab(), field);
            json.put("displayLogicExpression", parser.getJSExpression());
        }
    }

    protected void addReadOnlyLogic(Field field) throws JSONException {
        return;
    }

    protected boolean isRefListField(Field field) {
        return false; // Default to false, as we don't have a column to check against
    }

    protected boolean isSelectorField(Field field) {
        return false; // Default to false, as we don't have a column to check against
    }
}
