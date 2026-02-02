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
import org.apache.commons.lang3.StringUtils;
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
import org.openbravo.dal.core.OBContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.dal.service.OBCriteria;

/**
 * Concrete implementation of FieldBuilder for fields with database columns.
 * Extends the base functionality to handle column-specific properties such as
 * column metadata, referenced entities, process definitions, selectors, and
 * read-only logic.
 *
 * @author Futit Services S.L.
 */
public class FieldBuilderWithColumn extends FieldBuilder {
    private static final Map<String, Boolean> windowAccessCache = new ConcurrentHashMap<>();
    private static final String COLUMN_NAME = "columnName";
    private static final String COLUMN = "column";
    private static final String IS_MANDATORY = "isMandatory";
    private static final String INPUT_NAME = "inputName";
    private static final String IS_PARENT_RECORD_PROPERTY = "isParentRecordProperty";
    private static final String REFERENCED_ENTITY = "referencedEntity";
    private static final String REFERENCED_WINDOW_ID = "referencedWindowId";
    private static final String REFERENCED_TAB_ID = "referencedTabId";
    private static final String SELECTOR = "selector";
    private static final String REF_LIST = "refList";
    private static final String BUTTON_REF_LIST = "buttonRefList";
    private static final String PROCESS_DEFINITION = "processDefinition";
    private static final String PROCESS_ACTION = "processAction";
    private static final String READ_ONLY_LOGIC_EXPRESSION = "readOnlyLogicExpression";
    private static final String IS_REFERENCED_WINDOW_ACCESSIBLE = "isReferencedWindowAccessible";
    private static final String NULL_STRING = "null";

    /**
     * Constructs a FieldBuilderWithColumn for fields that have associated database
     * columns.
     *
     * @param field       The UI field entity with an associated database column
     * @param fieldAccess The field access permissions (can be null for default
     *                    permissions)
     */
    public FieldBuilderWithColumn(Field field, FieldAccess fieldAccess) {
        super(field, fieldAccess);
    }

    /**
     * Builds the complete JSON representation of a field with column-specific
     * properties.
     * Calls the parent method to add basic properties, then adds column-specific
     * metadata
     * including column information, referenced entities, processes, selectors, and
     * logic expressions.
     *
     * @return JSONObject containing complete field metadata with column-specific
     *         properties
     * @throws JSONException if there's an error building the JSON structure
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        try {
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
            addButtonReferenceValues(field);
            addLinkAccessibilityInfo();
        } catch (Exception e) {
            logger.warn("Error building JSON for field {}: {}", field.getId(), e.getMessage(), e);
        }

        return json;
    }

    /**
     * Determines if the database column associated with this field is updatable.
     * Overrides the base implementation to check the actual column's updatable
     * property.
     *
     * @return true if the column exists and is updatable, true as fallback if
     *         column is null
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

        json.put(COLUMN_NAME, columnName);
        json.put(COLUMN, columnJson);
        json.put(IS_MANDATORY, mandatory);
        json.put(INPUT_NAME, inputName);
        json.put(IS_PARENT_RECORD_PROPERTY, isParentRecordProperty);
    }

    /**
     * Adds referenced entity information to the field JSON for foreign key fields.
     * Determines the referenced entity, window, and tab for navigation purposes.
     * Only processes fields that have a referenced property (foreign key
     * relationship).
     * Any errors during retrieval are silently ignored to avoid breaking the JSON
     * structure.
     *
     * @param field The field that may reference another entity
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addReferencedProperty(Field field) throws JSONException {
        Property referenced = null;

        try {
            referenced = KernelUtils.getProperty(field).getReferencedProperty();
        } catch (Exception e) {
            // If any error occurs while getting the referenced property, skip adding
            // referenced property info
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

            json.put(REFERENCED_ENTITY, referenced.getEntity().getName());
            json.put(REFERENCED_WINDOW_ID, windowId);
            json.put(REFERENCED_TAB_ID, tabId);
        }
    }

    /**
     * Determines if a field represents a parent record property.
     * Checks if the field's column is a link to parent column and if the referenced
     * entity matches the parent tab's entity.
     *
     * @param field The field to check for parent record relationship
     * @param tab   The current tab context
     * @return true if the field represents a parent record property, false
     *         otherwise
     */
    private boolean isParentRecordProperty(Field field, Tab tab) {
        Entity parentEntity = null;

        if (field.getColumn().isLinkToParentColumn()) {
            Tab parentTab = KernelUtils.getInstance().getParentTab(tab);
            // If the parent table is not based in a db table, don't try to retrieve the
            // record
            // Because tables not based on db tables do not have BaseOBObjects
            // See issue https://issues.openbravo.com/view.php?id=29667
            if (parentTab != null && ApplicationConstants.TABLEBASEDTABLE.equals(
                    parentTab.getTable().getDataOriginType())) {
                parentEntity = ModelProvider.getInstance().getEntityByTableName(parentTab.getTable().getDBTableName());
            }

            try {
                Property property = KernelUtils.getProperty(field);
                Property referencedProperty = property.getReferencedProperty();
                if (referencedProperty == null) {
                    return false;
                }
                Entity referencedEntity = referencedProperty.getEntity();
                return referencedEntity.equals(parentEntity);
            } catch (Exception e) {
                // If any error occurs while getting the property, return false gracefully
                String errorMessage = String.format("Error checking parent record property for field %s: %s",
                        field.getId(), e.getMessage());
                logger.warn(errorMessage, e);
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Adds referenced table information using utility methods.
     * Alternative approach to addReferencedProperty that uses
     * Utils.getReferencedTab.
     * Provides referenced entity, window, and tab information for foreign key
     * fields.
     * Any errors during retrieval are silently ignored to avoid breaking the JSON
     * structure.
     *
     * @param field The field that may reference another table
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addReferencedTableInfo(Field field) throws JSONException {
        Property referenced = null;

        try {
            referenced = KernelUtils.getProperty(field).getReferencedProperty();
        } catch (Exception e) {
            // If any error occurs while getting the referenced property, skip adding
            // referenced table info
            String errorMessage = String.format("Error retrieving referenced property for field %s: %s",
                    field.getId(), e.getMessage());
            logger.warn(errorMessage, e);
            return;
        }

        if (referenced != null) {
            Tab referencedTab = getReferencedTab(referenced);

            if (referencedTab != null) {
                json.put(REFERENCED_ENTITY, referenced.getEntity().getName());
                json.put(REFERENCED_WINDOW_ID, referencedTab.getWindow().getId());
                json.put(REFERENCED_TAB_ID, referencedTab.getId());
            }
        }
    }

    /**
     * Adds selector information for fields that use selector-based references.
     * Configures custom selectors, tree selectors, or combo table selectors
     * based on the field's reference configuration.
     * * Handles exceptions gracefully to prevent breaking the entire field JSON
     * generation
     * if the selector configuration is invalid.
     *
     * @param field The field that may have selector functionality
     * @throws JSONException if there's an error updating the JSON structure
     *                       (outside the try-catch)
     */
    private void addComboSelectInfo(Field field) throws JSONException {
        if (isSelectorField(field)) {
            try {
                json.put(SELECTOR, getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
            } catch (Exception e) {
                logger.error("Error retrieving selector info for field: {} ({}). Skipping selector configuration.",
                        field.getId(), field.getName(), e);
            }
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
            json.put(REF_LIST, getListInfo(field.getColumn().getReferenceSearchKey(), language));
        }
    }

    /**
     * Adds button reference values for fields that are buttons with reference
     * lists.
     * Provides dropdown options for button fields configured with reference lists.
     * 
     * @param field The field that may be a button with reference list functionality
     * @throws JSONException if there's an error updating the JSON structure
     */
    private void addButtonReferenceValues(Field field) throws JSONException {
        if (isButtonField(field) && field.getColumn().getReferenceSearchKey() != null) {
            json.put(BUTTON_REF_LIST, addADListList(field.getColumn().getReferenceSearchKey()));
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
                json.put(PROCESS_DEFINITION, Utils.getFieldProcess(field));
            }

            if (processAction != null) {
                json.put(PROCESS_ACTION, ProcessActionBuilder.getFieldProcess(field, processAction));
            }
        }
    }

    /**
     * Adds read-only logic expression to the field JSON if configured on the
     * column.
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
            json.put(READ_ONLY_LOGIC_EXPRESSION, parser.getJSExpression());
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
     * Checks if the field's column reference is one of the supported selector
     * types.
     *
     * @param field The field to check for selector functionality
     * @return true if the field uses a selector reference, false otherwise
     */
    private boolean isSelectorField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.SELECTOR_REFERENCES.contains(column.getReference().getId());
    }

    /**
     * Determines if a field is a process field.
     * Checks if the field's column reference is of process type.
     *
     * @param field The field to check for process functionality
     * @return true if the field is a process field, false otherwise
     */
    private boolean isButtonField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.BUTTON_REFERENCE_ID.equals(column.getReference().getId());
    }

    /**
     * Injects the 'isReferencedWindowAccessible' property into the JSON object.
     * It checks if a 'referencedWindowId' is present and verifies if the current
     * user
     * has access to that window using a cached strategy.
     *
     * @throws JSONException if there is an error manipulating the JSON object
     */
    private void addLinkAccessibilityInfo() throws JSONException {
        if (json.has(REFERENCED_WINDOW_ID)) {
            String windowId = json.optString(REFERENCED_WINDOW_ID);

            if (StringUtils.isNotEmpty(windowId) && !StringUtils.equals(windowId, NULL_STRING)) {
                boolean isAccessible = isWindowAccessible(windowId);
                json.put(IS_REFERENCED_WINDOW_ACCESSIBLE, isAccessible);
            } else {
                json.put(IS_REFERENCED_WINDOW_ACCESSIBLE, false);
            }
        }
    }

    /**
     * Checks if the current user role has access to the specified window.
     * Uses a static ConcurrentHashMap to cache results and avoid repetitive DB
     * queries.
     *
     * @param windowId The ID of the window to check
     * @return true if the user has access, false otherwise
     */
    private boolean isWindowAccessible(String windowId) {
        try {
            OBContext context = OBContext.getOBContext();
            Role role = context.getRole();

            if (role == null) {
                return false;
            }

            // "RoleID_WindowID"
            String cacheKey = role.getId() + "_" + windowId;

            return windowAccessCache.computeIfAbsent(cacheKey, k -> checkAccessInDB(role, windowId));

        } catch (Exception e) {
            logger.warn("Error checking window access for window {}: {}", windowId, e.getMessage());
            return false;
        }
    }

    /**
     * Performs the actual database query to check if a role has access to a window.
     * This method is intended to be called only when the cache misses.
     *
     * @param role     The role to check
     * @param windowId The ID of the window
     * @return true if a record exists in AD_Window_Access, false otherwise
     */
    private boolean checkAccessInDB(Role role, String windowId) {
        try {
            OBDal dal = OBDal.getReadOnlyInstance();
            Window window = dal.get(Window.class, windowId);

            if (window == null) {
                return false;
            }

            OBCriteria<WindowAccess> criteria = dal.createCriteria(WindowAccess.class);
            criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
            criteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, window));
            criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));

            criteria.setMaxResults(1);

            return criteria.uniqueResult() != null;
        } catch (Exception e) {
            logger.error("DB Error checking access for Role {} and Window {}", role.getId(), windowId, e);
            return false;
        }
    }
}
