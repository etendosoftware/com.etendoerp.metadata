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
package com.etendoerp.metadata.data;

import static com.etendoerp.metadata.MetadataTestConstants.TEST_COLUMN_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_DATE;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_PROPERTY_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TABLE_NAME;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TAB_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;

/**
 * Tests how TabProcessor keys property fields (AD_Field.Property set) in the fields JSON of a tab.
 * A property field whose path crosses a related record is keyed by that path, as Classic names it.
 */
@ExtendWith(MockitoExtension.class)
class TabProcessorPropertyFieldTest {

  @Mock
  private Field ownField;
  @Mock
  private Column column;
  @Mock
  private Table table;
  @Mock
  private ModelProvider modelProvider;
  @Mock
  private Entity entity;
  @Mock
  private Property property;
  @Mock
  private OBContext obContext;

  private MockedStatic<ModelProvider> staticModelProvider;
  private MockedStatic<OBContext> staticOBContext;

  /**
   * Resolves the column of every field to the property {@code TEST_PROPERTY_NAME} of the tab's entity.
   */
  @BeforeEach
  void setUp() {
    staticModelProvider = mockStatic(ModelProvider.class);
    staticOBContext = mockStatic(OBContext.class);
    staticModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    staticOBContext.when(OBContext::getOBContext).thenReturn(obContext);

    when(ownField.getColumn()).thenReturn(column);
    when(column.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
    when(column.getTable()).thenReturn(table);
    when(table.getName()).thenReturn(TEST_TABLE_NAME);
    when(modelProvider.getEntity(TEST_TABLE_NAME)).thenReturn(entity);
    when(entity.getPropertyByColumnName(TEST_COLUMN_NAME)).thenReturn(property);
    when(property.getName()).thenReturn(TEST_PROPERTY_NAME);
  }

  /**
   * Closes the static mocks so they do not leak into other tests.
   */
  @AfterEach
  void tearDown() {
    staticModelProvider.close();
    staticOBContext.close();
  }

  /**
   * Tests that a property field whose path crosses a related record is keyed by its path and does
   * not overwrite a field of the tab's own entity whose column resolves to the same property name.
   *
   * @throws JSONException if the JSON processing fails (not expected in this test)
   */
  @Test
  void testGetFieldsKeysDottedPropertyFieldByItsPath() throws JSONException {
    Field propertyField = mock(Field.class);
    when(propertyField.getColumn()).thenReturn(column);
    JSONObject ownFieldJson = new JSONObject();
    JSONObject propertyFieldJson = new JSONObject().put(Field.PROPERTY_PROPERTY, "invoicePaymentSchedule.amount");

    JSONObject result = getFieldsWithMapper(List.of(ownField, propertyField),
        (field, withColumn) -> field == propertyField ? propertyFieldJson : ownFieldJson);

    assertEquals(ownFieldJson, result.get(TEST_PROPERTY_NAME));
    assertEquals(propertyFieldJson, result.get("invoicePaymentSchedule$amount"));
  }

  /**
   * Tests that a single-segment property field keeps the key of its column property.
   *
   * @throws JSONException if the JSON processing fails (not expected in this test)
   */
  @Test
  void testGetFieldsKeepsColumnKeyForSingleSegmentProperty() throws JSONException {
    JSONObject fieldJson = new JSONObject().put(Field.PROPERTY_PROPERTY, "amount");

    JSONObject result = getFieldsWithMapper(List.of(ownField), (field, withColumn) -> fieldJson);

    assertEquals(fieldJson, result.get(TEST_PROPERTY_NAME));
  }

  /**
   * Runs getFields over the given fields with every field accessible and an empty cache.
   *
   * @param fields      the fields to process
   * @param fieldMapper the function that builds the JSON of each field
   * @return the fields JSON keyed by field name
   */
  @SuppressWarnings("unchecked")
  private JSONObject getFieldsWithMapper(List<Field> fields, BiFunction<Field, Boolean, JSONObject> fieldMapper) {
    ConcurrentMap<String, JSONObject> cache = mock(ConcurrentMap.class);
    return TabProcessor.getFields(TEST_TAB_ID, TEST_DATE.toString(), fields, field -> true, Field::getColumn,
        field -> null, field -> null, Field::getName, Field::setName, fieldMapper, cache);
  }
}
