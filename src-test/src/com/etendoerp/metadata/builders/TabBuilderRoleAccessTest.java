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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.metadata.utils.Constants;

/**
 * Unit tests for the role access handling of {@link TabBuilder}: the read-only state resolved by
 * the caller and the Field Access applied on top of the complete field list.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class TabBuilderRoleAccessTest extends TabBuilderTestBase {

  private static final String FIELDS_KEY = "fields";
  private static final String UI_PATTERN_KEY = "uIPattern";
  private static final String RO_PATTERN = "RO";
  private static final String RESTRICTED_FIELD = "description";
  private static final String UNLISTED_FIELD = "poReference";
  private static final String RESTRICTED_FIELD_ID = "description-field-id";
  private static final String UNLISTED_FIELD_ID = "po-reference-field-id";

  /**
   * Tests that a tab resolved as read-only is rendered with the RO pattern even if its tab access
   * is editable, because the caller already applied Classic's rule.
   *
   * @throws Exception if the test context cannot be set up
   */
  @Test
  void resolvedReadOnlyTabIsRenderedReadOnly() throws Exception {
    TestContext ctx = setupContext();
    TabAccess tabAccess = mockTabAccess(true);

    executeResolvedAccessTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), tabAccess,
        List.of(), true, result -> assertEquals(RO_PATTERN, result.optString(UI_PATTERN_KEY)));
  }

  /**
   * Tests that a tab resolved as editable is not forced to RO, even when its window access is
   * read-only: an editable Tab Access overrides it, as in Classic.
   *
   * @throws Exception if the test context cannot be set up
   */
  @Test
  void resolvedEditableTabIsNotForcedReadOnly() throws Exception {
    TestContext ctx = setupContext();

    executeResolvedAccessTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, new JSONObject(), null, List.of(),
        false, result -> assertFalse(RO_PATTERN.equals(result.optString(UI_PATTERN_KEY))));
  }

  /**
   * Tests the ticket scenario: with an editable Tab Access, the field listed in Field Access as not
   * editable becomes read-only while the unlisted field stays editable and visible.
   *
   * @throws Exception if the test context cannot be set up
   */
  @Test
  void fieldAccessRestrictsOnlyTheListedField() throws Exception {
    TestContext ctx = setupContext();
    TabAccess tabAccess = mockTabAccess(true);
    FieldAccess fieldAccess = mockFieldAccess(RESTRICTED_FIELD_ID, false);

    executeResolvedAccessTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, createTabFields(), tabAccess,
        List.of(fieldAccess), false, result -> {
          JSONObject fields = getFields(result);
          assertTrue(isReadOnly(fields, RESTRICTED_FIELD));
          assertFalse(isReadOnly(fields, UNLISTED_FIELD));
        });
  }

  /**
   * Tests that, with a non-editable Tab Access, the unlisted fields inherit the tab editability
   * and none of them is hidden.
   *
   * @throws Exception if the test context cannot be set up
   */
  @Test
  void unlistedFieldsInheritNonEditableTabAccess() throws Exception {
    TestContext ctx = setupContext();
    TabAccess tabAccess = mockTabAccess(false);

    executeResolvedAccessTabBuilderTest(ctx.context, ctx.kernelUtils, ctx.tab, createTabFields(), tabAccess,
        List.of(), true, result -> {
          JSONObject fields = getFields(result);
          assertTrue(fields.has(RESTRICTED_FIELD));
          assertTrue(isReadOnly(fields, UNLISTED_FIELD));
        });
  }

  private TestContext setupContext() {
    TestContext ctx = setupTestContext();
    setupBasicMocks(ctx.context, ctx.language, ctx.tab, ctx.table, ctx.kernelUtils, List.of());
    return ctx;
  }

  private static TabAccess mockTabAccess(boolean editable) {
    TabAccess tabAccess = mock(TabAccess.class);
    when(tabAccess.isActive()).thenReturn(true);
    when(tabAccess.isEditableField()).thenReturn(editable);
    return tabAccess;
  }

  private static FieldAccess mockFieldAccess(String fieldId, boolean editable) {
    Field field = mock(Field.class);
    when(field.getId()).thenReturn(fieldId);
    FieldAccess fieldAccess = mock(FieldAccess.class);
    when(fieldAccess.isActive()).thenReturn(true);
    when(fieldAccess.isEditableField()).thenReturn(editable);
    when(fieldAccess.getField()).thenReturn(field);
    return fieldAccess;
  }

  private static JSONObject createTabFields() throws JSONException {
    JSONObject fields = new JSONObject();
    fields.put(RESTRICTED_FIELD, createEditableField(RESTRICTED_FIELD_ID));
    fields.put(UNLISTED_FIELD, createEditableField(UNLISTED_FIELD_ID));
    return fields;
  }

  private static JSONObject createEditableField(String fieldId) throws JSONException {
    JSONObject field = new JSONObject();
    field.put(Field.PROPERTY_ID, fieldId);
    field.put(Constants.JSON_COLUMN_NAME_KEY, fieldId);
    field.put(Constants.JSON_IS_UPDATABLE_KEY, true);
    field.put(Constants.JSON_IS_READ_ONLY_KEY, false);
    return field;
  }

  private static JSONObject getFields(JSONObject result) {
    try {
      return result.getJSONObject(FIELDS_KEY);
    } catch (JSONException e) {
      return fail("The tab JSON must contain its fields: " + e.getMessage());
    }
  }

  private static boolean isReadOnly(JSONObject fields, String fieldName) {
    return fields.optJSONObject(fieldName).optBoolean(Constants.JSON_IS_READ_ONLY_KEY);
  }
}
