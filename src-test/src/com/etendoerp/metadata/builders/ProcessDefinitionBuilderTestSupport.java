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

import static com.etendoerp.metadata.MetadataTestConstants.CONVERTER;
import static com.etendoerp.metadata.MetadataTestConstants.COULD_NOT_SET_CONVERTER_FIELD;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.service.json.DataToJsonConverter;

/**
 * Shared test scaffolding for {@link ProcessDefinitionBuilder} test classes.
 * Centralises the OBContext static mocking lifecycle and the reflection-based
 * converter injection so individual test classes only declare the fixtures and
 * assertions they actually exercise.
 */
abstract class ProcessDefinitionBuilderTestSupport {

  @Mock
  protected Process mockProcess;

  @Mock
  protected DataToJsonConverter mockConverter;

  @Mock
  protected OBContext mockOBContext;

  @Mock
  protected Language mockLanguage;

  private MockedStatic<OBContext> mockedOBContextStatic;

  @BeforeEach
  void initOBContextStaticMock() {
    mockedOBContextStatic = mockStatic(OBContext.class);
    when(mockOBContext.getLanguage()).thenReturn(mockLanguage);
    when(mockLanguage.getLanguage()).thenReturn("en_US");
    mockedOBContextStatic.when(OBContext::getOBContext).thenReturn(mockOBContext);
  }

  @AfterEach
  void closeOBContextStaticMock() {
    if (mockedOBContextStatic != null) {
      mockedOBContextStatic.close();
    }
  }

  /**
   * Injects {@link #mockConverter} into the private {@code converter} field of
   * the supplied builder, failing the test if reflection cannot set it.
   *
   * @param builder The builder instance whose converter field must be replaced.
   */
  protected void injectConverter(ProcessDefinitionBuilder builder) {
    injectConverter(builder, mockConverter);
  }

  /**
   * Injects an arbitrary converter (including {@code null}) into the private
   * {@code converter} field of the supplied builder.
   *
   * @param builder   The builder instance whose converter field must be replaced.
   * @param converter The value to assign — may be {@code null} for negative cases.
   */
  protected void injectConverter(ProcessDefinitionBuilder builder, DataToJsonConverter converter) {
    try {
      Field converterField = Builder.class.getDeclaredField(CONVERTER);
      converterField.setAccessible(true);
      converterField.set(builder, converter);
    } catch (Exception e) {
      fail(COULD_NOT_SET_CONVERTER_FIELD + e.getMessage());
    }
  }
}
