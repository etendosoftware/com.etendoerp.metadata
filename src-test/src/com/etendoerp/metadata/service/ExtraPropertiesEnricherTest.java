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

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.utils.Constants;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExtraPropertiesEnricher}.
 *
 * <p>Each test clears the internal cache via {@link ExtraPropertiesEnricher#clearCache()} in
 * {@code tearDown} to ensure isolation between test cases.</p>
 */
public class ExtraPropertiesEnricherTest {

    @After
    public void tearDown() {
        ExtraPropertiesEnricher.clearCache();
    }

    /**
     * When the entity name cannot be resolved by {@link ModelProvider}, the result must be
     * an empty string.
     */
    @Test
    public void getExtraPropertiesReturnsEmptyWhenEntityNotFound() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);
            when(mp.getEntity("UnknownEntity", false)).thenReturn(null);

            String result = ExtraPropertiesEnricher.getExtraProperties("UnknownEntity");

            assertEquals("", result);
        }
    }

    /**
     * When the entity has no FK properties pointing to entities with a Color column, the
     * result must be an empty string.
     */
    @Test
    public void getExtraPropertiesReturnsEmptyWhenNoFkHasColorReference() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);

            Property primitiveProp = mock(Property.class);
            when(primitiveProp.isOneToMany()).thenReturn(false);
            when(primitiveProp.isId()).thenReturn(false);
            when(primitiveProp.isPrimitive()).thenReturn(true);

            Entity entity = mock(Entity.class);
            when(entity.getProperties()).thenReturn(Collections.singletonList(primitiveProp));
            when(mp.getEntity("ETASK_SomeEntity", false)).thenReturn(entity);

            String result = ExtraPropertiesEnricher.getExtraProperties("ETASK_SomeEntity");

            assertEquals("", result);
        }
    }

    /**
     * When an FK property points to an entity with a Color-typed column, the returned string
     * must be {@code "<fkName>.<colorName>"}.
     */
    @Test
    public void getExtraPropertiesReturnsFkPathWhenTargetEntityHasColorProperty() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);

            Entity referencedEntity = mockEntityWithColorProperty("color");
            Property fkProp = mockFkProperty("priority", referencedEntity);

            Entity entity = mock(Entity.class);
            when(entity.getProperties()).thenReturn(Collections.singletonList(fkProp));
            when(mp.getEntity("ETASK_TaskType", false)).thenReturn(entity);

            String result = ExtraPropertiesEnricher.getExtraProperties("ETASK_TaskType");

            assertEquals("priority.color", result);
        }
    }

    /**
     * When multiple FK properties point to entities with Color columns, all paths must be
     * joined with a comma in the order they appear in the entity's property list.
     */
    @Test
    public void getExtraPropertiesReturnsCombinedPathsForMultipleColorFks() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);

            Property fk1 = mockFkProperty("status", mockEntityWithColorProperty("statusColor"));
            Property fk2 = mockFkProperty("priority", mockEntityWithColorProperty("color"));

            Entity entity = mock(Entity.class);
            when(entity.getProperties()).thenReturn(Arrays.asList(fk1, fk2));
            when(mp.getEntity("ETASK_Task", false)).thenReturn(entity);

            String result = ExtraPropertiesEnricher.getExtraProperties("ETASK_Task");

            assertEquals("status.statusColor,priority.color", result);
        }
    }

    /**
     * FK properties whose {@code targetEntity} is {@code null} must be silently skipped.
     */
    @Test
    public void getExtraPropertiesSkipsFkWithNoTargetEntity() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);

            Property fkProp = mock(Property.class);
            when(fkProp.isOneToMany()).thenReturn(false);
            when(fkProp.isId()).thenReturn(false);
            when(fkProp.isPrimitive()).thenReturn(false);
            when(fkProp.getTargetEntity()).thenReturn(null);

            Entity entity = mock(Entity.class);
            when(entity.getProperties()).thenReturn(Collections.singletonList(fkProp));
            when(mp.getEntity("ETASK_X", false)).thenReturn(entity);

            String result = ExtraPropertiesEnricher.getExtraProperties("ETASK_X");

            assertEquals("", result);
        }
    }

    /**
     * A property whose Color reference ID does not match {@value Constants#COLOR_REFERENCE_ID}
     * must not be included in the result.
     */
    @Test
    public void getExtraPropertiesIgnoresNonColorReferenceId() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);

            org.openbravo.base.model.Reference nonColorRef = mock(org.openbravo.base.model.Reference.class);
            when(nonColorRef.getId()).thenReturn("10"); // String reference, not Color
            DomainType dt = mock(DomainType.class);
            when(dt.getReference()).thenReturn(nonColorRef);
            Property nonColorProp = mock(Property.class);
            when(nonColorProp.isOneToMany()).thenReturn(false);
            when(nonColorProp.isId()).thenReturn(false);
            when(nonColorProp.getDomainType()).thenReturn(dt);

            Entity referencedEntity = mock(Entity.class);
            when(referencedEntity.getProperties()).thenReturn(Collections.singletonList(nonColorProp));

            Property fkProp = mockFkProperty("priority", referencedEntity);

            Entity entity = mock(Entity.class);
            when(entity.getProperties()).thenReturn(Collections.singletonList(fkProp));
            when(mp.getEntity("ETASK_Y", false)).thenReturn(entity);

            String result = ExtraPropertiesEnricher.getExtraProperties("ETASK_Y");

            assertEquals("", result);
        }
    }

    /**
     * Successive calls with the same entity name must hit {@link ModelProvider} only once
     * (result is cached).
     */
    @Test
    public void getExtraPropertiesCachesResult() {
        try (MockedStatic<ModelProvider> mpMock = mockStatic(ModelProvider.class)) {
            ModelProvider mp = mock(ModelProvider.class);
            mpMock.when(ModelProvider::getInstance).thenReturn(mp);
            when(mp.getEntity("ETASK_Cached", false)).thenReturn(null);

            ExtraPropertiesEnricher.getExtraProperties("ETASK_Cached");
            ExtraPropertiesEnricher.getExtraProperties("ETASK_Cached");

            verify(mp, times(1)).getEntity("ETASK_Cached", false);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Entity mockEntityWithColorProperty(String colorName) {
        org.openbravo.base.model.Reference colorRef = mock(org.openbravo.base.model.Reference.class);
        when(colorRef.getId()).thenReturn(Constants.COLOR_REFERENCE_ID);
        DomainType colorDt = mock(DomainType.class);
        when(colorDt.getReference()).thenReturn(colorRef);

        Property colorProp = mock(Property.class);
        when(colorProp.isOneToMany()).thenReturn(false);
        when(colorProp.isId()).thenReturn(false);
        when(colorProp.getDomainType()).thenReturn(colorDt);
        when(colorProp.getName()).thenReturn(colorName);

        Entity entity = mock(Entity.class);
        when(entity.getProperties()).thenReturn(Collections.singletonList(colorProp));
        return entity;
    }

    private static Property mockFkProperty(String name, Entity targetEntity) {
        Property prop = mock(Property.class);
        when(prop.isOneToMany()).thenReturn(false);
        when(prop.isId()).thenReturn(false);
        when(prop.isPrimitive()).thenReturn(false);
        when(prop.getTargetEntity()).thenReturn(targetEntity);
        when(prop.getName()).thenReturn(name);
        return prop;
    }
}
