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

import javax.enterprise.event.Observes;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.metadata.builders.SessionBuilder;

/**
 * Observes changes to the entities that make up a user's role/organization/warehouse
 * tree (role assignment, role-organization access, and organization-warehouse links,
 * plus the role/organization/warehouse/client records themselves) and clears the
 * {@link SessionBuilder} roles cache so it gets rebuilt on the next session request.
 * <p>
 * Follows the same pattern as {@link MetadataCacheInvalidationObserver}.
 * The base class {@link EntityPersistenceEventObserver#isValidEvent} already
 * skips events during bulk imports (when TriggerHandler is disabled).
 */
class SessionCacheInvalidationObserver extends EntityPersistenceEventObserver {

  private static final Entity[] entities = {
      ModelProvider.getInstance().getEntity("ADUserRoles"),
      ModelProvider.getInstance().getEntity("ADRoleOrganization"),
      ModelProvider.getInstance().getEntity("OrganizationWarehouse"),
      ModelProvider.getInstance().getEntity("ADRole"),
      ModelProvider.getInstance().getEntity("Organization"),
      ModelProvider.getInstance().getEntity("Warehouse"),
      ModelProvider.getInstance().getEntity("ADClient")
  };

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    SessionBuilder.clearRolesCache();
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    SessionBuilder.clearRolesCache();
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    SessionBuilder.clearRolesCache();
  }

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }
}
