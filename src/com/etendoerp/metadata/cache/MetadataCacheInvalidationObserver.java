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

package com.etendoerp.metadata.cache;

import javax.enterprise.event.Observes;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

/**
 * Observes changes to Application Dictionary entities (Window, Tab, Field, Column,
 * and their access records) and invalidates all metadata caches.
 * <p>
 * Follows the same pattern as MenuCacheHandler in the classic interface.
 * The base class {@link EntityPersistenceEventObserver#isValidEvent} already
 * skips events during bulk imports (when TriggerHandler is disabled).
 */
class MetadataCacheInvalidationObserver extends EntityPersistenceEventObserver {

  private static final Entity[] entities = {
      ModelProvider.getInstance().getEntity("ADWindow"),
      ModelProvider.getInstance().getEntity("ADTab"),
      ModelProvider.getInstance().getEntity("ADField"),
      ModelProvider.getInstance().getEntity("ADColumn"),
      ModelProvider.getInstance().getEntity("ADWindowAccess"),
      ModelProvider.getInstance().getEntity("ADTabAccess"),
      ModelProvider.getInstance().getEntity("ADFieldAccess")
  };

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MetadataCacheManager.invalidateAll();
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MetadataCacheManager.invalidateAll();
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MetadataCacheManager.invalidateAll();
  }

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }
}
