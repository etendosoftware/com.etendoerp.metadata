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

import com.etendoerp.metadata.builders.MenuBuilder;

/**
 * Unit tests for {@link MenuCacheInvalidationObserver}.
 * Verifies that persistence events on menu-composing entities invalidate the menu cache.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class MenuCacheInvalidationObserverTest {

  private static final String[] OBSERVED_TABLE_IDS = {
      "116", "289",
      "105", "79127717F4514B459D9014C91E793CE9", "376", "284", "FF80818132D7FB620132D8129D1A0028",
      "201", "E6F29F8A30BC4603B1D1195051C4F3A6", "378", "197", "FF80818132D85DB50132D860924E0004",
      "800148", "800149"
  };

  @Test
  void onNewInvalidatesMenuCacheForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<MenuBuilder> menuBuilderMock = mockStatic(MenuBuilder.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();
      Entity observedEntity = observer.getObservedEntities()[0];

      observer.onNew(createNewEvent(observedEntity));

      menuBuilderMock.verify(MenuBuilder::clearMenuCache, times(1));
    }
  }

  @Test
  void onUpdateInvalidatesMenuCacheForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<MenuBuilder> menuBuilderMock = mockStatic(MenuBuilder.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();
      Entity observedEntity = observer.getObservedEntities()[0];

      observer.onUpdate(createUpdateEvent(observedEntity));

      menuBuilderMock.verify(MenuBuilder::clearMenuCache, times(1));
    }
  }

  @Test
  void onDeleteInvalidatesMenuCacheForValidEvent() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<MenuBuilder> menuBuilderMock = mockStatic(MenuBuilder.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();
      Entity observedEntity = observer.getObservedEntities()[0];

      observer.onDelete(createDeleteEvent(observedEntity));

      menuBuilderMock.verify(MenuBuilder::clearMenuCache, times(1));
    }
  }

  @Test
  void onNewDoesNotInvalidateWhenTriggersDisabled() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<MenuBuilder> menuBuilderMock = mockStatic(MenuBuilder.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);
      when(TriggerHandler.getInstance().isDisabled()).thenReturn(true);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();
      Entity observedEntity = observer.getObservedEntities()[0];

      observer.onNew(createNewEvent(observedEntity));

      menuBuilderMock.verify(MenuBuilder::clearMenuCache, never());
    }
  }

  @Test
  void onNewDoesNotInvalidateForUnobservedEntity() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class);
        MockedStatic<MenuBuilder> menuBuilderMock = mockStatic(MenuBuilder.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();

      observer.onNew(createNewEvent(mock(Entity.class)));

      menuBuilderMock.verify(MenuBuilder::clearMenuCache, never());
    }
  }

  @Test
  void getObservedEntitiesReturnsAllMenuComposingEntities() {
    try (
        MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class);
        MockedStatic<TriggerHandler> triggerMock = mockStatic(TriggerHandler.class)
    ) {
      setupMocks(modelProviderMock, triggerMock);

      MenuCacheInvalidationObserver observer = new MenuCacheInvalidationObserver();
      Entity[] entities = observer.getObservedEntities();

      assertNotNull(entities);
      assertEquals(OBSERVED_TABLE_IDS.length, entities.length);
    }
  }

  /**
   * Sets up ModelProvider (returning a distinct Entity mock per observed table id) and
   * TriggerHandler (enabled) mocks.
   *
   * @param modelProviderMock The mocked static ModelProvider.
   * @param triggerMock       The mocked static TriggerHandler.
   */
  private void setupMocks(MockedStatic<ModelProvider> modelProviderMock,
      MockedStatic<TriggerHandler> triggerMock) {
    ModelProvider mockProvider = mock(ModelProvider.class);
    modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockProvider);

    for (String tableId : OBSERVED_TABLE_IDS) {
      when(mockProvider.getEntityByTableId(tableId)).thenReturn(mock(Entity.class));
    }

    TriggerHandler mockTriggerHandler = mock(TriggerHandler.class);
    triggerMock.when(TriggerHandler::getInstance).thenReturn(mockTriggerHandler);
    when(mockTriggerHandler.isDisabled()).thenReturn(false);
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
