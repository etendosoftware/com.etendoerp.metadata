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

package com.etendoerp.metadata.builders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;

/**
 * Unit tests for {@link FieldBuilder#addADListList(Reference)}.
 * <p>
 * The method iterates over a reference's AD List entries and builds a {@link JSONArray}
 * containing {@code id}, {@code value}, and {@code label} for each entry.
 * OBContext is mocked to avoid DAL dependency.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderADListTest {

  @Mock
  private Reference reference;

  @Mock
  private org.openbravo.model.ad.domain.List listEntry;

  @Mock
  private Language language;

  private static final String LIST_ID = "list-id";
  private static final String LIST_KEY = "search-key";
  private static final String LIST_LABEL = "List Label";

  /**
   * When the reference's AD list is empty, the result should be an empty JSONArray.
   */
  @Test
  void addADListListWithEmptyListReturnsEmptyArray() throws Exception {
    when(reference.getADListList()).thenReturn(Collections.emptyList());

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      OBContext mockContext = mock(OBContext.class);
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(language);

      JSONArray result = FieldBuilder.addADListList(reference);

      assertNotNull(result);
      assertEquals(0, result.length());
    }
  }

  /**
   * When the reference has a single AD list entry, the result should contain exactly one
   * JSON object with the correct id, value, and label fields.
   */
  @Test
  void addADListListWithOneEntryReturnsSingleElementWithCorrectFields() throws Exception {
    when(listEntry.getId()).thenReturn(LIST_ID);
    when(listEntry.getSearchKey()).thenReturn(LIST_KEY);
    when(listEntry.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, LIST_ID))
        .thenReturn(LIST_LABEL);
    when(reference.getADListList()).thenReturn(List.of(listEntry));

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      OBContext mockContext = mock(OBContext.class);
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(language);

      JSONArray result = FieldBuilder.addADListList(reference);

      assertNotNull(result);
      assertEquals(1, result.length());

      JSONObject entry = result.getJSONObject(0);
      assertEquals(LIST_ID, entry.getString("id"));
      assertEquals(LIST_KEY, entry.getString("value"));
      assertEquals(LIST_LABEL, entry.getString("label"));
    }
  }

  /**
   * When the reference has multiple AD list entries, each one should be included in the
   * result with its own id, value, and label.
   */
  @Test
  void addADListListWithMultipleEntriesReturnsAllElements() throws Exception {
    org.openbravo.model.ad.domain.List listEntry2 = mock(org.openbravo.model.ad.domain.List.class);
    String id2 = "list-id-2";
    String key2 = "search-key-2";
    String label2 = "List Label 2";

    when(listEntry.getId()).thenReturn(LIST_ID);
    when(listEntry.getSearchKey()).thenReturn(LIST_KEY);
    when(listEntry.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, LIST_ID))
        .thenReturn(LIST_LABEL);

    when(listEntry2.getId()).thenReturn(id2);
    when(listEntry2.getSearchKey()).thenReturn(key2);
    when(listEntry2.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, id2))
        .thenReturn(label2);

    when(reference.getADListList()).thenReturn(List.of(listEntry, listEntry2));

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      OBContext mockContext = mock(OBContext.class);
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(language);

      JSONArray result = FieldBuilder.addADListList(reference);

      assertNotNull(result);
      assertEquals(2, result.length());

      assertEquals(LIST_ID, result.getJSONObject(0).getString("id"));
      assertEquals(id2, result.getJSONObject(1).getString("id"));
    }
  }

  /**
   * Each JSON object in the result must contain the three mandatory keys: id, value, label.
   */
  @Test
  void addADListListEntryContainsMandatoryKeys() throws Exception {
    when(listEntry.getId()).thenReturn(LIST_ID);
    when(listEntry.getSearchKey()).thenReturn(LIST_KEY);
    when(listEntry.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, LIST_ID))
        .thenReturn(LIST_LABEL);
    when(reference.getADListList()).thenReturn(List.of(listEntry));

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      OBContext mockContext = mock(OBContext.class);
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(language);

      JSONArray result = FieldBuilder.addADListList(reference);

      JSONObject entry = result.getJSONObject(0);
      assertTrue(entry.has("id"), "Entry must have 'id' key");
      assertTrue(entry.has("value"), "Entry must have 'value' key");
      assertTrue(entry.has("label"), "Entry must have 'label' key");
    }
  }

  /**
   * When a label is null (e.g. no translation available), the method should still produce
   * a valid JSON object without throwing.
   */
  @Test
  void addADListListWithNullLabelDoesNotThrow() {
    when(listEntry.getId()).thenReturn(LIST_ID);
    when(listEntry.getSearchKey()).thenReturn(LIST_KEY);
    when(listEntry.get(org.openbravo.model.ad.domain.List.PROPERTY_NAME, language, LIST_ID))
        .thenReturn(null);
    when(reference.getADListList()).thenReturn(List.of(listEntry));

    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      OBContext mockContext = mock(OBContext.class);
      obContextMock.when(OBContext::getOBContext).thenReturn(mockContext);
      when(mockContext.getLanguage()).thenReturn(language);

      assertDoesNotThrow(() -> FieldBuilder.addADListList(reference));
    }
  }
}
