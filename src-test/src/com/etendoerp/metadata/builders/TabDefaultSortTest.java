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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.ApplicationUtils;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.order.OrderLine;

/**
 * Unit tests for {@link TabDefaultSort}, which mirrors Classic {@code OBViewGridComponent.getSortField}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TabDefaultSortTest {

  private static final String STORAGE_BIN = "storageBin";
  private static final String MOVEMENT_DATE = "movementDate";
  private static final String NAME = "name";
  private static final String TABLE_ID = "TABLE_ID";
  private static final String NO_SORT_FIELD = "";
  private static final int SHORT_LENGTH = 60;

  private final List<Field> fields = new ArrayList<>();
  private MockedStatic<KernelUtils> mockedKernelUtils;
  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<ApplicationUtils> mockedApplicationUtils;
  private KernelUtils kernelUtils;
  private Entity entity;
  private Tab tab;

  /**
   * Mocks the DAL collaborators: a tab whose fields are {@link #fields} and an entity without properties.
   */
  @BeforeEach
  void setUp() {
    kernelUtils = mock(KernelUtils.class);
    ModelProvider modelProvider = mock(ModelProvider.class);
    entity = mock(Entity.class);
    tab = mock(Tab.class);
    Table table = mock(Table.class);
    when(table.getId()).thenReturn(TABLE_ID);
    when(tab.getTable()).thenReturn(table);
    when(tab.getADFieldList()).thenReturn(fields);
    when(modelProvider.getEntityByTableId(TABLE_ID)).thenReturn(entity);

    mockedKernelUtils = mockStatic(KernelUtils.class);
    mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(kernelUtils);
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    mockedApplicationUtils = mockStatic(ApplicationUtils.class);
    mockedApplicationUtils.when(() -> ApplicationUtils.isUIButton(any(Field.class))).thenReturn(false);
  }

  /** Closes the static mocks. */
  @AfterEach
  void tearDown() {
    mockedKernelUtils.close();
    mockedModelProvider.close();
    mockedApplicationUtils.close();
  }

  /**
   * Adds a visible view field of the tab, shown in the grid and backed by {@code property}.
   *
   * @param sequence     field sequence number
   * @param recordSortNo record sort number, or null
   * @param property     property of its column, or null
   * @return the field, to adjust it in a test
   */
  private Field addField(long sequence, Long recordSortNo, Property property) {
    Field field = mock(Field.class);
    Column column = mock(Column.class);
    when(field.getId()).thenReturn("FIELD_" + sequence);
    when(field.getSequenceNumber()).thenReturn(sequence);
    when(field.getColumn()).thenReturn(column);
    when(field.isActive()).thenReturn(true);
    when(field.isDisplayed()).thenReturn(true);
    when(field.isShowInGridView()).thenReturn(true);
    when(field.isShownInStatusBar()).thenReturn(false);
    when(field.getRecordSortNo()).thenReturn(recordSortNo);
    when(column.isActive()).thenReturn(true);
    when(kernelUtils.getPropertyFromColumn(column, false)).thenReturn(property);
    fields.add(field);
    return field;
  }

  /**
   * Builds a property.
   *
   * @param name       property name
   * @param length     field length
   * @param identifier whether it is an identifier of its entity
   * @return the property
   */
  private static Property property(String name, int length, boolean identifier) {
    Property property = mock(Property.class);
    when(property.getName()).thenReturn(name);
    when(property.getFieldLength()).thenReturn(length);
    when(property.isIdentifier()).thenReturn(identifier);
    return property;
  }

  /**
   * Tests that a tab with an HQL order-by clause has no sort field: the clause is sent as {@code _orderBy}.
   */
  @Test
  void resolveReturnsNoSortFieldWithOrderByClause() {
    when(tab.getHqlorderbyclause()).thenReturn(MOVEMENT_DATE);
    addField(10, 1L, property(STORAGE_BIN, SHORT_LENGTH, false));

    assertEquals(NO_SORT_FIELD, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that the last record sort field in sequence order wins over the lowest record sort number, as in
   * Classic (Product › Transactions sorts by Movement Date, not by Storage Bin).
   */
  @Test
  void resolveTakesTheLastRecordSortField() {
    addField(20, 2L, property(MOVEMENT_DATE, SHORT_LENGTH, false));
    addField(10, 1L, property(STORAGE_BIN, SHORT_LENGTH, false));

    assertEquals(MOVEMENT_DATE, TabDefaultSort.resolve(tab));
  }

  /**
   * Provides changes that make a record sort field not a candidate.
   *
   * @return the (description, change) pairs
   */
  static Stream<Arguments> excludedRecordSortFields() {
    return Stream.of(
        Arguments.of("hidden in the grid", (Consumer<Field>) field -> when(field.isShowInGridView()).thenReturn(false)),
        Arguments.of("shown in the status bar", (Consumer<Field>) field -> when(field.isShownInStatusBar()).thenReturn(true)),
        Arguments.of("inactive", (Consumer<Field>) field -> when(field.isActive()).thenReturn(false)));
  }

  /**
   * Tests that a record sort field that is not a grid candidate is skipped and the identifier is used.
   *
   * @param description why the field is excluded
   * @param change      the change applied to the record sort field
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("excludedRecordSortFields")
  void resolveSkipsRecordSortFieldsThatAreNotCandidates(String description, Consumer<Field> change) {
    change.accept(addField(10, 1L, property(STORAGE_BIN, SHORT_LENGTH, false)));
    addField(20, null, property(NAME, SHORT_LENGTH, true));

    assertEquals(NAME, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that long fields and UI buttons are never the record sort field.
   */
  @Test
  void resolveSkipsLongFieldsAndButtons() {
    addField(10, 1L, property(STORAGE_BIN, TabDefaultSort.MAX_SORT_FIELD_LENGTH, false));
    Field button = addField(20, 2L, property(MOVEMENT_DATE, SHORT_LENGTH, false));
    mockedApplicationUtils.when(() -> ApplicationUtils.isUIButton(button)).thenReturn(true);

    assertEquals(NO_SORT_FIELD, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that, without record sort fields, the sequence number property wins over the line number one.
   */
  @Test
  void resolveUsesSequenceNumberBeforeLineNo() {
    when(entity.hasProperty(Tab.PROPERTY_SEQUENCENUMBER)).thenReturn(true);
    when(entity.hasProperty(OrderLine.PROPERTY_LINENO)).thenReturn(true);
    addField(10, null, property(NAME, SHORT_LENGTH, true));

    assertEquals(Tab.PROPERTY_SEQUENCENUMBER, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that the line number property is used when the entity has no sequence number.
   */
  @Test
  void resolveUsesLineNoWithoutSequenceNumber() {
    when(entity.hasProperty(OrderLine.PROPERTY_LINENO)).thenReturn(true);
    addField(10, null, property(NAME, SHORT_LENGTH, true));

    assertEquals(OrderLine.PROPERTY_LINENO, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that the first identifier field in sequence order is used when nothing else applies, and that
   * fields without a property are skipped.
   */
  @Test
  void resolveUsesTheFirstIdentifierField() {
    addField(30, null, property(STORAGE_BIN, SHORT_LENGTH, true));
    addField(10, null, null);
    addField(20, null, property(NAME, SHORT_LENGTH, true));

    assertEquals(NAME, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that a tab without candidates, sequence properties nor identifiers has no sort field.
   */
  @Test
  void resolveReturnsNoSortFieldWhenNothingApplies() {
    addField(10, null, property(MOVEMENT_DATE, SHORT_LENGTH, false));

    assertEquals(NO_SORT_FIELD, TabDefaultSort.resolve(tab));
  }

  /**
   * Tests that a table without a model entity skips the sequence properties.
   */
  @Test
  void resolveSkipsSequencePropertiesWithoutEntity() {
    when(ModelProvider.getInstance().getEntityByTableId(TABLE_ID)).thenReturn(null);
    addField(10, null, property(NAME, SHORT_LENGTH, true));

    assertEquals(NAME, TabDefaultSort.resolve(tab));
  }
}
