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

package com.etendoerp.metadata.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.client.application.ApplicationUtils;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.TableTree;
import org.openbravo.service.json.DataResolvingMode;

import com.etendoerp.metadata.data.TabProcessor;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.Constants;

/**
 * Builds a JSON representation of a tab including its fields, parent columns, and access permissions.
 */
public class TabBuilder extends Builder {
  private static final String[] AUDIT_FIELDS = {
          Constants.CREATION_DATE, Constants.CREATED_BY, Constants.UPDATED, Constants.UPDATED_BY
  };
  private static final Map<String, String> AUDIT_DB_COLUMNS = Map.of(
      Constants.CREATION_DATE, Constants.DB_CREATED,
      Constants.CREATED_BY, Constants.DB_CREATED_BY,
      Constants.UPDATED, Constants.DB_UPDATED,
      Constants.UPDATED_BY, Constants.DB_UPDATED_BY);

  /**
   * Initial grid visibility of the audit fields this builder injects. Etendo Classic never shows
   * them when a tab is opened: {@code OBViewFieldHandler.OBViewFieldAudit.isShowInitiallyInGrid()}
   * unconditionally returns false, which the view template renders as {@code showIf: 'false'}.
   * The fields still travel in the payload (flagged with {@code isAuditField}), so the frontend
   * column-visibility menu can enable them on demand.
   * <p>
   * This only affects the synthetic fields: a tab that defines its own AD_Field for Created or
   * Updated never reaches this code and keeps its dictionary configuration.
   */
  private static final boolean AUDIT_FIELD_SHOWN_IN_GRID_BY_DEFAULT = false;

  /** HQL property name every entity's primary key is universally mapped to. */
  private static final String ID_HQL_NAME = "id";

  /** Key under which the authoritative link-to-parent property is published. */
  private static final String PARENT_PROPERTY = "parentProperty";

  private final Tab tab;
  private final TabAccess tabAccess;
  private final boolean isWindowReadOnly;
  private final List<FieldAccess> preloadedFieldAccessList;

  /** Memoized result of {@link #resolveParentProperty()}; {@code null} until first resolved. */
  private String parentProperty;

  /**
   * Constructs a TabBuilder for the given tab.
   *
   * @param tab              the tab entity to build JSON for
   * @param tabAccess        the role-specific tab access configuration, or {@code null} if the tab
   *                         has no explicit access record (e.g. when called from TabService or when
   *                         the window has no TabAccess entries for the current role)
   * @param isWindowReadOnly {@code true} if the parent window was resolved as read-only (either
   *                         because {@code WindowAccess.isEditableField()} is {@code false} or
   *                         because the role has no explicit WindowAccess and the fallback is
   *                         implicit read-only); when {@code true}, the generated JSON will have
   *                         {@code uIPattern = "RO"} regardless of the tab-level access settings
   */
  public TabBuilder(Tab tab, TabAccess tabAccess, boolean isWindowReadOnly) {
    this(tab, tabAccess, isWindowReadOnly, null);
  }

  /**
   * Constructs a TabBuilder for the given tab, using a pre-loaded field access list instead of
   * lazily fetching it from {@code tabAccess}. Used by {@link WindowBuilder} to batch-load field
   * access data for all tabs of a window in a single query, avoiding an N+1 query pattern.
   *
   * @param tab                      the tab entity to build JSON for
   * @param tabAccess                the role-specific tab access configuration, or {@code null}
   * @param isWindowReadOnly         {@code true} if the parent window was resolved as read-only
   * @param preloadedFieldAccessList the field access records for this tab, already loaded by the
   *                                 caller; or {@code null} to fall back to lazily fetching them
   *                                 from {@code tabAccess}
   */
  public TabBuilder(Tab tab, TabAccess tabAccess, boolean isWindowReadOnly,
      List<FieldAccess> preloadedFieldAccessList) {
    this.tab = tab;
    this.tabAccess = tabAccess;
    this.isWindowReadOnly = isWindowReadOnly;
    this.preloadedFieldAccessList = preloadedFieldAccessList;
  }

  public JSONObject toJSON() {
    try {
      JSONObject json = converter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE);

      json.put("filter", tab.getFilterClause());
      json.put("displayLogic", tab.getDisplayLogic());

      String displayLogic = tab.getDisplayLogic();
      if (displayLogic != null && !displayLogic.isBlank()) {
        String displayLogicExpression = parseDisplayLogicExpression(displayLogic);
        if (displayLogicExpression != null) {
          json.put("displayLogicExpression", displayLogicExpression);
        }
      }

      json.put("entityName", tab.getTable().getName());
      JSONArray parentColumns = getParentColumns();
      json.put("parentColumns", parentColumns);

      JSONObject fields = getFields();
      enrichWithAuditFields(fields);
      enrichWithKeyColumnField(fields);
      enrichWithParentColumnFields(fields, parentColumns);
      json.put("fields", fields);

      Tab parentTab = getParentTab();

      if (parentTab != null) {
        json.put("parentTabId", parentTab.getId());
        // Classic's own answer, empty string included. An empty value is a real answer — "this
        // tab has no link column to its parent" — and the grid must then filter through the
        // tab's hqlwhereclause instead of an invented criteria, exactly as OBViewGrid does.
        // parentColumns cannot carry it: when the property is blank that array falls back to
        // every isLinkToParentColumn of the table, which is what made clients pick an
        // unrelated column. See ApplicationUtils.getParentProperty.
        json.put(PARENT_PROPERTY, resolveParentProperty());
      }

      if (Boolean.TRUE.equals(tab.isTreeIncluded())) {
        addTreeProperties(json);
      }

      boolean isTabReadOnly = isWindowReadOnly || (tabAccess != null && !tabAccess.isEditableField());
      if (isTabReadOnly) {
        json.put("uIPattern", "RO");
        json.put("readOnly", true);
      }

      json.put("obuiappCanAdd", Boolean.TRUE.equals(tab.isObuiappCanAdd()));

      return json;
    } catch (JSONException e) {
      logger.warn(e.getMessage(), e);
      throw new InternalServerException();
    }
  }

  private void addTreeProperties(JSONObject json) throws JSONException {
    json.put("hasTree", true);
    if (tab.getTable() != null) {
      json.put("tableId", tab.getTable().getId());
    }
    TableTree tableTree = tab.getTableTree();
    if (tableTree != null) {
      json.put("tableTreeId", tableTree.getId());
      if (tableTree.getTreeStructure() != null) {
        json.put("treeStructure", tableTree.getTreeStructure());
      }
    }
    json.put("isReadOnlyTree", Boolean.TRUE.equals(tab.isReadOnlyTree()));
    json.put("showTreeNodeIcons", Boolean.TRUE.equals(tab.isShowTreeNodeIcons()));
    String hqlWhere = tab.getHQLWhereClauseForRootNodes();
    if (hqlWhere != null && !hqlWhere.isEmpty()) {
      json.put("hqlWhereClauseForRootNodes", hqlWhere);
    }
  }

  private Tab getParentTab() {
    return KernelUtils.getInstance().getParentTab(tab);
  }

  /**
   * Resolves this tab's link-to-parent property with the very function the classic UI uses,
   * {@link ApplicationUtils#getParentProperty(Tab, Tab)}, and memoizes the result so the two
   * consumers ({@link #getParentColumns()} and the {@code parentProperty} JSON key) never
   * disagree and never pay for the lookup twice.
   *
   * @return the property name, or an empty string when the tab has no link column to its parent
   *         (a legitimate answer: such tabs are filtered by their hqlwhereclause alone)
   */
  private String resolveParentProperty() {
    if (parentProperty == null) {
      Tab parentTab = tab.getTabLevel() == 0 ? null : getParentTab();
      parentProperty = parentTab == null ? "" : ApplicationUtils.getParentProperty(tab, parentTab);
      if (parentProperty == null) {
        parentProperty = "";
      }
    }
    return parentProperty;
  }

  /**
   * Parses the display logic string into a JavaScript expression.
   *
   * @param displayLogic the display logic string to parse
   * @return the parsed JavaScript expression, or null if parsing fails
   */
  private String parseDisplayLogicExpression(String displayLogic) {
    try {
      org.openbravo.client.application.DynamicExpressionParser parser = new org.openbravo.client.application.DynamicExpressionParser(
          displayLogic, tab, (org.openbravo.model.ad.ui.Field) null);
      return parser.getJSExpression();
    } catch (Exception e) {
      logger.warn("Error parsing display logic for tab {}: {}", tab.getId(), e.getMessage());
      return null;
    }
  }

  private JSONArray getParentColumns() {
    JSONArray jsonColumns = new JSONArray();

    if (tab.getTabLevel() == 0) return jsonColumns;

    Tab parentTab = getParentTab();
    List<String> linkToParentColumns = new ArrayList<>();

    for (Column column : tab.getTable().getADColumnList()) {
      if (column.isLinkToParentColumn()) {
        String entityColumnName = TabProcessor.getEntityColumnName(column);
        if (StringUtils.isNotBlank(entityColumnName)) {
          linkToParentColumns.add(entityColumnName);
        }
      }
    }

    if (parentTab != null) {
      String resolvedParentProperty = resolveParentProperty();
      if (StringUtils.isNotBlank(resolvedParentProperty)) {
        jsonColumns.put(resolvedParentProperty);
        if (!linkToParentColumns.isEmpty() && !linkToParentColumns.contains(resolvedParentProperty)) {
          logger.warn(
              "Parent columns mismatch in tab {} ({}). parentTabId={}, parentProperty='{}', linkToParentColumns={}",
              tab.getId(), tab.getName(), parentTab.getId(), resolvedParentProperty, linkToParentColumns);
        }
        return jsonColumns;
      }
    }

    for (String columnName : linkToParentColumns) {
      jsonColumns.put(columnName);
    }

    return jsonColumns;
  }

  private JSONObject getFields() throws JSONException {
    if (preloadedFieldAccessList != null) {
      return preloadedFieldAccessList.isEmpty()
          ? TabProcessor.getTabFields(tab)
          : TabProcessor.getTabFields(tabAccess, preloadedFieldAccessList);
    }

    List<FieldAccess> adFieldAccessList = tabAccess != null ? tabAccess.getADFieldAccessList() : null;
    return (adFieldAccessList == null || adFieldAccessList.isEmpty())
        ? TabProcessor.getTabFields(tab)
        : TabProcessor.getTabFields(tabAccess);
  }

  /**
   * Enriches the fields object with standard audit fields if they are not already defined.
   * They are injected hidden from the grid, see {@link #AUDIT_FIELD_SHOWN_IN_GRID_BY_DEFAULT}.
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
          JSONObject syntheticField = createAuditField(column, auditField, baseGridPosition + order,
              AUDIT_FIELD_SHOWN_IN_GRID_BY_DEFAULT);
          fieldsJson.put(auditField, syntheticField);
          order++;
        } else {
          logger.debug("Audit column '{}' not found in table '{}'- skipping audit field '{}'", dbColumnName,
              table.getName(), auditField);
        }
      }
    }
  }

  /**
   * Ensures the tab's own primary key column is represented in the fields JSON, even when
   * no AD_Field exposes it as a UI element (e.g. a child tab whose record ID is never shown
   * as a field, only used as an implicit target for other tabs' FKs, such as Mentors).
   * <p>
   * Without this, the frontend has no way to resolve the key column's DB name/input name,
   * so it can't correctly build the FormInitializationComponent (session/callout) request,
   * and record creation silently loses server-computed defaults for that tab.
   *
   * @param fieldsJson the JSON object containing the tab's fields
   * @throws JSONException if there is an error manipulating the JSON structure
   */
  private void enrichWithKeyColumnField(JSONObject fieldsJson) throws JSONException {
    Table table = tab.getTable();
    if (table == null || fieldsJson.has(ID_HQL_NAME)) {
      return;
    }

    Column keyColumn = table.getADColumnList().stream()
        .filter(column -> Boolean.TRUE.equals(column.isKeyColumn()))
        .findFirst()
        .orElse(null);

    if (keyColumn != null) {
      JSONObject syntheticFieldJson = buildSyntheticFieldJson(keyColumn, ID_HQL_NAME);
      if (syntheticFieldJson != null) {
        fieldsJson.put(ID_HQL_NAME, syntheticFieldJson);
      }
    }
  }

  /**
   * Ensures every column listed in {@code parentColumns} is also represented in the fields
   * JSON, even when no AD_Field exposes it as a UI element. Child tabs that link to their
   * parent purely through an implicit FK (marked {@code isLinkToParentColumn}, with no visible
   * field) would otherwise leave the frontend unable to resolve the DB column/input name for
   * that property, so it could never send the parent's id when creating a new record.
   *
   * @param fieldsJson    the JSON object containing the tab's fields
   * @param parentColumns the hql property names already computed by {@link #getParentColumns()}
   * @throws JSONException if there is an error manipulating the JSON structure
   */
  private void enrichWithParentColumnFields(JSONObject fieldsJson, JSONArray parentColumns) throws JSONException {
    Table table = tab.getTable();
    if (table == null) {
      return;
    }

    for (int i = 0; i < parentColumns.length(); i++) {
      String propertyName = parentColumns.getString(i);
      if (fieldsJson.has(propertyName)) {
        continue;
      }

      Column matchingColumn = table.getADColumnList().stream()
          .filter(Column::isLinkToParentColumn)
          .filter(column -> propertyName.equals(TabProcessor.getEntityColumnName(column)))
          .findFirst()
          .orElse(null);

      if (matchingColumn != null) {
        JSONObject syntheticFieldJson = buildSyntheticFieldJson(matchingColumn, propertyName);
        if (syntheticFieldJson != null) {
          fieldsJson.put(propertyName, syntheticFieldJson);
        }
      }
    }
  }

  /**
   * Builds a field JSON for a column that has no real AD_Field, by delegating to
   * {@link FieldBuilderWithColumn} against a transient (never persisted) {@link Field}
   * wrapping that column. This reuses the exact same logic used for real fields —
   * column metadata, reference info, selector info, read-only logic — instead of
   * hand-duplicating a parallel JSON shape that could drift out of sync over time.
   * <p>
   * The resulting JSON's {@code hqlName} is forced to the given {@code hqlName} parameter
   * afterwards, since the caller (either {@link #enrichWithKeyColumnField} or
   * {@link #enrichWithParentColumnFields}) already computed the authoritative property name
   * for this column and the fields map is keyed by it; the two must always agree.
   *
   * @param column  the column with no AD_Field to build a synthetic field for
   * @param hqlName the hql property name this field must be keyed/reported under
   * @return the field JSON, built through the standard field-building pipeline, or {@code null}
   *         if the synthetic field could not be built for this column
   * @throws JSONException if there is an error manipulating the JSON structure
   */
  private JSONObject buildSyntheticFieldJson(Column column, String hqlName) throws JSONException {
    try {
      Field syntheticField = (Field) OBProvider.getInstance().get(Field.class);
      // client/organization: the FieldBuilder constructor converts the field to JSON via
      // converter.toJsonObject() before any of the enrichment steps run — unlike those steps,
      // that call isn't wrapped in a try/catch, so it must not throw for a plain transient
      // object. Setting them to the tab's own client/org keeps that conversion safe and is
      // also semantically correct (the field conceptually belongs to the same client/org).
      syntheticField.setClient(tab.getClient());
      syntheticField.setOrganization(tab.getOrganization());
      // The column's own id is already a unique 32-char identifier — a column can't be both
      // this tab's key column and a parent-link column at once, so reusing it verbatim as
      // the synthetic field's id can't collide with another field on the same tab. AD_Field.id
      // is a varchar(32); any added prefix (e.g. "id_" + column.getId()) overflows that length
      // and Openbravo rejects the transient object with "Value too long".
      syntheticField.setId(column.getId());
      syntheticField.setTab(tab);
      syntheticField.setColumn(column);
      syntheticField.setName(column.getName());
      syntheticField.setDisplayed(false);
      syntheticField.setReadOnly(true);
      syntheticField.setShowInGridView(false);

      JSONObject fieldJson = new FieldBuilderWithColumn(syntheticField, null).toJSON();
      fieldJson.put("hqlName", hqlName);

      return fieldJson;
    } catch (RuntimeException e) {
      // A single malformed/exotic column (e.g. missing reference metadata) must not take down
      // the whole tab's JSON - log and omit this synthetic field, the rest of the tab still builds.
      logger.warn("Error building synthetic field for column {} ({}): {}", column.getId(), hqlName,
          e.getMessage(), e);
      return null;
    }
  }

  /**
   * Searches for a column in the table by its database column name.
   *
   * @param table        the table to search in
   * @param dbColumnName the database column name to search for
   * @return the matching Column object, or null if not found
   */
  private Column findColumnByDBName(Table table, String dbColumnName) {
    return table.getADColumnList().stream()
        .filter(col -> StringUtils.equalsIgnoreCase(col.getDBColumnName(), dbColumnName))
        .findFirst()
        .orElse(null);
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
    field.put("isAuditField", true);

    return field;
  }

  /**
   * Determines if a field is a user reference type based on its name.
   *
   * @param hqlName the HQL property name
   * @return true if the field is a user reference, false otherwise
   */
  private boolean isUserField(String hqlName) {
    return StringUtils.equals(hqlName, Constants.CREATED_BY) || StringUtils.equals(hqlName, Constants.UPDATED_BY);
  }
}
