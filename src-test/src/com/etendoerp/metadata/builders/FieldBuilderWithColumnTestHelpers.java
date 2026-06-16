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

import static com.etendoerp.metadata.MetadataTestConstants.COLUMN_ID;
import static com.etendoerp.metadata.MetadataTestConstants.FIELD_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.mockito.MockedConstruction;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Stateless helpers extracted from {@link FieldBuilderWithColumnTest} to keep
 * that test class under the maximum number of methods enforced by SonarQube.
 * Hosts reflection-based access, common Mockito stubs and shared fixtures that
 * do not depend on the test instance state.
 */
final class FieldBuilderWithColumnTestHelpers {

  private FieldBuilderWithColumnTestHelpers() {
    throw new AssertionError("Utility class - do not instantiate");
  }

  /**
   * Returns a {@link MockedConstruction} that intercepts every
   * {@link DataToJsonConverter} constructed inside the try-with-resources
   * scope and pre-stubs its {@code toJsonObject(Field|Column, ...)} calls with
   * minimal payloads sufficient for the {@code FieldBuilder} pipeline.
   *
   * @return the active MockedConstruction; must be closed by the caller.
   * @throws JSONException if the canned JSON payloads cannot be built.
   */
  static MockedConstruction<DataToJsonConverter> mockDataToJsonConverter() throws JSONException {
    return mockConstruction(DataToJsonConverter.class,
        (mockInstance, context) -> {
          JSONObject base = new JSONObject().put("id", FIELD_ID);
          when(mockInstance.toJsonObject(any(Field.class),
              eq(DataResolvingMode.FULL_TRANSLATABLE)))
              .thenReturn(base);
          when(mockInstance.toJsonObject(any(Column.class),
              eq(DataResolvingMode.FULL_TRANSLATABLE)))
              .thenReturn(new JSONObject().put("id", COLUMN_ID));
        });
  }

  /**
   * Wires {@code obDal} to expose a single-result WindowAccess query for the
   * supplied window identifier. Used by both the cached and uncached access
   * test paths.
   *
   * @param obDal         The OBDal mock that should resolve the window lookup.
   * @param windowId      The window id expected by {@code obDal.get(Window.class, …)}.
   * @param criteriaMock  Pre-built OBCriteria&lt;WindowAccess&gt; mock to return.
   * @param windowAccess  The unique result of the criteria, or {@code null}.
   */
  static void setupWindowAccessMocks(OBDal obDal, String windowId,
      OBCriteria<WindowAccess> criteriaMock, WindowAccess windowAccess) {
    when(obDal.get(Window.class, windowId)).thenReturn(mock(Window.class));
    when(obDal.createCriteria(WindowAccess.class)).thenReturn(criteriaMock);
    when(criteriaMock.add(any())).thenReturn(criteriaMock);
    when(criteriaMock.setMaxResults(1)).thenReturn(criteriaMock);
    when(criteriaMock.uniqueResult()).thenReturn(windowAccess);
  }

  /**
   * Configures {@code obDal} so that table lookups return the supplied table
   * mock and Tab criteria chains return the supplied criteria mock with a
   * null unique result — the default needed by the tab-resolution branches.
   *
   * @param obDal    The OBDal mock that should resolve {@code Table} / criteria calls.
   * @param table    The Table mock returned by {@code obDal.get(Table.class, …)}.
   * @param criteria The OBCriteria&lt;Tab&gt; mock chained via {@code createCriteria(Tab.class)}.
   */
  static void setupOBDalWithTabCriteria(OBDal obDal, Table table, OBCriteria<Tab> criteria) {
    when(obDal.get(eq(Table.class), any())).thenReturn(table);
    when(obDal.createCriteria(Tab.class)).thenReturn(criteria);
    when(criteria.add(any(Criterion.class))).thenReturn(criteria);
    when(criteria.setMaxResults(anyInt())).thenReturn(criteria);
    when(criteria.uniqueResult()).thenReturn(null);
  }

  /**
   * Reflectively invokes a private/package method on the supplied target.
   *
   * @param target          The instance whose method is invoked.
   * @param methodName      The exact method name.
   * @param parameterTypes  The declared parameter types (use empty array for no-arg).
   * @param args            The actual arguments forwarded to the method.
   * @return The value returned by the invoked method, or {@code null} for void.
   * @throws ReflectiveOperationException if the method cannot be located or invoked.
   */
  static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  /**
   * Replaces the private {@code json} field declared on {@link FieldBuilder}
   * with the supplied payload, bypassing the builder pipeline so individual
   * methods can be exercised in isolation.
   *
   * @param builder The FieldBuilder instance to mutate.
   * @param json    The JSON payload to inject.
   * @throws ReflectiveOperationException if the field cannot be accessed.
   */
  static void setJson(FieldBuilder builder, JSONObject json) throws ReflectiveOperationException {
    java.lang.reflect.Field jsonField = FieldBuilder.class.getDeclaredField("json");
    jsonField.setAccessible(true);
    jsonField.set(builder, json);
  }
}
