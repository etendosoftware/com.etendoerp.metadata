package com.etendoerp.metadata.data;

import static org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess;

import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.Process;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.builders.FieldBuilder;
import com.etendoerp.redis.interfaces.CachedConcurrentMap;
import com.etendoerp.redis.interfaces.CachedList;

public class TabProcessor {
  private static final String FIELD_CACHE = "FIELDS_METADATA";
  private static final String FIELD_ACCESS_CACHE = "FIELD_ACCESS_METADATA";
  private static final CachedConcurrentMap<String, CachedList<Field>> fieldCache = new CachedConcurrentMap<>(
      FIELD_CACHE);
  private static final CachedConcurrentMap<String, CachedList<FieldAccess>> fieldAccessCache = new CachedConcurrentMap<>(
      FIELD_ACCESS_CACHE);

  private static boolean isFieldAccessible(Field field) {
    return field.isActive() && hasAccessToProcess(field, field.getTab().getWindow().getId());
  }

  private static boolean isFieldAccessAccessible(FieldAccess fieldAccess) {
    return fieldAccess.isActive() && isFieldAccessible(fieldAccess.getField());
  }

  public static List<Field> getFieldList(Tab tab) {
    return CachedList.fetchAndFilter(tab.getId(), tab.getUpdated().getTime(), fieldCache, tab::getADFieldList,
        TabProcessor::isFieldAccessible);
  }

  public static List<FieldAccess> getFieldAccessList(TabAccess tabAccess) {
    return CachedList.fetchAndFilter(tabAccess.getId(), tabAccess.getUpdated().getTime(), fieldAccessCache,
        tabAccess::getADFieldAccessList, TabProcessor::isFieldAccessAccessible);
  }

  public static String getEntityColumnName(Column column) {
    String tableName = column.getTable().getName();
    String columnName = column.getDBColumnName();
    Entity entity = ModelProvider.getInstance().getEntity(tableName);

    if (entity == null) {
      return null;
    }

    Property property = entity.getPropertyByColumnName(columnName);

    if (property == null) {
      return null;
    }

    return property.getName();
  }

  public static JSONObject getJSONField(Field field, FieldAccess access) throws JSONException {
    return new FieldBuilder(field, access).toJSON();
  }

  public static boolean hasAccessToProcess(Field field, String windowId) {
    Column col = field.getColumn();

    if (col == null) return true;

    Process process = col.getOBUIAPPProcess();

    if (process == null) return true;

    return hasAccess(process, Map.of("windowId", windowId));
  }
}

