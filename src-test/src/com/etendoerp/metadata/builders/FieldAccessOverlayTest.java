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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.metadata.utils.Constants;

/**
 * Unit tests for {@link FieldAccessOverlay}, which applies the role's Tab Access and Field Access
 * editability on top of the complete field list of a tab.
 */
class FieldAccessOverlayTest {

  private static final String FIELD_NAME = "description";
  private static final String FIELD_ID = "description-field-id";

  /**
   * Tests that a field listed in Field Access as not editable becomes read-only and takes the
   * check-on-save flag of its access row.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void restrictsFieldListedAsNotEditable() throws JSONException {
    FieldAccess fieldAccess = mockFieldAccess(true, false);
    when(fieldAccess.isCheckonsave()).thenReturn(false);

    JSONObject field = applySingle(editableField(), mockTabAccess(true), List.of(fieldAccess));

    assertTrue(field.getBoolean(Constants.JSON_IS_READ_ONLY_KEY));
    assertFalse(field.getBoolean(Constants.JSON_IS_EDITABLE_KEY));
    assertFalse(field.getBoolean(Constants.JSON_CHECK_ON_SAVE_KEY));
  }

  /**
   * Tests that a field without Field Access inherits a non-editable Tab Access.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void unlistedFieldInheritsNonEditableTab() throws JSONException {
    JSONObject field = applySingle(editableField(), mockTabAccess(false), List.of());

    assertTrue(field.getBoolean(Constants.JSON_IS_READ_ONLY_KEY));
    assertFalse(field.has(Constants.JSON_CHECK_ON_SAVE_KEY));
  }

  /**
   * Tests that a field listed as editable stays editable under a non-editable Tab Access.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void editableFieldAccessOverridesNonEditableTab() throws JSONException {
    JSONObject source = editableField();

    JSONObject field = applySingle(source, mockTabAccess(false), List.of(mockFieldAccess(true, true)));

    assertSame(source, field);
  }

  /**
   * Tests that an inactive Field Access is ignored, so the field inherits the Tab Access.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void inactiveFieldAccessIsIgnored() throws JSONException {
    JSONObject source = editableField();

    JSONObject field = applySingle(source, mockTabAccess(true), List.of(mockFieldAccess(false, false)));

    assertSame(source, field);
  }

  /**
   * Tests that fields that are not editable by dictionary keep their own configuration: read-only
   * fields, status bar fields, fields with a non-updatable column and fields without a column.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void fieldsNotEditableByDictionaryAreUntouched() throws JSONException {
    JSONObject readOnly = editableField().put(Field.PROPERTY_READONLY, true);
    JSONObject statusBar = editableField().put(Field.PROPERTY_SHOWNINSTATUSBAR, true);
    JSONObject notUpdatable = editableField().put(Constants.JSON_IS_UPDATABLE_KEY, false);
    JSONObject withoutColumn = editableField();
    withoutColumn.remove(Constants.JSON_COLUMN_NAME_KEY);

    for (JSONObject source : List.of(readOnly, statusBar, notUpdatable, withoutColumn)) {
      assertSame(source, applySingle(source, mockTabAccess(false), List.of()));
    }
  }

  /**
   * Tests that the overlay never mutates the (possibly cached) input and never adds or removes
   * fields.
   *
   * @throws JSONException if the fields JSON cannot be built or read
   */
  @Test
  void neverMutatesInputNorChangesFieldSet() throws JSONException {
    JSONObject source = editableField();
    JSONObject fields = new JSONObject().put(FIELD_NAME, source);

    JSONObject result = FieldAccessOverlay.apply(fields, mockTabAccess(false), List.of());

    assertEquals(1, result.length());
    assertTrue(result.has(FIELD_NAME));
    assertFalse(source.getBoolean(Constants.JSON_IS_READ_ONLY_KEY));
    assertSame(source, fields.getJSONObject(FIELD_NAME));
  }

  private static JSONObject applySingle(JSONObject field, TabAccess tabAccess, List<FieldAccess> fieldAccesses)
      throws JSONException {
    JSONObject fields = new JSONObject().put(FIELD_NAME, field);
    return FieldAccessOverlay.apply(fields, tabAccess, fieldAccesses).getJSONObject(FIELD_NAME);
  }

  private static JSONObject editableField() throws JSONException {
    return new JSONObject()
        .put(Field.PROPERTY_ID, FIELD_ID)
        .put(Constants.JSON_COLUMN_NAME_KEY, FIELD_NAME)
        .put(Constants.JSON_IS_UPDATABLE_KEY, true)
        .put(Constants.JSON_IS_READ_ONLY_KEY, false);
  }

  private static TabAccess mockTabAccess(boolean editable) {
    TabAccess tabAccess = mock(TabAccess.class);
    when(tabAccess.isEditableField()).thenReturn(editable);
    return tabAccess;
  }

  private static FieldAccess mockFieldAccess(boolean active, boolean editable) {
    Field field = mock(Field.class);
    when(field.getId()).thenReturn(FIELD_ID);
    FieldAccess fieldAccess = mock(FieldAccess.class);
    when(fieldAccess.isActive()).thenReturn(active);
    when(fieldAccess.isEditableField()).thenReturn(editable);
    when(fieldAccess.getField()).thenReturn(field);
    return fieldAccess;
  }
}
