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

import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.ForeignKeyDomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.util.Check;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.ProcessParameter;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.data.ReferenceSelectors;
import com.etendoerp.metadata.utils.Constants;

import javax.servlet.ServletException;

/**
 * Abstract base class for building field metadata in JSON format.
 * Provides common functionality for all field types and defines the contract
 * for field-specific implementations through template methods.
 *
 * @author Futit Services S.L.
 */
public abstract class FieldBuilder extends Builder {
    protected final Field field;
    protected final FieldAccess fieldAccess;
    protected final JSONObject json;
    protected final Language lang;

    /**
     * Constructs a FieldBuilder with the specified field and access permissions.
     * Initializes the JSON object with basic field data and sets up the language context.
     *
     * @param field The UI field entity containing field definition and properties
     * @param fieldAccess The field access permissions (can be null for default permissions)
     */
    protected FieldBuilder(Field field, FieldAccess fieldAccess) {
        this.field = field;
        this.fieldAccess = fieldAccess;
        this.json = converter.toJsonObject(field, DataResolvingMode.FULL_TRANSLATABLE);
        this.lang = OBContext.getOBContext().getLanguage();
    }

    /**
     * Determines if a field represents a process (button or action field).
     * Checks both legacy process actions and new process definitions.
     *
     * @param field The field to check for process functionality
     * @return true if the field has an associated process action or definition, false otherwise
     */
    public static boolean isProcessField(Field field) {
        Process processAction = field.getColumn().getProcess();
        org.openbravo.client.application.Process processDefinition = field.getColumn().getOBUIAPPProcess();

        return (processAction != null || processDefinition != null);
    }


    /**
     * Creates selector information JSON for a given field and reference.
     * Determines the appropriate selector type (custom selector, tree selector, or combo table)
     * and delegates to the specific selector info creation method.
     *
     * @param fieldId The unique identifier of the field
     * @param ref The reference definition that may contain selector configurations
     * @return JSONObject containing complete selector configuration for the field
     * @throws JSONException if there's an error creating the JSON structure
     */
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

    /**
     * Extracts selector and tree selector instances from a reference definition.
     * Analyzes the reference to find configured selectors and tree selectors.
     *
     * @param ref The reference definition to analyze (can be null)
     * @return ReferenceSelectors object containing found selector and tree selector instances
     */
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

    /**
     * Creates selector information for a basic combo table selector.
     * Used when no custom selector or tree selector is configured.
     * Sets up default table-based selection with standard parameters.
     *
     * @param fieldId The unique identifier of the field
     * @return JSONObject with combo table selector configuration
     * @throws JSONException if there's an error creating the JSON structure
     */
    private static JSONObject addComboTableSelectorInfo(String fieldId) throws JSONException {
        JSONObject selectorInfo = new JSONObject();

        ProcessParameter field = OBDal.getInstance().get(ProcessParameter.class, fieldId);
        if (field == null) {
            return new JSONObject();
        }

        JSONArray comboData = new JSONArray();
        try {
            ConnectionProvider connProvider = DalConnectionProvider.getReadOnlyConnectionProvider();
            var vars = RequestContext.get().getVariablesSecureApp();
            var comboTableData = new ComboTableData(vars, connProvider,
                field.getReference().getId(), field.getDBColumnName(), "",
                field.getValidation().getId(),
                Utility.getContext(connProvider, vars, "#AccessibleOrgTree", ""),
                Utility.getContext(connProvider, vars, "#User_Client", ""), 0);
            Utility.fillSQLParameters(connProvider, vars, null, comboTableData,
                field.getProcess().getId(),
                Utility.getContext(connProvider, vars, "#AD_Org_ID", field.getProcess().getId()));
            var select = comboTableData.select(false);
            for (FieldProvider fieldProvider : select) {
                String id = fieldProvider.getField("ID");
                String name = fieldProvider.getField("NAME");
                JSONObject entry = new JSONObject();
                entry.put("id", id);
                entry.put("name", name);
                comboData.put(entry);
            }
        } catch (Exception e) {
            throw new OBException(e);
        }

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
        selectorInfo.put(Constants.RESPONSE_VALUES, comboData);

        return selectorInfo;
    }

    /**
     * Adds list information for a reference's AD List entries.
     * @param ref The reference containing the AD List entries
     * @return JSONArray containing list entries with value and label
     * @throws JSONException if an error occurs while creating or populating the
     *  *         {@link JSONObject} or {@link JSONArray}
     */
    static JSONArray addADListList(Reference ref) throws JSONException {
        JSONArray selectorInfo = new JSONArray();
        for (org.openbravo.model.ad.domain.List list : ref.getADListList()) {
            JSONObject listElement = new JSONObject();
            listElement.put("id", list.getId());
            listElement.put("value", list.getSearchKey());
            listElement.put("label", list.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, OBContext.getOBContext().getLanguage(), list.getId()));
            selectorInfo.put(listElement);
        }
        return selectorInfo;
    }

    /**
     * Creates selector information for a tree-based selector.
     * Configures tree datasource with display and value fields from the tree definition.
     *
     * @param fieldId The unique identifier of the field
     * @param treeSelector The tree selector configuration
     * @return JSONObject with tree selector configuration
     * @throws JSONException if there's an error creating the JSON structure
     */
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

    /**
     * Creates comprehensive selector information for a custom selector.
     * Handles datasource determination, field properties, and search configuration.
     *
     * @param fieldId The unique identifier of the field
     * @param selector The custom selector configuration
     * @return JSONObject with complete custom selector configuration
     * @throws JSONException if there's an error creating the JSON structure
     */
    protected static JSONObject addSelectorInfo(String fieldId, Selector selector) throws JSONException {
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

    /**
     * Configures selector properties including selected, derived, and additional properties.
     * Analyzes selector fields to determine which properties should be included in queries
     * and which should be available for display and searching.
     *
     * @param fields List of selector field configurations
     * @param displayField The field used for display (can be null)
     * @param valueField The field used for values (can be null)
     * @param selectorInfo The JSON object to populate with property configurations
     * @throws JSONException if there's an error updating the JSON structure
     */
    private static void setSelectorProperties(List<SelectorField> fields, SelectorField displayField,
        SelectorField valueField, JSONObject selectorInfo) throws JSONException {
        String valueFieldProperty = valueField != null ? getValueField(
            valueField.getObuiselSelector()) : JsonConstants.IDENTIFIER;
        String displayFieldProperty = displayField != null ? getDisplayField(
            displayField.getObuiselSelector()) : JsonConstants.IDENTIFIER;

        StringBuilder selectedProperties = new StringBuilder(JsonConstants.ID);
        StringBuilder derivedProperties = new StringBuilder();
        StringBuilder extraProperties = new StringBuilder(valueFieldProperty);

        if (displayField != null && !JsonConstants.IDENTIFIER.equals(displayFieldProperty)) {
            appendWithComma(extraProperties, displayFieldProperty);
            appendWithComma(selectedProperties, displayFieldProperty);
        }

        for (SelectorField field : fields) {
            processSelectorField(field, displayField, valueField, displayFieldProperty, valueFieldProperty,
                selectedProperties, derivedProperties, extraProperties);
        }

        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selectedProperties.toString());
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, extraProperties + "," + derivedProperties);
    }

    /**
     * Processes an individual selector field and categorizes it into selected, derived, or extra properties.
     *
     * @param field The selector field to process.
     * @param displayField The configured display field for the selector.
     * @param valueField The configured value field for the selector.
     * @param displayFieldProperty The property name of the display field.
     * @param valueFieldProperty The property name of the value field.
     * @param selectedProperties StringBuilder for selected properties.
     * @param derivedProperties StringBuilder for derived properties.
     * @param extraProperties StringBuilder for extra properties.
     */
    private static void processSelectorField(SelectorField field, SelectorField displayField, SelectorField valueField,
        String displayFieldProperty, String valueFieldProperty, StringBuilder selectedProperties,
        StringBuilder derivedProperties, StringBuilder extraProperties) {
        String fieldName = getPropertyOrDataSourceField(field);

        if (JsonConstants.ID.equals(fieldName) || JsonConstants.IDENTIFIER.equals(fieldName)) {
            return;
        }

        if (fieldName.contains(JsonConstants.FIELD_SEPARATOR)) {
            appendWithComma(derivedProperties, fieldName);
        } else {
            appendWithComma(selectedProperties, fieldName);
        }

        if (isExtraProperty(field, displayField, valueField, fieldName, displayFieldProperty, valueFieldProperty)) {
            appendWithComma(extraProperties, fieldName);
        }
    }

    /**
     * Determines if a selector field should be considered an extra property.
     *
     * @param field The selector field to check.
     * @param displayField The configured display field.
     * @param valueField The configured value field.
     * @param fieldName The name of the field being checked.
     * @param displayFieldProperty The property name of the display field.
     * @param valueFieldProperty The property name of the value field.
     * @return true if the field is an extra property, false otherwise.
     */
    private static boolean isExtraProperty(SelectorField field, SelectorField displayField, SelectorField valueField,
        String fieldName, String displayFieldProperty, String valueFieldProperty) {
        return field.isOutfield() &&
            (displayField == null || fieldName.equals(displayFieldProperty)) &&
            (valueField == null || fieldName.equals(valueFieldProperty));
    }

    /**
     * Appends a value to a StringBuilder, adding a comma separator if the StringBuilder is not empty.
     *
     * @param sb The StringBuilder to append to.
     * @param value The value to append.
     */
    private static void appendWithComma(StringBuilder sb, String value) {
        if (sb.length() > 0) {
            sb.append(",");
        }
        sb.append(value);
    }

    /**
     * Generates list information for reference list fields (dropdown options).
     * Converts reference list entries into JSON format with localized labels.
     *
     * @param refList The reference containing list definitions
     * @param language The language for label localization
     * @return JSONArray containing list options with id, value, and localized label
     * @throws JSONException if there's an error creating the JSON structure
     */
    public static JSONArray getListInfo(Reference refList, Language language) throws JSONException {
        JSONArray result = new JSONArray();

        for (org.openbravo.model.ad.domain.List list : refList.getADListList()) {
            JSONObject listJson = new JSONObject();

            listJson.put("id", list.getId());
            listJson.put("value", list.getSearchKey());
            listJson.put("label", list.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, list.getId()));
            listJson.put("color", list.get(org.openbravo.model.ad.domain.List.PROPERTY_ETMETACOLOR, language, list.getId()));
            listJson.put("active", list.isActive());

            result.put(listJson);
        }

        return result;
    }

    /**
     * Determines which fields should be available for text searching in selector suggestions.
     * Filters out boolean fields and configures foreign key fields appropriately.
     *
     * @param selector The selector configuration to analyze
     * @return Comma-separated string of field names available for text search
     */
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

                if (sb.length() != 0) {
                    sb.append(",");
                }
                sb.append(fieldName);
            }
        }
        return sb.toString();
    }

    /**
     * Determines the display field for a selector.
     * Uses configured display field or falls back to intelligent defaults based on datasource type.
     *
     * @param selector The selector configuration
     * @return The property name to use for display purposes
     */
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

    /**
     * Determines the value field for a selector.
     * Uses configured value field or falls back to defaults, handling foreign key fields appropriately.
     *
     * @param selector The selector configuration
     * @return The property name to use for value storage
     */
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

    /**
     * Checks if a selector field represents a boolean type.
     * Boolean fields are excluded from search functionality as they don't work well with text search.
     *
     * @param selectorField The selector field to check
     * @return true if the field is of boolean type, false otherwise
     */
    private static boolean isBoolean(SelectorField selectorField) {
        final DomainType domainType = getDomainType(selectorField);
        if (domainType instanceof PrimitiveDomainType) {
            PrimitiveDomainType primitiveDomainType = (PrimitiveDomainType) domainType;
            return boolean.class == primitiveDomainType.getPrimitiveType() || Boolean.class == primitiveDomainType.getPrimitiveType();
        }
        return false;
    }

    /**
     * Retrieves the domain type for a selector field.
     * Handles different selector field configurations (table-based, custom query, datasource-based).
     *
     * @param selectorField The selector field to analyze
     * @return The domain type of the field, or null if cannot be determined
     */
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

    /**
     * Gets the domain type for a reference by its ID.
     *
     * @param referenceId The unique identifier of the reference
     * @return The domain type associated with the reference
     * @throws IllegalStateException if no reference is found for the given ID
     */
    public static DomainType getDomainType(String referenceId) {
        final org.openbravo.base.model.Reference reference = ModelProvider.getInstance().getReference(referenceId);
        Check.isNotNull(reference, "No reference found for referenceid " + referenceId);
        return reference.getDomainType();
    }

    /**
     * Extracts the property name or datasource field name from a selector field.
     * Handles different types of selector field configurations and normalizes path separators.
     *
     * @param selectorField The selector field to extract the name from
     * @return The property or field name with normalized path separators
     * @throws IllegalStateException if the selector field has no valid property or datasource field
     */
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

    /**
     * Generates the input name for a database column.
     * Used for form input field naming in the UI.
     *
     * @param column The database column
     * @return The standardized input name for the column
     */
    protected static String getInputName(Column column) {
        return DataSourceUtils.getInpName(column);
    }

    /**
     * Generates the HQL (Hibernate Query Language) property name for a field.
     * Attempts to determine the correct HQL name from database table and column information,
     * falling back to the field name if unable to determine.
     *
     * @param field The field to generate HQL name for
     * @return The HQL property name, or field name as fallback
     */
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

    /**
     * Builds the complete JSON representation of the field.
     * Template method that calls specific property addition methods in sequence.
     * Subclasses can override to add additional properties after calling super.toJSON().
     *
     * @return JSONObject containing the complete field metadata
     * @throws JSONException if there's an error building the JSON structure
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        addAccessProperties(fieldAccess);
        addHqlName(field);
        addDisplayLogic(field);

        return json;
    }

    /**
     * Adds access control properties to the field JSON.
     * Determines field editability, read-only status, and update permissions based on
     * field access configuration and column properties.
     *
     * @param access The field access permissions (can be null for defaults)
     * @throws JSONException if there's an error updating the JSON structure
     */
    protected void addAccessProperties(FieldAccess access) throws JSONException {
        boolean checkOnSave = access != null ? access.isCheckonsave() : Constants.DEFAULT_CHECKON_SAVE;
        boolean editableField = access != null ? access.isEditableField() : Constants.DEFAULT_EDITABLE_FIELD;
        boolean fieldIsReadOnly = field.isReadOnly();
        boolean isColUpdatable = getColumnUpdatable();
        boolean readOnly = fieldIsReadOnly || (access != null && !access.isEditableField());

        json.put("checkOnSave", checkOnSave);
        json.put("isEditable", editableField);
        json.put("isReadOnly", readOnly);
        json.put("isUpdatable", isColUpdatable);
    }

    /**
     * Determines if the associated column is updatable.
     * Template method that allows subclasses to provide column-specific logic.
     * Base implementation returns true for fields without columns.
     *
     * @return true if the column is updatable, false otherwise
     */
    protected boolean getColumnUpdatable() {
        return true; // Default implementation for fields without columns
    }

    /**
     * Adds the HQL name property to the field JSON.
     * The HQL name is used for database queries and data binding.
     *
     * @param field The field to extract HQL name from
     * @throws JSONException if there's an error updating the JSON structure
     */
    protected void addHqlName(Field field) throws JSONException {
        String hqlName = getHqlName(field);
        json.put("hqlName", hqlName);
    }

    /**
     * Adds display logic expression to the field JSON if configured.
     * Display logic controls field visibility based on dynamic conditions.
     * Converts Etendo display logic syntax to JavaScript expressions.
     *
     * @param field The field that may have display logic configured
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addDisplayLogic(Field field) throws JSONException {
        String displayLogic = field.getDisplayLogic();

        if (displayLogic != null && !displayLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(displayLogic, field.getTab(), field);
            json.put("displayLogicExpression", parser.getJSExpression());
        }
    }
}
