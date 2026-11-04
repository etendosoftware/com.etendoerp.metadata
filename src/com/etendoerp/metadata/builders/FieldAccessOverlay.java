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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.metadata.utils.Constants;

/**
 * Applies a role's Tab Access and Field Access editability on top of the complete field list of a
 * tab, as Classic does in {@code WindowSettingsActionHandler.getFieldLevelRoles}:
 * <ul>
 * <li>only fields that are editable by dictionary (not read-only, not in the status bar, with an
 * updatable column) are affected;</li>
 * <li>a field with an active Field Access takes its editability from it;</li>
 * <li>any other field inherits the editability of the Tab Access.</li>
 * </ul>
 * No field is ever added or removed.
 */
public final class FieldAccessOverlay {

  private FieldAccessOverlay() {
  }

  /**
   * Returns the fields with the role's editability applied. The input object and its field objects
   * are never mutated, because they may be shared by the metadata field cache: changed fields are
   * copied into a new object.
   *
   * @param fields        the complete fields of the tab, keyed by field name
   * @param tabAccess     the active tab access of the role for the tab
   * @param fieldAccesses the field accesses of that tab access (active and inactive)
   * @return a new fields object with the editability applied
   * @throws JSONException if a field object cannot be read or copied
   */
  public static JSONObject apply(JSONObject fields, TabAccess tabAccess, List<FieldAccess> fieldAccesses)
      throws JSONException {
    Map<String, FieldAccess> activeByFieldId = indexActiveByFieldId(fieldAccesses);
    boolean tabEditable = Boolean.TRUE.equals(tabAccess.isEditableField());
    JSONObject result = new JSONObject();

    Iterator<?> keys = fields.keys();
    while (keys.hasNext()) {
      String key = (String) keys.next();
      JSONObject field = fields.getJSONObject(key);
      result.put(key, applyToField(field, activeByFieldId, tabEditable));
    }
    return result;
  }

  private static JSONObject applyToField(JSONObject field, Map<String, FieldAccess> activeByFieldId,
      boolean tabEditable) throws JSONException {
    if (!isEditableByDictionary(field)) {
      return field;
    }
    FieldAccess fieldAccess = activeByFieldId.get(field.optString(Field.PROPERTY_ID));
    if (isEditable(fieldAccess, tabEditable)) {
      return field;
    }

    JSONObject restricted = copy(field);
    restricted.put(Constants.JSON_IS_READ_ONLY_KEY, true);
    restricted.put(Constants.JSON_IS_EDITABLE_KEY, false);
    if (fieldAccess != null) {
      restricted.put(Constants.JSON_CHECK_ON_SAVE_KEY, Boolean.TRUE.equals(fieldAccess.isCheckonsave()));
    }
    return restricted;
  }

  private static JSONObject copy(JSONObject source) throws JSONException {
    JSONObject target = new JSONObject();
    Iterator<?> keys = source.keys();
    while (keys.hasNext()) {
      String key = (String) keys.next();
      target.put(key, source.get(key));
    }
    return target;
  }

  private static boolean isEditable(FieldAccess fieldAccess, boolean tabEditable) {
    if (fieldAccess == null) {
      return tabEditable;
    }
    return Boolean.TRUE.equals(fieldAccess.isEditableField());
  }

  private static boolean isEditableByDictionary(JSONObject field) {
    return !field.optBoolean(Field.PROPERTY_READONLY) && !field.optBoolean(Field.PROPERTY_SHOWNINSTATUSBAR)
        && field.has(Constants.JSON_COLUMN_NAME_KEY) && field.optBoolean(Constants.JSON_IS_UPDATABLE_KEY);
  }

  private static Map<String, FieldAccess> indexActiveByFieldId(List<FieldAccess> fieldAccesses) {
    Map<String, FieldAccess> result = new HashMap<>();
    for (FieldAccess fieldAccess : fieldAccesses) {
      if (Boolean.TRUE.equals(fieldAccess.isActive()) && fieldAccess.getField() != null) {
        result.put(fieldAccess.getField().getId(), fieldAccess);
      }
    }
    return result;
  }
}
