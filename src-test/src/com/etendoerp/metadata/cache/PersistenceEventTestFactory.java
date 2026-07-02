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

import org.openbravo.base.model.Entity;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

/**
 * Shared test factory for the persistence events consumed by the cache-invalidation observers.
 * Centralises the mock wiring ({@code event.getTargetInstance().getEntity() == targetEntity})
 * used by both {@link MetadataCacheInvalidationObserverTest} and
 * {@link MenuCacheInvalidationObserverTest}.
 */
final class PersistenceEventTestFactory {

  private PersistenceEventTestFactory() {
  }

  /**
   * Builds an {@link EntityNewEvent} whose target instance reports the given entity.
   *
   * @param targetEntity The entity the event's target instance must return.
   * @return The mocked new event.
   */
  static EntityNewEvent newEvent(Entity targetEntity) {
    EntityNewEvent event = mock(EntityNewEvent.class);
    wireTargetInstance(event, targetEntity);
    return event;
  }

  /**
   * Builds an {@link EntityUpdateEvent} whose target instance reports the given entity.
   *
   * @param targetEntity The entity the event's target instance must return.
   * @return The mocked update event.
   */
  static EntityUpdateEvent updateEvent(Entity targetEntity) {
    EntityUpdateEvent event = mock(EntityUpdateEvent.class);
    wireTargetInstance(event, targetEntity);
    return event;
  }

  /**
   * Builds an {@link EntityDeleteEvent} whose target instance reports the given entity.
   *
   * @param targetEntity The entity the event's target instance must return.
   * @return The mocked delete event.
   */
  static EntityDeleteEvent deleteEvent(Entity targetEntity) {
    EntityDeleteEvent event = mock(EntityDeleteEvent.class);
    wireTargetInstance(event, targetEntity);
    return event;
  }

  /**
   * Wires the shared {@code getTargetInstance().getEntity()} stub common to every event type.
   *
   * @param event        The mocked persistence event.
   * @param targetEntity The entity the target instance must return.
   */
  private static void wireTargetInstance(EntityPersistenceEvent event, Entity targetEntity) {
    BaseOBObject targetInstance = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(targetInstance);
    when(targetInstance.getEntity()).thenReturn(targetEntity);
  }
}
