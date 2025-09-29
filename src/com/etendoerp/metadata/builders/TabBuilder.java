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
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataResolvingMode;

import com.etendoerp.metadata.data.TabProcessor;
import com.etendoerp.metadata.exceptions.InternalServerException;

public class TabBuilder extends Builder {
  private static final String CREATION_DATE = "creationDate";
  private static final String CREATED_BY = "createdBy";
  private static final String UPDATED = "updated";
  private static final String UPDATED_BY = "updatedBy";
  private static final String DB_CREATED = "Created";
  private static final String DB_CREATED_BY = "CreatedBy";
  private static final String DB_UPDATED = "Updated";
  private static final String DB_UPDATED_BY = "UpdatedBy";
  private static final String[] AUDIT_FIELDS = {
          CREATION_DATE, CREATED_BY, UPDATED, UPDATED_BY
  };
  private static final Map<String, String> AUDIT_DB_COLUMNS = Map.of(
          CREATION_DATE, DB_CREATED,
          CREATED_BY, DB_CREATED_BY,
          UPDATED, DB_UPDATED,
          UPDATED_BY, DB_UPDATED_BY
  );

  private final Tab tab;
  private final TabAccess tabAccess;

  public TabBuilder(Tab tab, TabAccess tabAccess) {
    this.tab = tab;
    this.tabAccess = tabAccess;
  }

  public JSONObject toJSON() {
    try {
      JSONObject json = converter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE);

      json.put("filter", tab.getFilterClause());
      json.put("displayLogic", tab.getDisplayLogic());
      json.put("entityName", tab.getTable().getName());
      json.put("parentColumns", getParentColumns());

      JSONObject fields = getFields();
      enrichWithAuditFields(fields);
      json.put("fields", fields);

      Tab parentTab = getParentTab();

      if (parentTab != null) {
        json.put("parentTabId", parentTab.getId());
      }

      return json;
    } catch (JSONException e) {
      logger.warn(e.getMessage(), e);
      throw new InternalServerException();
    }
  }

  private Tab getParentTab() {
    return KernelUtils.getInstance().getParentTab(tab);
  }

  private JSONArray getParentColumns() {
    JSONArray jsonColumns = new JSONArray();

    if (tab.getTabLevel() == 0) return jsonColumns;

    for (Column column : tab.getTable().getADColumnList()) {
      if (column.isLinkToParentColumn()) {
        jsonColumns.put(TabProcessor.getEntityColumnName(column));
      }
    }

    return jsonColumns;
  }

  private JSONObject getFields() throws JSONException {
    List<FieldAccess> adFieldAccessList = tabAccess != null ? tabAccess.getADFieldAccessList() : null;

    if (adFieldAccessList == null || adFieldAccessList.isEmpty()) {
      return TabProcessor.getTabFields(tab);
    }
    return TabProcessor.getTabFields(tabAccess);
  }

  /**
   * Enriches the fields object with standard audit fields if they are not already defined.
   * Only creationDate and updated are visible in grid by default.
   * Skips audit fields if the corresponding database columns don't exist in the table.
   *
   * @param fieldsJson the JSON object containing the tab's fields
   * @throws JSONException if there is an error manipulating the JSON structure
   */
  private void enrichWithAuditFields(JSONObject fieldsJson) throws JSONException {
    Table table = tab.getTable();
    if (table == null) {
      return;
    }

    int baseGridPosition = 9000;
    int order = 0;

    for (String auditField : AUDIT_FIELDS) {
      if (!fieldsJson.has(auditField)) {
        String dbColumnName = AUDIT_DB_COLUMNS.get(auditField);
        Column column = findColumnByDBName(table, dbColumnName);

        if (column != null) {
          boolean showInGrid = shouldShowInGrid(auditField);
          JSONObject syntheticField = createAuditField(column, auditField, baseGridPosition + order,
                  showInGrid);
          fieldsJson.put(auditField, syntheticField);
          order++;
        } else {
            logger.debug("Audit column '{}' not found in table '{}'- skipping audit field '{}'", dbColumnName, table.getName(), auditField);
        }
      }
    }
  }

  /**
   * Searches for a column in the table by its database column name.
   *
   * @param table the table to search in
   * @param dbColumnName the database column name to search for
   * @return the matching Column object, or null if not found
   */
  private Column findColumnByDBName(Table table, String dbColumnName) {
    return table.getADColumnList().stream()
            .filter(col -> StringUtils.equals(col.getDBColumnName(), dbColumnName))
            .findFirst()
            .orElse(null);
  }

  /**
   * Determines if an audit field should be visible in the grid by default.
   * Only creationDate and updated are shown by default.
   *
   * @param fieldName the name of the audit field
   * @return true if the field should be visible in grid, false otherwise
   */
  private boolean shouldShowInGrid(String fieldName) {
    return StringUtils.equals(fieldName, CREATION_DATE) || StringUtils.equals(fieldName, UPDATED);
  }

  /**
   * Creates a JSON object representing a synthetic audit field with all required metadata.
   *
   * @param column the database column object (must not be null)
   * @param hqlName the HQL property name for the field
   * @param gridPosition the position in the grid (used for ordering)
   * @param showInGrid whether the field should be visible in the grid view
   * @return a complete JSON object representing the audit field
   * @throws JSONException if there is an error creating the JSON structure
   */
  private JSONObject createAuditField(Column column, String hqlName, int gridPosition, boolean
          showInGrid)
          throws JSONException {
    if (column == null) {
      throw new IllegalArgumentException("Column cannot be null when creating audit field: " + hqlName);
    }

    JSONObject field = new JSONObject();
    field.put("id", "audit_" + column.getId());
    field.put("name", column.getName());
    field.put("description", column.getDescription());
    field.put("helpComment", column.getHelpComment());
    field.put("hqlName", hqlName);
    field.put("columnName", column.getDBColumnName());
    field.put("displayed", false);
    field.put("isFirstFocusedField", false);
    field.put("sequenceNumber", JSONObject.NULL);
    field.put("showInGridView", showInGrid);
    field.put("gridPosition", gridPosition);
    field.put("isReadOnly", true);
    field.put("isEditable", false);
    field.put("isUpdatable", false);
    field.put("readOnly", true);
    field.put("checkOnSave", false);
    field.put("isMandatory", column.isMandatory());
    field.put("isParentRecordProperty", false);
    JSONObject columnJson = converter.toJsonObject(column, DataResolvingMode.FULL_TRANSLATABLE);
    field.put("column", columnJson);
    field.put("column$_identifier", column.getIdentifier());
    if (isUserField(hqlName)) {
      JSONObject selector = new JSONObject();
      selector.put("displayField", "_identifier");
      selector.put("valueField", "id");
      field.put("selector", selector);
      field.put("referencedEntity", "ADUser");
    }
    field.put("centralMaintenance", true);
    field.put("ignoreInWad", false);
    field.put("fieldGroup", JSONObject.NULL);
    field.put("displayLogic", JSONObject.NULL);
    field.put("displayOnSameLine", false);
    field.put("displayFieldOnly", false);
    field.put("displayEncription", false);
    field.put("startinoddcolumn", false);
    field.put("startnewline", false);
    field.put("shownInStatusBar", false);
    field.put("tab", tab.getId());
    field.put("tab$_identifier", tab.getIdentifier());

    return field;
  }

  /**
   * Determines if a field is a user reference type based on its name.
   *
   * @param hqlName the HQL property name
   * @return true if the field is a user reference, false otherwise
   */
  private boolean isUserField(String hqlName) {
    return StringUtils.equals(hqlName, CREATED_BY) || StringUtils.equals(hqlName, UPDATED_BY);
  }
}