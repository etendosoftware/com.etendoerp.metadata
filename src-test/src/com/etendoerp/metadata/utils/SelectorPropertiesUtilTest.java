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
package com.etendoerp.metadata.utils;

import static com.etendoerp.metadata.MetadataTestConstants.DISPLAY_PROPERTY;
import static com.etendoerp.metadata.MetadataTestConstants.IDENTIFIER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.datasource.DatasourceField;
import org.openbravo.service.json.JsonConstants;
import org.openbravo.userinterface.selector.Selector;
import org.openbravo.userinterface.selector.SelectorField;

import com.etendoerp.metadata.builders.FieldBuilder;

/**
 * Test class for {@link SelectorPropertiesUtil}, which builds selector property
 * metadata and grid columns for custom selectors.
 * Uses JUnit 5 and Mockito; the static back-calls to {@link FieldBuilder} are stubbed
 * via {@link MockedStatic} with {@code CALLS_REAL_METHODS}.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SelectorPropertiesUtilTest {

  private static final String VALUE_PROPERTY = "valueProperty";
  private static final String DERIVED_PROPERTY = "parent" + JsonConstants.FIELD_SEPARATOR + "child";
  private static final String EXTRA_FIELD = "extraField";
  private static final String ACCESSOR_VALUE = "accessorValue";
  private static final String HEADER = "Column Header";
  private static final String COLUMN_ID = "column-id";
  private static final String REFERENCE_ID = "ref-id";
  private static final String KEY_REFERENCE_ID = "referenceId";

  /**
   * Creates a {@link SelectorField} mock whose {@code getObuiselSelector()} returns the
   * given selector. Helper to avoid repeating mock wiring across tests.
   *
   * @param selector the selector the field belongs to
   * @return the configured {@link SelectorField} mock
   */
  private static SelectorField fieldWithSelector(Selector selector) {
    SelectorField sf = mock(SelectorField.class);
    when(sf.getObuiselSelector()).thenReturn(selector);
    return sf;
  }

  /**
   * Stubs {@link FieldBuilder#getPropertyOrDataSourceField(SelectorField)} for the given
   * field on the provided static mock.
   *
   * @param fb   the static mock of {@link FieldBuilder}
   * @param sf   the selector field
   * @param name the property name to return
   */
  private static void stubName(MockedStatic<FieldBuilder> fb, SelectorField sf, String name) {
    fb.when(() -> FieldBuilder.getPropertyOrDataSourceField(sf)).thenReturn(name);
  }

  /**
   * Executes {@link SelectorPropertiesUtil#buildGridColumn(SelectorField)} with the
   * shared {@link OBContext} and {@link FieldBuilder} static stubs, so each test only
   * needs to configure the selector field's reference resolution.
   *
   * @param sf the selector field to build the column for
   * @return the resulting grid column JSON
   * @throws JSONException if the JSON structure cannot be built
   */
  private JSONObject buildColumn(SelectorField sf) throws JSONException {
    when(sf.get(eq(SelectorField.PROPERTY_NAME), any())).thenReturn(HEADER);
    OBContext context = mock(OBContext.class);
    when(context.getLanguage()).thenReturn(mock(Language.class));
    try (MockedStatic<OBContext> obContext = mockStatic(OBContext.class);
        MockedStatic<FieldBuilder> fb = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      obContext.when(OBContext::getOBContext).thenReturn(context);
      stubName(fb, sf, ACCESSOR_VALUE);
      return SelectorPropertiesUtil.buildGridColumn(sf);
    }
  }

  /**
   * Tests setSelectorProperties with non-identifier display and value fields and an
   * empty field list: the display field is added to both selected and extra properties.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testSetSelectorPropertiesWithDisplayAndValueFields() throws JSONException {
    Selector selector = mock(Selector.class);
    SelectorField displayField = fieldWithSelector(selector);
    SelectorField valueField = fieldWithSelector(selector);
    JSONObject info = new JSONObject();

    try (MockedStatic<FieldBuilder> fb = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      fb.when(() -> FieldBuilder.getValueField(selector)).thenReturn(VALUE_PROPERTY);
      fb.when(() -> FieldBuilder.getDisplayField(selector)).thenReturn(DISPLAY_PROPERTY);

      SelectorPropertiesUtil.setSelectorProperties(Collections.emptyList(), displayField, valueField, info);

      assertEquals(JsonConstants.ID + "," + DISPLAY_PROPERTY,
          info.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER));
      assertEquals(VALUE_PROPERTY + "," + DISPLAY_PROPERTY + ",",
          info.getString(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER));
    }
  }

  /**
   * Tests setSelectorProperties with null display and value fields: both fall back to
   * the identifier and only the default id is kept as a selected property.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testSetSelectorPropertiesWithNullDisplayAndValue() throws JSONException {
    JSONObject info = new JSONObject();

    SelectorPropertiesUtil.setSelectorProperties(Collections.emptyList(), null, null, info);

    assertEquals(JsonConstants.ID, info.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER));
    assertEquals(IDENTIFIER + ",", info.getString(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER));
  }

  /**
   * Tests setSelectorProperties routes a field whose name contains the field separator
   * into the derived properties bucket.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testSetSelectorPropertiesWithDerivedProperty() throws JSONException {
    SelectorField derivedField = mock(SelectorField.class);
    when(derivedField.isOutfield()).thenReturn(false);
    JSONObject info = new JSONObject();

    try (MockedStatic<FieldBuilder> fb = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      stubName(fb, derivedField, DERIVED_PROPERTY);

      SelectorPropertiesUtil.setSelectorProperties(List.of(derivedField), null, null, info);

      assertEquals(JsonConstants.ID, info.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER));
      assertTrue(info.getString(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER).endsWith(DERIVED_PROPERTY));
    }
  }

  /**
   * Tests setSelectorProperties adds an outfield (matching the null display/value
   * fields) to both selected and extra properties via the isExtraProperty branch.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testSetSelectorPropertiesWithExtraProperty() throws JSONException {
    SelectorField outField = mock(SelectorField.class);
    when(outField.isOutfield()).thenReturn(true);
    JSONObject info = new JSONObject();

    try (MockedStatic<FieldBuilder> fb = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      stubName(fb, outField, EXTRA_FIELD);

      SelectorPropertiesUtil.setSelectorProperties(List.of(outField), null, null, info);

      assertTrue(info.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER).contains(EXTRA_FIELD));
      assertTrue(info.getString(JsonConstants.ADDITIONAL_PROPERTIES_PARAMETER).contains(EXTRA_FIELD));
    }
  }

  /**
   * Tests setSelectorProperties skips fields named after the reserved id/identifier
   * keywords, leaving the selected properties unchanged.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testSetSelectorPropertiesSkipsIdField() throws JSONException {
    SelectorField idField = mock(SelectorField.class);
    JSONObject info = new JSONObject();

    try (MockedStatic<FieldBuilder> fb = mockStatic(FieldBuilder.class, CALLS_REAL_METHODS)) {
      stubName(fb, idField, JsonConstants.ID);

      SelectorPropertiesUtil.setSelectorProperties(List.of(idField), null, null, info);

      assertEquals(JsonConstants.ID, info.getString(JsonConstants.SELECTEDPROPERTIES_PARAMETER));
    }
  }

  /**
   * Tests buildGridColumn populates every column attribute and resolves the reference id
   * from the selector field's own reference.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testBuildGridColumnWithOwnReference() throws JSONException {
    SelectorField sf = mock(SelectorField.class);
    when(sf.getId()).thenReturn(COLUMN_ID);
    when(sf.isSortable()).thenReturn(true);
    when(sf.isFilterable()).thenReturn(false);
    when(sf.getSortno()).thenReturn(5L);
    Reference reference = mock(Reference.class);
    when(sf.getReference()).thenReturn(reference);
    when(reference.getId()).thenReturn(REFERENCE_ID);

    JSONObject result = buildColumn(sf);

    assertEquals(COLUMN_ID, result.getString("id"));
    assertEquals(HEADER, result.getString("header"));
    assertEquals(ACCESSOR_VALUE, result.getString("accessorKey"));
    assertTrue(result.getBoolean("enableSorting"));
    assertFalse(result.getBoolean("enableFiltering"));
    assertEquals(5L, result.getLong("sortNo"));
    assertEquals(REFERENCE_ID, result.getString(KEY_REFERENCE_ID));
  }

  /**
   * Tests buildGridColumn resolves the reference id from the field's column when the
   * field has no own reference.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testBuildGridColumnWithColumnReference() throws JSONException {
    SelectorField sf = mock(SelectorField.class);
    Column column = mock(Column.class);
    Reference reference = mock(Reference.class);
    when(sf.getColumn()).thenReturn(column);
    when(column.getReference()).thenReturn(reference);
    when(reference.getId()).thenReturn(REFERENCE_ID);

    JSONObject result = buildColumn(sf);

    assertEquals(REFERENCE_ID, result.getString(KEY_REFERENCE_ID));
  }

  /**
   * Tests buildGridColumn resolves the reference id from the datasource field reference
   * when neither the field nor its column provide one.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testBuildGridColumnWithDatasourceFieldReference() throws JSONException {
    SelectorField sf = mock(SelectorField.class);
    DatasourceField datasourceField = mock(DatasourceField.class);
    Reference reference = mock(Reference.class);
    when(sf.getObserdsDatasourceField()).thenReturn(datasourceField);
    when(datasourceField.getReference()).thenReturn(reference);
    when(reference.getId()).thenReturn(REFERENCE_ID);

    JSONObject result = buildColumn(sf);

    assertEquals(REFERENCE_ID, result.getString(KEY_REFERENCE_ID));
  }

  /**
   * Tests buildGridColumn omits the referenceId key when no reference can be resolved.
   *
   * @throws JSONException if the JSON structure cannot be built
   */
  @Test
  void testBuildGridColumnWithNullReference() throws JSONException {
    SelectorField sf = mock(SelectorField.class);

    JSONObject result = buildColumn(sf);

    assertFalse(result.has(KEY_REFERENCE_ID));
  }

  /**
   * Tests that the utility class cannot be instantiated and that its private constructor
   * throws an {@link UnsupportedOperationException}.
   *
   * @throws NoSuchMethodException if the declared constructor cannot be found
   */
  @Test
  void testPrivateConstructorThrows() throws NoSuchMethodException {
    Constructor<SelectorPropertiesUtil> constructor = SelectorPropertiesUtil.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    InvocationTargetException exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
    assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
  }
}
