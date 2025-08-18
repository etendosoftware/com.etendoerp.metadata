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

package com.etendoerp.metadata.data;

import static com.etendoerp.metadata.utils.Utils.evaluateDisplayLogicAtServerLevel;
import static org.openbravo.client.application.process.BaseProcessActionHandler.hasAccess;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.etendoerp.metadata.builders.FieldBuilderWithoutColumn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.redis.interfaces.CachedConcurrentMap;
import java.util.function.BiFunction;

public class TabProcessor {
  private static final Logger logger = LogManager.getLogger(TabProcessor.class);
  private static final String FIELD_CACHE = "FIELDS_METADATA";
  private static final String FIELD_ACCESS_CACHE = "FIELD_ACCESS_METADATA";
  private static final CachedConcurrentMap<String, JSONObject> fieldCache = new CachedConcurrentMap<>(FIELD_CACHE);
  private static final CachedConcurrentMap<String, JSONObject> fieldAccessCache = new CachedConcurrentMap<>(
      FIELD_ACCESS_CACHE);

  private static boolean isFieldAccessible(Field field) {
    return field.isActive() && hasAccessToProcess(field,
        field.getTab().getWindow().getId()) && evaluateDisplayLogicAtServerLevel(field);
  }

  private static boolean isFieldAccessAccessible(FieldAccess fieldAccess) {
    return fieldAccess.isActive() && isFieldAccessible(fieldAccess.getField());
  }

  public static <T> JSONObject getFields(String id, String updated, List<T> data, Predicate<T> accessPredicate,
                                         Function<T, Column> columnExtractor, Function<T,String> customJsExtractor,
                                         BiFunction<T, Boolean, JSONObject> fieldMapper,
                                         ConcurrentMap<String, JSONObject> cache) {
    String cacheKey = getCacheKey(id, updated);
    JSONObject list = cache.get(cacheKey);
    if (list != null) return list;

    JSONObject result = new JSONObject();

    for (T fieldLike : data) {
      try {
        if (accessPredicate.test(fieldLike)) {
          Column column = columnExtractor.apply(fieldLike);
          if (column != null) {
            String entityColumnName = getEntityColumnName(column);
            if (entityColumnName != null) {
              result.put(entityColumnName, fieldMapper.apply(fieldLike, true));
            } else {
              logger.warn("Could not determine entity column name for column: {} - skipping field",
                      column.getDBColumnName());
            }
          } else {
            String customJs = customJsExtractor.apply(fieldLike);
            if (customJs != null) {
              result.put("test", fieldMapper.apply(fieldLike, false));
            }
            logger.warn("Field has null column - skipping field: {}", fieldLike);
          }
        }
      } catch (JSONException e) {
        logger.warn("Error processing field: {} - {}", fieldLike, e.getMessage(), e);
      }
    }

    cache.put(cacheKey, result);
    return result;
  }

  public static JSONObject getTabFields(Tab tab) {
    return getFields(tab.getId(), tab.getUpdated().toString(), tab.getADFieldList(), TabProcessor::isFieldAccessible,
        Field::getColumn, Field::getEtmetaCustomjs ,TabProcessor::getJSONField, fieldCache);
  }

  public static JSONObject getTabFields(TabAccess tabAccess) {
    return getFields(tabAccess.getId(), tabAccess.getUpdated().toString(), tabAccess.getADFieldAccessList(),
        TabProcessor::isFieldAccessAccessible, fieldAccess -> fieldAccess.getField().getColumn(),
        fieldAccess -> fieldAccess.getField().getEtmetaCustomjs(),
        TabProcessor::getJSONField, fieldAccessCache);
  }

  public static String getEntityColumnName(Column column) {
    if (column == null) {
      logger.warn("Column parameter is null in getEntityColumnName");
      return null;
    }

    if (column.getTable() == null) {
      logger.warn("Column has null table: {}", column.getDBColumnName());
      return null;
    }

    String tableName = column.getTable().getName();
    String columnName = column.getDBColumnName();
    Entity entity = ModelProvider.getInstance().getEntity(tableName);

    if (entity == null) {
      logger.warn("No entity found for table: {}", tableName);
      return null;
    }

    Property property = entity.getPropertyByColumnName(columnName);

    if (property == null) {
      logger.warn("No property found for column: {} in table: {}", columnName, tableName);
      return null;
    }

    return property.getName();
  }

  public static JSONObject getJSONField(Field field, boolean withColumn) {
    try {
      if (withColumn) {
        return new FieldBuilderWithColumn(field, null).toJSON();
      }
      return new FieldBuilderWithoutColumn(field, null).toJSON();
    } catch (JSONException e) {
      logger.warn(e.getMessage(), e);

      return new JSONObject();
    }
  }

  public static JSONObject getJSONField(FieldAccess access, boolean withColumn) {

    try {
      if (withColumn) {
        return new FieldBuilderWithColumn(access.getField(), access).toJSON();
      }
      return new FieldBuilderWithoutColumn(access.getField(), access).toJSON();
    } catch (JSONException e) {
      logger.warn(e.getMessage(), e);

      return new JSONObject();
    }
  }

  public static boolean hasAccessToProcess(Field field, String windowId) {
    Column col = field.getColumn();

    if (col == null) return true;

    Process process = col.getOBUIAPPProcess();

    if (process == null) return true;

    return hasAccess(process, Map.of("windowId", windowId));
  }

  private static String getContext() {
    OBContext obContext = OBContext.getOBContext();

    return obContext != null ? obContext.toString() : "";
  }

  private static String getCacheKey(String id, String updated) {
    return String.join("#", id, updated, getContext());
  }
}
