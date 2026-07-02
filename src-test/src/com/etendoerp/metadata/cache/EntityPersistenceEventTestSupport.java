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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.TriggerHandler;

/**
 * Shared mocking helpers for {@code EntityPersistenceEventObserver} subclass unit tests
 * (e.g. {@link MetadataCacheInvalidationObserverTest}, {@link SessionCacheInvalidationObserverTest}),
 * avoiding duplicated {@code ModelProvider}/{@code TriggerHandler}/event mocking boilerplate.
 */
final class EntityPersistenceEventTestSupport {

  private EntityPersistenceEventTestSupport() {
  }

  /**
   * Stubs {@link ModelProvider#getInstance()} to return a mock provider resolving each of
   * {@code entityNames} to its own {@link Entity} mock, and {@link TriggerHandler#getInstance()}
   * to a mock with triggers enabled.
   */
  static void setupMocks(MockedStatic<ModelProvider> modelProviderMock,
      MockedStatic<TriggerHandler> triggerMock, String[] entityNames) {
    ModelProvider mockProvider = mock(ModelProvider.class);
    modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockProvider);

    for (String entityName : entityNames) {
      when(mockProvider.getEntity(entityName)).thenReturn(mock(Entity.class));
    }

    TriggerHandler mockTriggerHandler = mock(TriggerHandler.class);
    triggerMock.when(TriggerHandler::getInstance).thenReturn(mockTriggerHandler);
    when(mockTriggerHandler.isDisabled()).thenReturn(false);
  }

  static EntityNewEvent createNewEvent(Entity targetEntity) {
    EntityNewEvent event = mock(EntityNewEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }

  static EntityUpdateEvent createUpdateEvent(Entity targetEntity) {
    EntityUpdateEvent event = mock(EntityUpdateEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }

  static EntityDeleteEvent createDeleteEvent(Entity targetEntity) {
    EntityDeleteEvent event = mock(EntityDeleteEvent.class);
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
    return event;
  }
}
