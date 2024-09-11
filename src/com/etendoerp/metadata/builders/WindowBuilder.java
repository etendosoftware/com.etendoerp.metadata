package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.smf.mobile.utils.webservices.WindowUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;
import org.openbravo.base.model.domaintype.ForeignKeyDomainType;
import org.openbravo.base.model.domaintype.PrimitiveDomainType;
import org.openbravo.base.util.Check;
import org.openbravo.client.application.ApplicationConstants;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.Sqlc;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.datamodel.Column;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class WindowBuilder {
    private static final DataToJsonConverter converter = new DataToJsonConverter();
    private static final DataToJsonConverter windowConverter = new DataToJsonConverter();
    private static final DataToJsonConverter tabConverter = new DataToJsonConverter();
    private final static String[] WINDOW_PROPERTIES = new String[]{Window.PROPERTY_ID, Window.PROPERTY_NAME, Window.PROPERTY_WINDOWTYPE, Window.PROPERTY_DESCRIPTION};
    private final static String[] TAB_PROPERTIES = new String[]{Tab.PROPERTY_ID, Tab.PROPERTY_NAME, Tab.PROPERTY_TABLEVEL, Tab.PROPERTY_TABLE};
    private static final List<String> ALWAYS_DISPLAYED_COLUMNS = Collections.singletonList("AD_Org_ID");
    private static final String LIST_REFERENCE_ID = "17";
    private static final String SELECTOR_REFERENCE_ID = "95E2A8B50A254B2AAE6774B8C2F28120";
    private static final String SEARCH_REFERENCE_ID = "30";
    private static final String TABLE_DIR_REFERENCE_ID = "19";
    private static final String TABLE_REFERENCE_ID = "18";
    private static final String TREE_REFERENCE_ID = "8C57A4A2E05F4261A1FADF47C30398AD";
    private static final List<String> SELECTOR_REFERENCES = Arrays.asList(TABLE_REFERENCE_ID, TABLE_DIR_REFERENCE_ID, SEARCH_REFERENCE_ID, SELECTOR_REFERENCE_ID, TREE_REFERENCE_ID);
    private static final String CUSTOM_QUERY_DS = "F8DD408F2F3A414188668836F84C21AF";
    private static final String TABLE_DATASOURCE = "ComboTableDatasourceService";
    private static final String TREE_DATASOURCE = "90034CAE96E847D78FBEF6D38CB1930D";
    private static final String DATASOURCE_PROPERTY = "datasourceName";
    private static final String SELECTOR_DEFINITION_PROPERTY = "_selectorDefinitionId";
    private static final String FIELD_ID_PROPERTY = "fieldId";
    private static final String DISPLAY_FIELD_PROPERTY = "displayField";
    private static final String VALUE_FIELD_PROPERTY = "valueField";
    private final String id;

    public WindowBuilder(String id) {
        this.id = id;
        windowConverter.setSelectedProperties(String.join(",", WINDOW_PROPERTIES));
        tabConverter.setSelectedProperties(String.join(",", TAB_PROPERTIES));
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
            throw new IllegalStateException("Selectorfield " + selectorField + " has a null datasource and a null property");
        }
        return result.replace(DalUtil.DOT, DalUtil.FIELDSEPARATOR);
    }

    public JSONObject toJSON() throws JSONException {
        Role role = OBContext.getOBContext().getRole();
        org.openbravo.model.ad.ui.Window adWindow = OBDal.getInstance().get(org.openbravo.model.ad.ui.Window.class, this.id);
        OBCriteria<WindowAccess> windowAccessCriteria = OBDal.getInstance().createCriteria(WindowAccess.class);
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));

        if (adWindow != null) {
            windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
            windowAccessCriteria.setMaxResults(1);
            WindowAccess windowAccess = (WindowAccess) windowAccessCriteria.uniqueResult();

            if (windowAccess != null) {
                JSONObject window = windowConverter.toJsonObject(windowAccess.getWindow(), DataResolvingMode.FULL_TRANSLATABLE);
                window.put("tabs", getTabsAndFields(windowAccess.getADTabAccessList(), windowAccess.getWindow()));

                return window;
            } else {
                throw new OBSecurityException();
            }
        } else {
            throw new NotFoundException();
        }
    }

    private JSONArray getTabsAndFields(List<TabAccess> tabAccesses, org.openbravo.model.ad.ui.Window window) throws JSONException {
        JSONArray tabs = new JSONArray();

        if (tabAccesses.isEmpty()) {
            for (Tab tab : window.getADTabList().stream().filter(Tab::isActive).toList()) {
                if (isTabAllowed(tab)) {
                    tabs.put(buildTabJSON(tab));
                }
            }
        } else {
            for (TabAccess tabAccess : tabAccesses.stream().filter(tabAccess -> tabAccess.isActive() && tabAccess.isAllowRead() && tabAccess.getTab().isActive() && tabAccess.getTab().isAllowRead()).toList()) {
                if (isTabAllowed(tabAccess.getTab())) {
                    tabs.put(buildTabJSON(tabAccess.getTab()));
                }
            }
        }

        return tabs;
    }

    private JSONObject buildTabJSON(Tab tab) throws JSONException {
        JSONObject jsonTab;

        // TODO: Use our own converter that returns only the fields we need
        jsonTab = tabConverter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE);
        jsonTab.put("editableField", true);
        jsonTab.put("fields", getFields(tab, null));
        jsonTab.put("entityName", getTabEntityName(tab));
        jsonTab.put("parentColumns", WindowUtils.getParentColumns(tab));
        jsonTab.put("identifiers", WindowUtils.getTabIdentifiers(tab));
        return jsonTab;
    }

    private boolean isTabAllowed(Tab tab) {
        return tab.getDisplayLogic() == null || tab.getDisplayLogic().trim().isEmpty();
    }

    private JSONObject getFields(Tab tab, List<FieldAccess> adFieldAccessList) throws JSONException {
        JSONObject fields = new JSONObject();

        if (adFieldAccessList == null || adFieldAccessList.isEmpty()) {
            // All tabs
            for (Field field : tab.getADFieldList().stream().filter(field -> field.isActive() && shouldDisplayField(field) && hasAccessToProcess(field, tab.getWindow().getId())).toList()) {

                String columnName = getEntityColumnName(field.getColumn());

                fields.put(columnName, getJSONField(field, null));
            }
        } else {
            // certain tabs
            for (FieldAccess fieldAccess : adFieldAccessList.stream().filter(fieldAccess -> fieldAccess.isActive() && fieldAccess.getField().isActive() && shouldDisplayField(fieldAccess.getField()) && hasAccessToProcess(fieldAccess.getField(), tab.getWindow().getId())).toList()) {

                Field field = fieldAccess.getField();

                String columnName = getEntityColumnName(fieldAccess.getField().getColumn());

                fields.put(columnName, getJSONField(field, fieldAccess));
            }
        }

        return fields;
    }

    private String getTabEntityName(Tab tab) {
        return tab.getTable().getName();
    }

    private String getEntityColumnName(Column column) {
        String tableName = column.getTable().getName();
        String columnName = column.getDBColumnName();

        return ModelProvider.getInstance().getEntity(tableName).getPropertyByColumnName(columnName).getName();
    }

    private JSONObject getJSONField(Field field, FieldAccess access) throws JSONException {
        JSONObject jsonField;

        // TODO: Use our own converter that returns only the fields we need
        jsonField = converter.toJsonObject(field, DataResolvingMode.FULL_TRANSLATABLE);
        jsonField.put("checkonsave", access != null ? access.isCheckonsave() : true);
        jsonField.put("editableField", access != null ? access.isEditableField() : true);
        jsonField.put("columnName", getEntityColumnName(field.getColumn()));
        jsonField.put("column", converter.toJsonObject(field.getColumn(), DataResolvingMode.FULL_TRANSLATABLE));
        jsonField.put("inpName", Sqlc.TransformaNombreColumna(field.getColumn().getDBColumnName()));
        jsonField.put("isParentRecordProperty", isParentRecordProperty(field, field.getTab()));

        if (access != null && !access.isEditableField() && field.isReadOnly()) {
            jsonField.put("readOnly", true);
        }
        jsonField.put("isMandatory", field.getColumn().isMandatory());

        if (isProcessField(field)) {
            // TODO: Use our own converter that returns only the fields we need
            Process obuiappProcess = field.getColumn().getOBUIAPPProcess();
            JSONObject process = converter.toJsonObject(obuiappProcess, DataResolvingMode.FULL_TRANSLATABLE);
            JSONArray parameters = new JSONArray();

            for (Parameter param : obuiappProcess.getOBUIAPPParameterList()) {
                JSONObject jsonParam = converter.toJsonObject(param, DataResolvingMode.FULL_TRANSLATABLE);

                if (isSelectorParameter(param)) {
                    jsonParam.put("selector", getSelectorInfo(param.getId(), param.getReferenceSearchKey()));
                }
                if (isListParameter(param)) {
                    Reference refList = param.getReferenceSearchKey();

                    JSONArray refListValues = getListInfo(refList);
                    jsonParam.put("refList", refListValues);
                }

                parameters.put(jsonParam);
            }
            process.put("parameters", parameters);

            jsonField.put("process", process);
        }

        if (isRefListField(field)) {
            Reference refList = field.getColumn().getReferenceSearchKey();

            JSONArray refListValues = getListInfo(refList);
            jsonField.put("refList", refListValues);
        }

        if (isSelectorField(field)) {
            jsonField.put("selector", getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
        }

        return jsonField;
    }

    private boolean isProcessField(Field field) {
        Column column = field.getColumn();

        // Add getProcess() when adding support for old processes

        return column != null && column.getOBUIAPPProcess() != null;
    }

    private boolean isRefListField(Field field) {
        Column column = field.getColumn();

        return column != null && LIST_REFERENCE_ID.equals(column.getReference().getId());
    }

    private boolean isSelectorField(Field field) {
        Column column = field.getColumn();

        return column != null && SELECTOR_REFERENCES.contains(column.getReference().getId());
    }

    private boolean isParentRecordProperty(Field field, Tab tab) {
        Entity parentEntity = null;

        if (field.getColumn().isLinkToParentColumn()) {
            Tab parentTab = KernelUtils.getInstance().getParentTab(tab);
            // If the parent table is not based in a db table, don't try to retrieve the record
            // Because tables not based on db tables do not have BaseOBObjects
            // See issue https://issues.openbravo.com/view.php?id=29667
            if (parentTab != null && ApplicationConstants.TABLEBASEDTABLE.equals(parentTab.getTable().getDataOriginType())) {
                parentEntity = ModelProvider.getInstance().getEntityByTableName(parentTab.getTable().getDBTableName());
            }

            Property property = KernelUtils.getProperty(field);
            Entity referencedEntity = property.getReferencedProperty().getEntity();
            return referencedEntity.equals(parentEntity);
        } else {
            return false;
        }
    }

    private boolean isSelectorParameter(Parameter parameter) {
        return SELECTOR_REFERENCES.contains(parameter.getReference().getId());
    }

    private boolean isListParameter(Parameter parameter) {
        return LIST_REFERENCE_ID.contains(parameter.getReference().getId());
    }

    private boolean hasAccessToProcess(Field field, String windowId) {
        Process process = field.getColumn() != null && field.getColumn().getOBUIAPPProcess() != null ? field.getColumn().getOBUIAPPProcess() : null;
        if (process != null) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("windowId", windowId);
            return BaseProcessActionHandler.hasAccess(process, params);
        }
        // is not a process
        return true;
    }

    private boolean shouldDisplayField(Field field) {
        boolean isScanProcess = field.getColumn() != null && field.getColumn().getOBUIAPPProcess() != null && field.getColumn().getOBUIAPPProcess().isSmfmuScan() != null && field.getColumn().getOBUIAPPProcess().isSmfmuScan();
        // Hides fields with logic until mobile app implements behavior

        return field.getColumn() != null && (field.isDisplayed() || isScanProcess || field.getColumn().isStoredInSession() || field.getColumn().isLinkToParentColumn() || ALWAYS_DISPLAYED_COLUMNS.contains(field.getColumn().getDBColumnName()));
    }

    private JSONObject getSelectorInfo(String fieldId, Reference ref) throws JSONException {
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
                selectorInfo.put(JsonConstants.SORTBY_PARAMETER, selector.getDisplayfield().getDisplayColumnAlias() != null ? selector.getDisplayfield().getDisplayColumnAlias() : selector.getDisplayfield().getProperty());
            } else {
                selectorInfo.put(JsonConstants.SORTBY_PARAMETER, JsonConstants.IDENTIFIER);
            }
            selectorInfo.put(JsonConstants.NOCOUNT_PARAMETER, true);
            selectorInfo.put(FIELD_ID_PROPERTY, fieldId);
            // For now we only support suggestion style search (only drop down)
            selectorInfo.put(JsonConstants.TEXTMATCH_PARAMETER, selector.getSuggestiontextmatchstyle());

            setSelectorProperties(selector.getOBUISELSelectorFieldList(), selector.getDisplayfield(), selector.getValuefield(), selectorInfo);

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

    private void setSelectorProperties(List<SelectorField> fields, SelectorField displayField, SelectorField valueField, JSONObject selectorInfo) throws JSONException {
        String valueFieldProperty = valueField != null ? getValueField(valueField.getObuiselSelector()) : JsonConstants.IDENTIFIER;
        String displayFieldProperty = displayField != null ? getDisplayField(displayField.getObuiselSelector()) : JsonConstants.IDENTIFIER;

        String selectedProperties = JsonConstants.ID;
        StringBuilder derivedProperties = new StringBuilder();
        StringBuilder extraProperties = new StringBuilder(valueFieldProperty);

        // get extra properties
        if (displayField != null && !JsonConstants.IDENTIFIER.equals(displayFieldProperty)) {
            extraProperties.append(",").append(displayFieldProperty);
            selectedProperties += "," + displayFieldProperty;
        }

        // get selected and derived properties
        for (SelectorField field : fields) {
            String fieldName = getPropertyOrDataSourceField(field);
            if (fieldName.equals(JsonConstants.ID) || fieldName.equals(JsonConstants.IDENTIFIER)) {
                continue;
            }
            if (fieldName.contains(JsonConstants.FIELD_SEPARATOR)) {
                if (derivedProperties.isEmpty()) {
                    derivedProperties = new StringBuilder(fieldName);
                } else {
                    derivedProperties.append(',').append(fieldName);
                }
            } else {
                // Include following line when supporting for selector pop ups: selectedProperties += "," + fieldName;
            }

            // get extra properties
            if (field.isOutfield() && (displayField != null && !fieldName.equals(displayFieldProperty)) || (valueField != null && !fieldName.equals(valueFieldProperty))) {
                continue;
            } else if (field.isOutfield()) {
                extraProperties.append(",").append(fieldName);
            }
        }

        selectorInfo.put(JsonConstants.SELECTEDPROPERTIES_PARAMETER, selectedProperties);
        selectorInfo.put(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER, extraProperties + "," + derivedProperties);
    }

    public String getExtraSearchFields(Selector selector) {
        final String displayField = getDisplayField(selector);
        final StringBuilder sb = new StringBuilder();
        for (SelectorField selectorField : selector.getOBUISELSelectorFieldList().stream().filter(SelectorField::isActive).toList()) {
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

                if (!sb.isEmpty()) {
                    sb.append(",");
                }
                sb.append(fieldName);
            }
        }
        return sb.toString();
    }

    public String getDisplayField(Selector selector) {
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

    public String getValueField(Selector selector) {
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

    private boolean isBoolean(SelectorField selectorField) {
        final DomainType domainType = getDomainType(selectorField);
        if (domainType instanceof PrimitiveDomainType primitiveDomainType) {
            return boolean.class == primitiveDomainType.getPrimitiveType() || Boolean.class == primitiveDomainType.getPrimitiveType();
        }
        return false;
    }
}
