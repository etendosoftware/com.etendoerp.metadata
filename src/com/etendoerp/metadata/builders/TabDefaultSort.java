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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.ApplicationUtils;
import org.openbravo.client.application.window.OBViewFieldHandler;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.order.OrderLine;

/**
 * Resolves the property a tab grid is sorted by when the tab has no order-by clause, with the same rules as
 * Etendo Classic ({@code OBViewGridComponent.getSortField}), so both UIs open every tab in the same order:
 * <ol>
 * <li>a tab with an HQL order-by clause has no sort field (the clause is sent as {@code _orderBy});</li>
 * <li>otherwise the <em>last</em> view field, in sequence order, with a record sort number, shown in the grid
 * and shorter than {@value #MAX_SORT_FIELD_LENGTH}. Classic never updates its lowest sort number while
 * looping, so the last qualifying field wins, not the lowest one: kept as is for parity;</li>
 * <li>otherwise the {@code sequenceNumber} or {@code lineNo} property of the entity;</li>
 * <li>otherwise the first view field whose property is an identifier;</li>
 * <li>otherwise none (empty string).</li>
 * </ol>
 * Classic also hides a field whose server-evaluated display logic is false; no field with a record sort
 * number uses that logic, so it is not evaluated here.
 */
public final class TabDefaultSort {

  /** Key of the default sort property in the tab JSON. */
  public static final String DEFAULT_SORT_FIELD = "defaultSortField";

  /** Fields this long or longer are never the sort field (Classic {@code viewField.getLength() < 2000}). */
  static final int MAX_SORT_FIELD_LENGTH = 2000;
  private static final String NO_SORT_FIELD = "";

  private TabDefaultSort() {
  }

  /**
   * Resolves the default sort property of a tab.
   *
   * @param tab the tab whose grid is sorted
   * @return the property name, or an empty string when the tab has an order-by clause or nothing applies
   */
  public static String resolve(Tab tab) {
    if (StringUtils.isNotEmpty(tab.getHqlorderbyclause())) {
      return NO_SORT_FIELD;
    }
    List<Property> viewProperties = new ArrayList<>();
    Property recordSortProperty = null;
    for (Field field : getViewFields(tab)) {
      Property property = KernelUtils.getInstance().getPropertyFromColumn(field.getColumn(), false);
      viewProperties.add(property);
      if (isRecordSortField(field, property)) {
        recordSortProperty = property;
      }
    }
    if (recordSortProperty != null) {
      return recordSortProperty.getName();
    }
    String sequenceProperty = findSequenceProperty(tab);
    if (sequenceProperty != null) {
      return sequenceProperty;
    }
    return findIdentifierProperty(viewProperties);
  }

  /**
   * Fields Classic turns into grid/form view fields ({@code OBViewFieldHandler.getFields}), in sequence order.
   *
   * @param tab the tab
   * @return the view fields of the tab
   */
  static List<Field> getViewFields(Tab tab) {
    List<Field> fields = new ArrayList<>(tab.getADFieldList());
    fields.sort(new OBViewFieldHandler.FormFieldComparator());
    fields.removeIf(field -> !isViewField(field));
    return fields;
  }

  /**
   * Whether Classic shows the field as a view field backed by a column.
   *
   * @param field the field
   * @return true when the field is active, visible in the form or grid, not a UI button nor a status bar field
   */
  static boolean isViewField(Field field) {
    Column column = field.getColumn();
    if (column == null || !isTrue(field.isActive()) || !isTrue(column.isActive())) {
      return false;
    }
    boolean visible = isTrue(field.isDisplayed()) || isTrue(field.isShowInGridView());
    return visible && !ApplicationUtils.isUIButton(field) && !isTrue(field.isShownInStatusBar());
  }

  /**
   * Whether the field can be the record sort field: it has a sort number, is shown in the grid and is short.
   *
   * @param field    the view field
   * @param property its property, if any
   * @return true when Classic takes it as a sort field candidate
   */
  static boolean isRecordSortField(Field field, Property property) {
    return field.getRecordSortNo() != null && isTrue(field.isShowInGridView()) && property != null
        && property.getFieldLength() < MAX_SORT_FIELD_LENGTH;
  }

  /**
   * The sequence number or line number property of the tab entity, the two examples Classic checks.
   *
   * @param tab the tab
   * @return the property name, or null when the entity has neither
   */
  static String findSequenceProperty(Tab tab) {
    Entity entity = ModelProvider.getInstance().getEntityByTableId(tab.getTable().getId());
    if (entity == null) {
      return null;
    }
    if (entity.hasProperty(Tab.PROPERTY_SEQUENCENUMBER)) {
      return Tab.PROPERTY_SEQUENCENUMBER;
    }
    if (entity.hasProperty(OrderLine.PROPERTY_LINENO)) {
      return OrderLine.PROPERTY_LINENO;
    }
    return null;
  }

  /**
   * The first identifier property among the view field properties.
   *
   * @param viewProperties the properties of the view fields, in sequence order (nulls allowed)
   * @return the property name, or an empty string when none is an identifier
   */
  static String findIdentifierProperty(List<Property> viewProperties) {
    for (Property property : viewProperties) {
      if (property != null && property.isIdentifier()) {
        return property.getName();
      }
    }
    return NO_SORT_FIELD;
  }

  private static boolean isTrue(Boolean value) {
    return Boolean.TRUE.equals(value);
  }
}
