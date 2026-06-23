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
package com.etendoerp.metadata.eventhandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.event.Observes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;

/**
 * Propagates {@code active = false} from a parent record to all its direct and indirect
 * child records when the parent is deactivated (active changes from {@code true} to
 * {@code false}).
 *
 * <p>Child relationships are discovered generically via the OB model: any entity property
 * where {@code isParent() == true} and whose {@code targetEntity} matches the deactivated
 * entity is treated as a child link. Propagation is recursive up to {@value #MAX_DEPTH}
 * levels deep.
 *
 * <p>A thread-local guard prevents cascading {@link EntityUpdateEvent}s fired by child
 * saves from triggering redundant propagation cycles during the same flush.
 */
class ActiveFlagPropagationHandler extends EntityPersistenceEventObserver {

  private static final Logger log = LogManager.getLogger(ActiveFlagPropagationHandler.class);
  private static final int MAX_DEPTH = 10;

  /**
   * Tracks composite keys ({@code entityName#recordId}) of records currently being
   * deactivated by this handler within the current thread. Prevents cascading
   * {@link EntityUpdateEvent}s from re-entering propagation for records already handled.
   */
  private static final ThreadLocal<Set<String>> PROPAGATING =
      ThreadLocal.withInitial(HashSet::new);

  /**
   * All entities that have an {@code IsActive} column. Computed lazily on first call and
   * cached per-instance. In production the CDI container creates one handler instance, so
   * this is effectively a one-time build.
   */
  private Entity[] cachedObservedEntities;

  @Override
  protected Entity[] getObservedEntities() {
    if (cachedObservedEntities == null) {
      cachedObservedEntities = buildObservedEntities();
    }
    return cachedObservedEntities;
  }

  /** Builds the entity list from the OB model. Extracted so subclasses can override in tests. */
  Entity[] buildObservedEntities() {
    return ModelProvider.getInstance()
        .getModel()
        .stream()
        .filter(e -> e.getProperties().stream().anyMatch(Property::isActiveColumn))
        .toArray(Entity[]::new);
  }

  /** Fires when any managed entity is updated. */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    BaseOBObject target = event.getTargetInstance();
    Entity entity = target.getEntity();

    Property activeProperty = findActiveProperty(entity);
    if (activeProperty == null) {
      return;
    }

    Boolean newActive = (Boolean) event.getCurrentState(activeProperty);
    Boolean prevActive = (Boolean) event.getPreviousState(activeProperty);

    boolean deactivated = Boolean.FALSE.equals(newActive) && !Boolean.FALSE.equals(prevActive);
    if (!deactivated) {
      return;
    }

    String key = recordKey(entity, target);
    if (PROPAGATING.get().contains(key)) {
      // This record is already being handled by explicit recursion in the same thread.
      return;
    }

    propagate(target, entity, 0);
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  private void propagate(BaseOBObject parent, Entity parentEntity, int depth) {
    if (depth >= MAX_DEPTH) {
      log.warn("Active-flag propagation reached max depth ({}) at entity {}",
          MAX_DEPTH, parentEntity.getName());
      return;
    }

    String parentId = (String) parent.getId();

    for (Entity childEntity : getObservedEntities()) {
      Property childActiveProperty = findActiveProperty(childEntity);
      if (childActiveProperty == null) {
        continue;
      }

      for (Property prop : childEntity.getProperties()) {
        if (!prop.isParent() || !parentEntity.equals(prop.getTargetEntity())) {
          continue;
        }
        deactivateChildren(parentId, childEntity, prop, childActiveProperty, depth);
      }
    }
  }

  private void deactivateChildren(String parentId, Entity childEntity, Property parentProp,
      Property childActiveProperty, int depth) {
    try {
      // OBQuery auto-adds "active = true" filter (filterOnActive defaults to true),
      // so only currently-active children are returned.
      OBQuery<BaseOBObject> query = OBDal.getInstance().createQuery(
          childEntity.getName(),
          parentProp.getName() + ".id = :parentId");
      query.setNamedParameter("parentId", parentId);
      // Bypass org filter: deactivate all children regardless of the current user's
      // readable organisations (consistent with Classic behaviour).
      query.setFilterOnReadableOrganization(false);

      List<BaseOBObject> children = query.list();
      for (BaseOBObject child : children) {
        String childKey = recordKey(childEntity, child);
        PROPAGATING.get().add(childKey);
        try {
          child.set(childActiveProperty.getName(), Boolean.FALSE);
          propagate(child, childEntity, depth + 1);
        } finally {
          PROPAGATING.get().remove(childKey);
        }
      }
    } catch (Exception e) {
      log.warn("Error propagating active=false to {} children of parent {}: {}",
          childEntity.getName(), parentId, e.getMessage(), e);
    }
  }

  private static Property findActiveProperty(Entity entity) {
    return entity.getProperties()
        .stream()
        .filter(Property::isActiveColumn)
        .findFirst()
        .orElse(null);
  }

  private static String recordKey(Entity entity, BaseOBObject bob) {
    return entity.getName() + "#" + bob.getId();
  }
}
