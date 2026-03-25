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
package com.etendoerp.metadata.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.TriggerHandler;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.data.TabProcessor;

/**
 * Unit tests for {@link MetadataCacheInvalidationObserver}.
 * Verifies that entity persistence events trigger cache invalidation.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class MetadataCacheInvalidationObserverTest {

  private static final String[] OBSERVED_ENTITY_NAMES = {
      "ADWindow", "ADTab", "ADField", "ADColumn",
      "ADWindowAccess", "ADTabAccess", "ADFieldAccess"
  };

  @Test
  void onNewInvalidatesCachesForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      Entity observedEntity = setupMocks(modelProviderMock, triggerMock);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();

      EntityNewEvent event = createNewEvent(observedEntity);
      observer.onNew(event);

      tabProcessorMock.verify(TabProcessor::clearFieldCache, times(1));
      tabProcessorMock.verify(TabProcessor::clearFieldAccessCache, times(1));
      windowBuilderMock.verify(WindowBuilder::clearTabAllowedCache, times(1));
      fieldBuilderMock.verify(FieldBuilderWithColumn::clearWindowAccessCache, times(1));
    }
  }

  @Test
  void onUpdateInvalidatesCachesForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      Entity observedEntity = setupMocks(modelProviderMock, triggerMock);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();

      EntityUpdateEvent event = createUpdateEvent(observedEntity);
      observer.onUpdate(event);

      tabProcessorMock.verify(TabProcessor::clearFieldCache, times(1));
    }
  }

  @Test
  void onDeleteInvalidatesCachesForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      Entity observedEntity = setupMocks(modelProviderMock, triggerMock);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();

      EntityDeleteEvent event = createDeleteEvent(observedEntity);
      observer.onDelete(event);

      tabProcessorMock.verify(TabProcessor::clearFieldCache, times(1));
    }
  }

  @Test
  void onNewDoesNotInvalidateWhenTriggersDisabled() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      Entity observedEntity = setupMocks(modelProviderMock, triggerMock);

      TriggerHandler mockTriggerHandler = TriggerHandler.getInstance();
      when(mockTriggerHandler.isDisabled()).thenReturn(true);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();

      EntityNewEvent event = createNewEvent(observedEntity);
      observer.onNew(event);

      tabProcessorMock.verify(TabProcessor::clearFieldCache, never());
    }
  }

  @Test
  void onNewDoesNotInvalidateForUnobservedEntity() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<TabProcessor> tabProcessorMock = mockStatic(TabProcessor.class);
        MockedStatic<WindowBuilder> windowBuilderMock = mockStatic(WindowBuilder.class);
        MockedStatic<FieldBuilderWithColumn> fieldBuilderMock = mockStatic(FieldBuilderWithColumn.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      Entity unrelatedEntity = mock(Entity.class);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();

      EntityNewEvent event = createNewEvent(unrelatedEntity);
      observer.onNew(event);

      tabProcessorMock.verify(TabProcessor::clearFieldCache, never());
    }
  }

  @Test
  void getObservedEntitiesReturnsSevenEntities() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MetadataCacheInvalidationObserver observer = new MetadataCacheInvalidationObserver();
      Entity[] entities = observer.getObservedEntities();

      assertNotNull(entities);
      assertEquals(OBSERVED_ENTITY_NAMES.length, entities.length);
    }
  }

  /**
   * Sets up ModelProvider and TriggerHandler mocks.
   * Returns the first observed entity mock for use in event creation.
   */
  private Entity setupMocks(MockedStatic<ModelProvider> modelProviderMock,
      MockedStatic<TriggerHandler> triggerMock) {
    ModelProvider mockProvider = mock(ModelProvider.class);
    modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockProvider);

    Entity[] entityMocks = new Entity[OBSERVED_ENTITY_NAMES.length];
    for (int i = 0; i < OBSERVED_ENTITY_NAMES.length; i++) {
      entityMocks[i] = mock(Entity.class);
      when(mockProvider.getEntity(OBSERVED_ENTITY_NAMES[i])).thenReturn(entityMocks[i]);
    }

    TriggerHandler mockTriggerHandler = mock(TriggerHandler.class);
    triggerMock.when(TriggerHandler::getInstance).thenReturn(mockTriggerHandler);
    when(mockTriggerHandler.isDisabled()).thenReturn(false);

    return entityMocks[0];
  }

  private EntityNewEvent createNewEvent(Entity targetEntity) {
    EntityNewEvent event = mock(EntityNewEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }

  private EntityUpdateEvent createUpdateEvent(Entity targetEntity) {
    EntityUpdateEvent event = mock(EntityUpdateEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }

  private EntityDeleteEvent createDeleteEvent(Entity targetEntity) {
    EntityDeleteEvent event = mock(EntityDeleteEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }
}
