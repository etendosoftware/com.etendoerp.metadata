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

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.I18NComponent;
import org.openbravo.dal.core.OBContext;

/**
 * Simplified unit tests for LabelsBuilder class.
 * Tests the basic internationalization labels functionality.
 */
@ExtendWith(MockitoExtension.class)
class LabelsBuilderSimpleTest {

    @Mock
    private I18NComponent mockI18NComponent;

    @Mock
    private I18NComponent.Label mockLabel1;

    @Mock
    private I18NComponent.Label mockLabel2;

    private LabelsBuilder createLabelsBuilder() {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class)) {
            OBContext mockContext = mock(OBContext.class);
            org.openbravo.model.ad.system.Language mockLanguage = mock(org.openbravo.model.ad.system.Language.class);
            
            mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockContext.getLanguage()).thenReturn(mockLanguage);
            
            return new LabelsBuilder();
        }
    }

    /**
     * Tests that LabelsBuilder extends Builder correctly.
     */
    @Test
    void shouldExtendBuilder() {
        LabelsBuilder builder = createLabelsBuilder();
        assertTrue(builder instanceof Builder);
    }

    /**
     * Tests toJSON with multiple labels returns correct JSON structure.
     */
    @Test
    void toJSONWithLabelsShouldReturnCorrectJSON() throws Exception {
        LabelsBuilder labelsBuilder = createLabelsBuilder();

        // Setup mock labels
        when(mockLabel1.getKey()).thenReturn("label.save");
        when(mockLabel1.getValue()).thenReturn("Save");
        
        when(mockLabel2.getKey()).thenReturn("label.cancel");
        when(mockLabel2.getValue()).thenReturn("Cancel");

        List<I18NComponent.Label> labels = new ArrayList<>();
        labels.add(mockLabel1);
        labels.add(mockLabel2);

        when(mockI18NComponent.getLabels()).thenReturn(labels);

        try (MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {
            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                .thenReturn(mockI18NComponent);

            JSONObject result = labelsBuilder.toJSON();

            assertNotNull(result);
            assertEquals(2, result.length());
            assertEquals("Save", result.getString("label.save"));
            assertEquals("Cancel", result.getString("label.cancel"));
        }
    }

    /**
     * Tests toJSON with empty labels list returns empty JSON object.
     */
    @Test
    void toJSONWithEmptyLabelsShouldReturnEmptyJSON() throws Exception {
        LabelsBuilder labelsBuilder = createLabelsBuilder();

        List<I18NComponent.Label> emptyLabels = new ArrayList<>();
        when(mockI18NComponent.getLabels()).thenReturn(emptyLabels);

        try (MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {
            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                .thenReturn(mockI18NComponent);

            JSONObject result = labelsBuilder.toJSON();

            assertNotNull(result);
            assertEquals(0, result.length());
        }
    }

    /**
     * Tests toJSON handles WeldUtils exception gracefully.
     */
    @Test
    void toJSONWithWeldUtilsExceptionShouldThrow() throws Exception {
        LabelsBuilder labelsBuilder = createLabelsBuilder();

        try (MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {
            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                .thenReturn(null);

            assertThrows(Exception.class, () -> {
                labelsBuilder.toJSON();
            });
        }
    }

    /**
     * Tests toJSON with special characters in labels.
     */
    @Test
    void toJSONWithSpecialCharactersShouldWork() throws Exception {
        LabelsBuilder labelsBuilder = createLabelsBuilder();

        when(mockLabel1.getKey()).thenReturn("label.special");
        when(mockLabel1.getValue()).thenReturn("Acentos: áéíóú");

        List<I18NComponent.Label> labels = new ArrayList<>();
        labels.add(mockLabel1);

        when(mockI18NComponent.getLabels()).thenReturn(labels);

        try (MockedStatic<WeldUtils> mockedWeldUtils = mockStatic(WeldUtils.class)) {
            mockedWeldUtils.when(() -> WeldUtils.getInstanceFromStaticBeanManager(I18NComponent.class))
                .thenReturn(mockI18NComponent);

            JSONObject result = labelsBuilder.toJSON();

            assertNotNull(result);
            assertEquals("Acentos: áéíóú", result.getString("label.special"));
        }
    }
}