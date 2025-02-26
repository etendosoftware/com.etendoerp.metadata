package com.etendoerp.metadata.builders;

import com.etendoerp.etendorx.utils.DataSourceUtils;
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
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.domain.ReferencedTree;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.datasource.DataSource;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FieldBuilder extends Builder {
    private static final boolean DEFAULT_CHECKON_SAVE = true;
    private static final boolean DEFAULT_EDITABLE_FIELD = true;
    private static final String LIST_REFERENCE_ID = "17";
    private static final String CUSTOM_QUERY_DS = "F8DD408F2F3A414188668836F84C21AF";
    private static final String TABLE_DATASOURCE = "ComboTableDatasourceService";
    private static final String TREE_DATASOURCE = "90034CAE96E847D78FBEF6D38CB1930D";
    private static final String DATASOURCE_PROPERTY = "datasourceName";
    private static final String SELECTOR_DEFINITION_PROPERTY = "_selectorDefinitionId";
    private static final String FIELD_ID_PROPERTY = "fieldId";
    private static final String DISPLAY_FIELD_PROPERTY = "displayField";
    private static final String VALUE_FIELD_PROPERTY = "valueField";
    private static final String PROCESS_REFERENCE_VALUE = "28";
    private static final String SELECTOR_REFERENCE_ID = "95E2A8B50A254B2AAE6774B8C2F28120";
    private static final String SEARCH_REFERENCE_ID = "30";
    private static final String TABLE_DIR_REFERENCE_ID = "19";
    private static final String TABLE_REFERENCE_ID = "18";
    private static final String TREE_REFERENCE_ID = "8C57A4A2E05F4261A1FADF47C30398AD";
    private static final List<String> SELECTOR_REFERENCES = Arrays.asList(TABLE_REFERENCE_ID,
                                                                          TABLE_DIR_REFERENCE_ID,
                                                                          SEARCH_REFERENCE_ID,
                                                                          SELECTOR_REFERENCE_ID,
                                                                          TREE_REFERENCE_ID);
    private final Field field;
    private final FieldAccess fieldAccess;
    private final JSONObject json;

    public FieldBuilder(Field field, FieldAccess fieldAccess) {
        super();
        this.field = field;
        this.fieldAccess = fieldAccess;
        this.json = converter.toJsonObject(field, DataResolvingMode.FULL_TRANSLATABLE);
    }

    public FieldBuilder(Field field) {
        this(field, null);
    }

    public static boolean isProcessField(Field field) {
        Column column = field.getColumn();

        return column != null && column.getReference() != null &&
               PROCESS_REFERENCE_VALUE.equals(column.getReference().getId()) && column.getOBUIAPPProcess() != null;
    }

    private static boolean isSelectorParameter(Parameter parameter) {
        return SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    private static boolean isListParameter(Parameter parameter) {
        return LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    public static JSONObject createProcessJSON(Process process) throws JSONException {
        DataToJsonConverter converter = new DataToJsonConverter();
        JSONObject processJSON = converter.toJsonObject(process, DataResolvingMode.FULL_TRANSLATABLE);
        JSONArray parameters = new JSONArray();

        for (Parameter param : process.getOBUIAPPParameterList()) {
            JSONObject paramJSON = converter.toJsonObject(param, DataResolvingMode.FULL_TRANSLATABLE);

            if (isSelectorParameter(param)) {
                paramJSON.put("selector", getSelectorInfo(param.getId(), param.getReferenceSearchKey()));
            }
            if (isListParameter(param)) {
                paramJSON.put("refList", getListInfo(param.getReferenceSearchKey()));
            }

            parameters.put(paramJSON);
        }

        processJSON.put("parameters", parameters);

        return processJSON;
    }

    private static JSONObject getSelectorInfo(String fieldId, Reference ref) throws JSONException {
        JSONObject selectorInfo = new JSONObject();

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

        if (selector != null) {
            String dataSourceId;
            if (selector.getObserdsDatasource() != null) {
                dataSourceId = selector.getObserdsDatasource().getId();
            } else if (selector.isCustomQuery()) {
                dataSourceId = CUSTOM_QUERY_DS;
            } else {
                dataSourceId = selector.getTable().getName();
            }

            selectorInfo.put(DATASOURCE_PROPERTY, dataSourceId);
            selectorInfo.put(SELECTOR_DEFINITION_PROPERTY, selector.getId());
            selectorInfo.put("filterClass", "org.openbravo.userinterface.selector.SelectorDataSourceFilter");

            if (selector.getDisplayfield() != null) {
                selectorInfo.put(JsonConstants.SORTBY_PARAMETER,
                                 selector.getDisplayfield().getDisplayColumnAlias() != null ?
                                 selector.getDisplayfield().getDisplayColumnAlias() :
                                 selector.getDisplayfield().getProperty());
            } else {
                selectorInfo.put(JsonConstants.SORTBY_PARAMETER, JsonConstants.IDENTIFIER);
            }
            selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
            selectorInfo.put(FIELD_ID_PROPERTY, fieldId);
            // For now we only support suggestion style search (only drop down)
            selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, selector.getSuggestiontextmatchstyle());

            setSelectorProperties(selector.getOBUISELSelectorFieldList(),
                                  selector.getDisplayfield(),
                                  selector.getValuefield(),
                                  selectorInfo);

            selectorInfo.put("extraSearchFields", getExtraSearchFields(selector));
            selectorInfo.put(DISPLAY_FIELD_PROPERTY, getDisplayField(selector));
            selectorInfo.put(VALUE_FIELD_PROPERTY, getValueField(selector));

        } else if (treeSelector != null) {
            selectorInfo.put(DATASOURCE_PROPERTY, TREE_DATASOURCE);
            selectorInfo.put(SELECTOR_DEFINITION_PROPERTY, treeSelector.getId());
            selectorInfo.put("treeReferenceId", treeSelector.getId());
            if (treeSelector.getDisplayfield() != null) {
                selectorInfo.put(JsonConstants.SORTBY_PARAMETER, treeSelector.getDisplayfield().getProperty());
                selectorInfo.put(DISPLAY_FIELD_PROPERTY, treeSelector.getDisplayfield().getProperty());
            }
            selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, JsonConstants.TEXTMATCH_SUBSTRING);
            selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
            selectorInfo.put(FIELD_ID_PROPERTY, fieldId);
            selectorInfo.put(VALUE_FIELD_PROPERTY, treeSelector.getValuefield().getProperty());
            selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, JsonConstants.ID);
            selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, JsonConstants.ID + ",");
        } else {
            selectorInfo.put(SELECTOR_DEFINITION_PROPERTY, (Object) null);
            selectorInfo.put(JsonConstants.SORTBY_PARAMETER, JsonConstants.IDENTIFIER);
            selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, JsonConstants.TEXTMATCH_SUBSTRING);
            selectorInfo.put(DATASOURCE_PROPERTY, TABLE_DATASOURCE);
            selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
            selectorInfo.put(FIELD_ID_PROPERTY, fieldId);
            selectorInfo.put(DISPLAY_FIELD_PROPERTY, JsonConstants.IDENTIFIER);
            selectorInfo.put(VALUE_FIELD_PROPERTY, JsonConstants.ID);
            selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, JsonConstants.ID);
            selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, JsonConstants.ID + ",");
        }
        return selectorInfo;
    }

    private static void setSelectorProperties(List<SelectorField> fields, SelectorField displayField,
                                              SelectorField valueField, JSONObject selectorInfo) throws JSONException {
        String valueFieldProperty =
                valueField != null ? getValueField(valueField.getObuiselSelector()) : JsonConstants.IDENTIFIER;
        String displayFieldProperty =
                displayField != null ? getDisplayField(displayField.getObuiselSelector()) : JsonConstants.IDENTIFIER;

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

            if ((!field.isOutfield() || (displayField == null || fieldName.equals(displayFieldProperty))) &&
                (valueField == null || fieldName.equals(valueFieldProperty))) {
                if (field.isOutfield()) {
                    extraProperties.append(",").append(fieldName);
                }
            }
        }

        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selectedProperties.toString());
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, extraProperties + "," + derivedProperties);
    }

    private static JSONArray getListInfo(Reference refList) throws JSONException {
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
        for (SelectorField selectorField : selector.getOBUISELSelectorFieldList()
                                                   .stream()
                                                   .filter(SelectorField::isActive)
                                                   .collect(Collectors.toList())) {
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
            return boolean.class == primitiveDomainType.getPrimitiveType() ||
                   Boolean.class == primitiveDomainType.getPrimitiveType();
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
        } else if (selectorField.getObuiselSelector().getTable() != null &&
                   selectorField.getObuiselSelector().isCustomQuery() && selectorField.getReference() != null) {
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

    @Override
    public JSONObject toJSON() throws JSONException {
        addAccessProperties(fieldAccess);
        addBasicProperties(field);
        addReferencedProperty(field);
        addReferencedTableInfo(field);
        addDisplayLogic(json, field);
        addReadOnlyLogic(json, field);
        addProcessInfo(json, field);
        addSelectorReferenceList(json, field);
        addComboSelectInfo(json, field);

        return json;
    }

    private void addAccessProperties(FieldAccess access) throws JSONException {
        boolean checkOnSave = access != null ? access.isCheckonsave() : DEFAULT_CHECKON_SAVE;
        boolean editableField = access != null ? access.isEditableField() : DEFAULT_EDITABLE_FIELD;
        boolean readOnly = access != null && !access.isEditableField() && access.getField().isReadOnly();

        json.put("checkonsave", checkOnSave);
        json.put("editableField", editableField);
        json.put("readOnly", readOnly);
    }

    private void addBasicProperties(Field field) throws JSONException {
        Column column = field.getColumn();
        boolean mandatory = column.isMandatory();
        boolean isParentRecordProperty = isParentRecordProperty(field, field.getTab());
        JSONObject columnJson = converter.toJsonObject(field.getColumn(), DataResolvingMode.FULL_TRANSLATABLE);
        String inputName = DataSourceUtils.getInpName(column);
        String hqlName = DataSourceUtils.getHQLColumnName(field)[0];
        String columnName = column.getDBColumnName();

        json.put("hqlName", hqlName);
        json.put("columnName", columnName);
        json.put("column", columnJson);
        json.put("isMandatory", mandatory);
        json.put("inputName", inputName);
        json.put("isParentRecordProperty", isParentRecordProperty);
    }

    private void addReferencedProperty(Field field) throws JSONException {
        Property referenced = KernelUtils.getProperty(field).getReferencedProperty();

        if (referenced != null) {
            String tableId = referenced.getEntity().getTableId();
            Table table = OBDal.getInstance().get(Table.class, tableId);
            Tab referencedTab = (Tab) OBDal.getInstance()
                                           .createCriteria(Tab.class)
                                           .add(Restrictions.eq(Tab.PROPERTY_TABLE, table))
                                           .setMaxResults(1)
                                           .uniqueResult();
            Window referencedWindow = referencedTab != null ? referencedTab.getWindow() : null;
            String tabId = referencedTab != null ? referencedTab.getId() : null;
            String windowId = referencedWindow != null ? referencedWindow.getId() : null;

            json.put("referencedEntity", referenced.getEntity().getName());
            json.put("referencedWindowId", windowId);
            json.put("referencedTabId", tabId);
        }
    }

    private boolean isParentRecordProperty(Field field, Tab tab) {
        Entity parentEntity = null;

        if (field.getColumn().isLinkToParentColumn()) {
            Tab parentTab = KernelUtils.getInstance().getParentTab(tab);
            // If the parent table is not based in a db table, don't try to retrieve the record
            // Because tables not based on db tables do not have BaseOBObjects
            // See issue https://issues.openbravo.com/view.php?id=29667
            if (parentTab != null &&
                ApplicationConstants.TABLEBASEDTABLE.equals(parentTab.getTable().getDataOriginType())) {
                parentEntity = ModelProvider.getInstance().getEntityByTableName(parentTab.getTable().getDBTableName());
            }

            Property property = KernelUtils.getProperty(field);
            Entity referencedEntity = property.getReferencedProperty().getEntity();
            return referencedEntity.equals(parentEntity);
        } else {
            return false;
        }
    }

    private void addReferencedTableInfo(Field field) throws JSONException {
        Property referenced = KernelUtils.getProperty(field).getReferencedProperty();

        if (referenced != null) {
            String tableId = referenced.getEntity().getTableId();
            Table table = OBDal.getInstance().get(Table.class, tableId);

            Tab referencedTab = (Tab) OBDal.getInstance()
                                           .createCriteria(Tab.class)
                                           .add(Restrictions.eq(Tab.PROPERTY_TABLE, table))
                                           .add(Restrictions.eq(Tab.PROPERTY_ACTIVE, true))
                                           .setMaxResults(1)
                                           .uniqueResult();

            if (referencedTab != null) {
                json.put("referencedEntity", referenced.getEntity().getName());
                json.put("referencedWindowId", referencedTab.getWindow().getId());
                json.put("referencedTabId", referencedTab.getId());
                json.put("referencedFields", getReferencedFields(referencedTab));
            }
        }
    }

    private void addComboSelectInfo(JSONObject jsonField, Field field) throws JSONException {
        if (isSelectorField(field)) {
            jsonField.put("selector", getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
        }
    }

    private void addSelectorReferenceList(JSONObject jsonField, Field field) throws JSONException {
        if (isRefListField(field)) {
            jsonField.put("refList", getListInfo(field.getColumn().getReferenceSearchKey()));
        }
    }

    private void addProcessInfo(JSONObject jsonField, Field field) throws JSONException {
        if (isProcessField(field)) {
            jsonField.put("process", getProcessInfo(field));
        }
    }

    private void addDisplayLogic(JSONObject jsonField, Field field) throws JSONException {
        String displayLogic = field.getDisplayLogic();

        if (displayLogic != null && !displayLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(displayLogic, field.getTab(), field);
            jsonField.put("displayLogicFields", getDisplayLogicFields(parser));
            jsonField.put("displayLogicExpression", parser.getJSExpression());
        }
    }

    private void addReadOnlyLogic(JSONObject jsonField, Field field) throws JSONException {
        String readOnlyLogic = field.getColumn().getReadOnlyLogic();

        if (readOnlyLogic != null && !readOnlyLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(readOnlyLogic, field.getTab(), field);
            jsonField.put("readOnlyLogicExpression", parser.getJSExpression());
        }
    }

    private JSONObject getProcessInfo(Field field) throws JSONException {
        Process process = field.getColumn().getOBUIAPPProcess();
        JSONObject processJson = createProcessJSON(process);

        processJson.put("fieldId", field.getId());
        processJson.put("columnId", field.getColumn().getId());
        processJson.put("displayLogic", field.getDisplayLogic());
        processJson.put("buttonText", field.getColumn().getName());
        processJson.put("fieldName", field.getName());
        processJson.put("reference", field.getColumn().getReference().getId());
        processJson.put("manualURL", Utility.getTabURL(field.getTab(), null, false));

        return processJson;
    }

    private List<JSONObject> getDisplayLogicFields(DynamicExpressionParser parser) {
        return parser.getFields().stream().map(this::buildDisplayLogicFieldJSON).collect(Collectors.toList());
    }

    private JSONArray getReferencedFields(Tab tab) throws JSONException {
        JSONArray fields = new JSONArray();
        for (Field field : tab.getADFieldList()) {
            if (field.isActive() && field.isDisplayed()) {
                JSONObject fieldInfo = new JSONObject();
                fieldInfo.put("name", field.getName());
                fieldInfo.put("columnName", field.getColumn().getDBColumnName());
                fields.put(fieldInfo);
            }
        }
        return fields;
    }

    private JSONObject buildDisplayLogicFieldJSON(Field field) {
        return converter.toJsonObject(field, DataResolvingMode.FULL_TRANSLATABLE);
    }

    private boolean isRefListField(Field field) {
        Column column = field.getColumn();

        return column != null && LIST_REFERENCE_ID.equals(column.getReference().getId());
    }

    private boolean isSelectorField(Field field) {
        Column column = field.getColumn();

        return column != null && SELECTOR_REFERENCES.contains(column.getReference().getId());
    }
}
