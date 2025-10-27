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

import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.LegacyUtils;
import com.etendoerp.metadata.utils.Utils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.ApplicationConstants;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;

import static com.etendoerp.metadata.utils.Utils.getReferencedTab;

/**
 * Concrete implementation of FieldBuilder for fields with database columns.
 * Extends the base functionality to handle column-specific properties such as
 * column metadata, referenced entities, process definitions, selectors, and read-only logic.
 *
 * @author Futit Services S.L.
 */
public class FieldBuilderWithColumn extends FieldBuilder {

    /**
     * Constructs a FieldBuilderWithColumn for fields that have associated database columns.
     *
     * @param field The UI field entity with an associated database column
     * @param fieldAccess The field access permissions (can be null for default permissions)
     */
    public FieldBuilderWithColumn(Field field, FieldAccess fieldAccess) {
        super(field, fieldAccess);
    }

    /**
     * Builds the complete JSON representation of a field with column-specific properties.
     * Calls the parent method to add basic properties, then adds column-specific metadata
     * including column information, referenced entities, processes, selectors, and logic expressions.
     *
     * @return JSONObject containing complete field metadata with column-specific properties
     * @throws JSONException if there's an error building the JSON structure
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        // Call parent method to add basic properties
        super.toJSON();

        // Add column-specific properties
        addColumnSpecificProperties(field);
        addReferencedProperty(field);
        addReferencedTableInfo(field);
        addReadOnlyLogic(field);
        addProcessInfo(field);
        addSelectorReferenceList(field);
        addComboSelectInfo(field);

        return json;
    }

    /**
     * Determines if the database column associated with this field is updatable.
     * Overrides the base implementation to check the actual column's updatable property.
     *
     * @return true if the column exists and is updatable, true as fallback if column is null
     */
    @Override
    protected boolean getColumnUpdatable() {
        return field.getColumn() != null ? field.getColumn().isUpdatable() : true;
    }

    /**
     * Adds column-specific properties to the field JSON.
     * Includes column metadata, mandatory status, input name, column name,
     * and parent record relationship information.
     *
     * @param field The field with associated column to extract properties from
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addColumnSpecificProperties(Field field) throws JSONException {
        Column column = field.getColumn();
        boolean mandatory = column.isMandatory();
        boolean isParentRecordProperty = isParentRecordProperty(field, field.getTab());
        JSONObject columnJson = converter.toJsonObject(field.getColumn(), DataResolvingMode.FULL_TRANSLATABLE);
        String inputName = getInputName(column);
        String columnName = column.getDBColumnName();

        json.put("columnName", columnName);
        json.put("column", columnJson);
        json.put("isMandatory", mandatory);
        json.put("inputName", inputName);
        json.put("isParentRecordProperty", isParentRecordProperty);
    }

    /**
     * Adds referenced entity information to the field JSON for foreign key fields.
     * Determines the referenced entity, window, and tab for navigation purposes.
     * Only processes fields that have a referenced property (foreign key relationship).
     * Any errors during retrieval are silently ignored to avoid breaking the JSON structure.
     *
     * @param field The field that may reference another entity
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addReferencedProperty(Field field) throws JSONException {
        Property referenced = null;

        try {
            referenced = KernelUtils.getProperty(field).getReferencedProperty();
        } catch (Exception e) {
            // If any error occurs while getting the referenced property, skip adding referenced property info
            String errorMessage = String.format("Error retrieving referenced property for field %s: %s",
                    field.getId(), e.getMessage());
            logger.warn(errorMessage, e);
            return;
        }

        if (referenced != null) {
            String tableId = referenced.getEntity().getTableId();
            Table table = OBDal.getInstance().get(Table.class, tableId);
            Tab referencedTab = (Tab) OBDal.getInstance().createCriteria(Tab.class).add(
                    Restrictions.eq(Tab.PROPERTY_TABLE, table)).setMaxResults(1).uniqueResult();
            Window referencedWindow = referencedTab != null ? referencedTab.getWindow() : null;
            String tabId = referencedTab != null ? referencedTab.getId() : null;
            String windowId = referencedWindow != null ? referencedWindow.getId() : null;

            json.put("referencedEntity", referenced.getEntity().getName());
            json.put("referencedWindowId", windowId);
            json.put("referencedTabId", tabId);
        }
    }

    /**
     * Determines if a field represents a parent record property.
     * Checks if the field's column is a link to parent column and if the referenced
     * entity matches the parent tab's entity.
     *
     * @param field The field to check for parent record relationship
     * @param tab The current tab context
     * @return true if the field represents a parent record property, false otherwise
     */
    private boolean isParentRecordProperty(Field field, Tab tab) {
        Entity parentEntity = null;

        if (field.getColumn().isLinkToParentColumn()) {
            Tab parentTab = KernelUtils.getInstance().getParentTab(tab);
            // If the parent table is not based in a db table, don't try to retrieve the record
            // Because tables not based on db tables do not have BaseOBObjects
            // See issue https://issues.openbravo.com/view.php?id=29667
            if (parentTab != null && ApplicationConstants.TABLEBASEDTABLE.equals(
                    parentTab.getTable().getDataOriginType())) {
                parentEntity = ModelProvider.getInstance().getEntityByTableName(parentTab.getTable().getDBTableName());
            }

            Property property = KernelUtils.getProperty(field);
            Entity referencedEntity = property.getReferencedProperty().getEntity();
            return referencedEntity.equals(parentEntity);
        } else {
            return false;
        }
    }

    /**
     * Adds referenced table information using utility methods.
     * Alternative approach to addReferencedProperty that uses Utils.getReferencedTab.
     * Provides referenced entity, window, and tab information for foreign key fields.
     * Any errors during retrieval are silently ignored to avoid breaking the JSON structure.
     *
     * @param field The field that may reference another table
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addReferencedTableInfo(Field field) throws JSONException {
        Property referenced = null;

        try {
            referenced = KernelUtils.getProperty(field).getReferencedProperty();
        } catch (Exception e) {
            // If any error occurs while getting the referenced property, skip adding referenced table info
            String errorMessage = String.format("Error retrieving referenced property for field %s: %s",
                    field.getId(), e.getMessage());
            logger.warn(errorMessage, e);
            return;
        }

        if (referenced != null) {
            Tab referencedTab = getReferencedTab(referenced);

            if (referencedTab != null) {
                json.put("referencedEntity", referenced.getEntity().getName());
                json.put("referencedWindowId", referencedTab.getWindow().getId());
                json.put("referencedTabId", referencedTab.getId());
            }
        }
    }

    /**
     * Adds selector information for fields that use selector-based references.
     * Configures custom selectors, tree selectors, or combo table selectors
     * based on the field's reference configuration.
     *
     * @param field The field that may have selector functionality
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addComboSelectInfo(Field field) throws JSONException {
        if (isSelectorField(field)) {
            json.put("selector", getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
        }
    }

    /**
     * Adds reference list information for fields that use list-based references.
     * Provides dropdown options for fields with predefined value lists.
     *
     * @param field The field that may have reference list functionality
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addSelectorReferenceList(Field field) throws JSONException {
        if (isRefListField(field)) {
            json.put("refList", getListInfo(field.getColumn().getReferenceSearchKey(), language));
        }
    }

    /**
     * Adds process information for fields that trigger processes or actions.
     * Handles both legacy processes and new process definitions.
     * Includes process action buttons and process definition metadata.
     *
     * @param field The field that may have associated process functionality
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addProcessInfo(Field field) throws JSONException {
        String processId = field.getId();
        boolean isLegacyProcess = LegacyUtils.isLegacyProcess(processId);
        if (isProcessField(field) || isLegacyProcess) {
            Process processAction = null;
            if (isLegacyProcess) {
                // Create a new Process instance to simulate a real process
                processAction = LegacyUtils.getLegacyProcess(processId);
            } else {
                processAction = field.getColumn().getProcess();
            }
            org.openbravo.client.application.Process processDefinition = field.getColumn().getOBUIAPPProcess();

            if (processDefinition != null) {
                json.put("processDefinition", Utils.getFieldProcess(field));
            }

            if (processAction != null) {
                json.put("processAction", ProcessActionBuilder.getFieldProcess(field, processAction));
            }
        }
    }

    /**
     * Adds read-only logic expression to the field JSON if configured on the column.
     * Read-only logic controls field editability based on dynamic conditions.
     * Converts Etendo read-only logic syntax to JavaScript expressions.
     *
     * @param field The field whose column may have read-only logic configured
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addReadOnlyLogic(Field field) throws JSONException {
        String readOnlyLogic = field.getColumn().getReadOnlyLogic();

        if (readOnlyLogic != null && !readOnlyLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(readOnlyLogic, field.getTab(), field);
            json.put("readOnlyLogicExpression", parser.getJSExpression());
        }
    }

    /**
     * Determines if a field uses reference list functionality.
     * Checks if the field's column reference is of list type.
     *
     * @param field The field to check for reference list functionality
     * @return true if the field uses a list reference, false otherwise
     */
    private boolean isRefListField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.LIST_REFERENCE_ID.equals(column.getReference().getId());
    }

    /**
     * Determines if a field uses selector functionality.
     * Checks if the field's column reference is one of the supported selector types.
     *
     * @param field The field to check for selector functionality
     * @return true if the field uses a selector reference, false otherwise
     */
    private boolean isSelectorField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.SELECTOR_REFERENCES.contains(column.getReference().getId());
    }
}
