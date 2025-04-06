package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.etendoerp.metadata.utils.Utils.evaluateDisplayLogicAtServerLevel;

public class TabBuilder extends Builder {
    private final Tab tab;
    private final TabAccess tabAccess;

    public TabBuilder(Tab tab, TabAccess tabAccess) {
        this.tab = tab;
        this.tabAccess = tabAccess;
    }

    public JSONObject toJSON() {
        try {
            JSONObject json = converter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE);
            Language language = OBContext.getOBContext().getLanguage();

            json.put("id", tab.getId());
            json.put("level", tab.getTabLevel());
            json.put("filter", tab.getFilterClause());
            json.put("displayLogic", tab.getDisplayLogic());
            json.put("windowId", tab.getWindow().getId());
            json.put("entityName", tab.getTable().getName());
            json.put("name", tab.get(Tab.PROPERTY_NAME, language, tab.getId()));
            json.put("title", tab.getWindow().get(Window.PROPERTY_NAME, language, tab.getWindow().getId()));
            json.put("description", tab.get(Tab.PROPERTY_DESCRIPTION, language, tab.getId()));
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
        Long level = tab.getTabLevel();
        if (tab.getTabLevel() > 0) {
            for (Tab relatedTab : tab.getWindow().getADTabList()) {
                if (level == relatedTab.getTabLevel() + 1) {
                    return relatedTab;
                }

            }
        }

        return null;
    }

    private JSONArray getParentColumns() {
        JSONArray jsonColumns = new JSONArray();

        if (tab.getTabLevel() > 0) {
            for (Column column : tab.getTable().getADColumnList()) {
                if (column.isLinkToParentColumn()) {
                    jsonColumns.put(getEntityColumnName(column));
                }
            }
        }

        return jsonColumns;
    }

    private JSONObject getFields() throws JSONException {
        JSONObject fields = new JSONObject();
        List<FieldAccess> adFieldAccessList = tabAccess != null ? tabAccess.getADFieldAccessList() : null;

        if (adFieldAccessList == null || adFieldAccessList.isEmpty()) {
            for (Field field : getFieldList()) {
                String columnName = getEntityColumnName(field.getColumn());

                if (evaluateDisplayLogicAtServerLevel(field)) {
                    fields.put(columnName, getJSONField(field, null));
                }
            }
        } else {
            for (FieldAccess fieldAccess : getFieldAccessList(adFieldAccessList)) {
                Field field = fieldAccess.getField();
                String columnName = getEntityColumnName(field.getColumn());

                if (evaluateDisplayLogicAtServerLevel(field)) {
                    fields.put(columnName, getJSONField(field, fieldAccess));
                }
            }
        }

        return fields;
    }


    private List<FieldAccess> getFieldAccessList(List<FieldAccess> adFieldAccessList) {
        List<FieldAccess> result = new ArrayList<>();
        for (FieldAccess fieldAccess : adFieldAccessList) {
            if (isFieldAccessAllowed(fieldAccess)) {
                result.add(fieldAccess);
            }
        }
        return result;
    }

    private List<Field> getFieldList() {
        List<Field> result = new ArrayList<>();
        for (Field field : tab.getADFieldList()) {
            if (isFieldAllowed(field)) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isFieldAllowed(Field field) {
        return field.isActive() && hasAccessToProcess(field, tab.getWindow().getId());
    }

    private boolean isFieldAccessAllowed(FieldAccess fieldAccess) {
        Field field = fieldAccess.getField();
        return fieldAccess.isActive() && field.isActive() && hasAccessToProcess(field, tab.getWindow().getId());
    }

    private String getEntityColumnName(Column column) {
        String tableName = column.getTable().getName();
        String columnName = column.getDBColumnName();
        return ModelProvider.getInstance().getEntity(tableName).getPropertyByColumnName(columnName).getName();
    }

    private JSONObject getJSONField(Field field, FieldAccess access) throws JSONException {
        return new FieldBuilder(field, access).toJSON();
    }

    protected boolean hasAccessToProcess(Field field, String windowId) {
        Process process = field.getColumn() != null && field.getColumn().getOBUIAPPProcess() != null ?
                          field.getColumn().getOBUIAPPProcess() : null;

        if (process != null) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("windowId", windowId);
            return BaseProcessActionHandler.hasAccess(process, params);
        }

        return true;
    }
}
