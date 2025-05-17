package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.utils.Utils.evaluateDisplayLogicAtServerLevel;

import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataResolvingMode;

import com.etendoerp.metadata.data.TabProcessor;
import com.etendoerp.metadata.exceptions.InternalServerException;

public class TabBuilder extends Builder {
  private final Tab tab;
  private final TabAccess tabAccess;

  public TabBuilder(Tab tab, TabAccess tabAccess) {
    this.tab = tab;
    this.tabAccess = tabAccess;
  }

  private static void addField(Field field, JSONObject fields, JSONObject field1) throws JSONException {
    String columnName = TabProcessor.getEntityColumnName(field.getColumn());

    if (evaluateDisplayLogicAtServerLevel(field)) {
      fields.put(columnName, field1);
    }
  }

  public JSONObject toJSON() {
    try {
      JSONObject json = converter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE);

      json.put("filter", tab.getFilterClause());
      json.put("displayLogic", tab.getDisplayLogic());
      json.put("entityName", tab.getTable().getName());
      json.put("parentColumns", getParentColumns());
      json.put("fields", getFields());

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
    JSONObject result = new JSONObject();
    List<FieldAccess> adFieldAccessList = tabAccess != null ? tabAccess.getADFieldAccessList() : null;

    if (adFieldAccessList == null || adFieldAccessList.isEmpty()) {
      for (Field field : TabProcessor.getFieldList(tab)) {
        addField(field, result, TabProcessor.getJSONField(field, null));
      }
    } else {
      for (FieldAccess fieldAccess : TabProcessor.getFieldAccessList(tabAccess)) {
        Field field = fieldAccess.getField();
        addField(field, result, TabProcessor.getJSONField(field, fieldAccess));
      }
    }

    return result;
  }
}
