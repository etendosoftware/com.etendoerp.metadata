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
 * Concrete implementation of FieldBuilder for fields with columns
 * @author Futit Services S.L.
 */
public class FieldBuilderWithColumn extends FieldBuilder {

    public FieldBuilderWithColumn(Field field, FieldAccess fieldAccess) {
        super(field, fieldAccess);
    }

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

    @Override
    protected boolean getColumnUpdatable() {
        return field.getColumn() != null ? field.getColumn().isUpdatable() : true;
    }

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

    private void addReferencedProperty(Field field) throws JSONException {
        Property referenced = KernelUtils.getProperty(field).getReferencedProperty();

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

    private void addReferencedTableInfo(Field field) throws JSONException {
        Property referenced = KernelUtils.getProperty(field).getReferencedProperty();

        if (referenced != null) {
            Tab referencedTab = getReferencedTab(referenced);

            if (referencedTab != null) {
                json.put("referencedEntity", referenced.getEntity().getName());
                json.put("referencedWindowId", referencedTab.getWindow().getId());
                json.put("referencedTabId", referencedTab.getId());
            }
        }
    }

    private void addComboSelectInfo(Field field) throws JSONException {
        if (isSelectorField(field)) {
            json.put("selector", getSelectorInfo(field.getId(), field.getColumn().getReferenceSearchKey()));
        }
    }

    private void addSelectorReferenceList(Field field) throws JSONException {
        if (isRefListField(field)) {
            json.put("refList", getListInfo(field.getColumn().getReferenceSearchKey(), language));
        }
    }

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

    private void addReadOnlyLogic(Field field) throws JSONException {
        String readOnlyLogic = field.getColumn().getReadOnlyLogic();

        if (readOnlyLogic != null && !readOnlyLogic.isBlank()) {
            DynamicExpressionParser parser = new DynamicExpressionParser(readOnlyLogic, field.getTab(), field);
            json.put("readOnlyLogicExpression", parser.getJSExpression());
        }
    }

    private boolean isRefListField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.LIST_REFERENCE_ID.equals(column.getReference().getId());
    }

    private boolean isSelectorField(Field field) {
        Column column = field.getColumn();

        return column != null && Constants.SELECTOR_REFERENCES.contains(column.getReference().getId());
    }
}
