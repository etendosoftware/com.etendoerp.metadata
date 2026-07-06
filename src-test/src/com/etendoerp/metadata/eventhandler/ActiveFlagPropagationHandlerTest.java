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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.Property;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;

/**
 * Unit tests for {@link ActiveFlagPropagationHandler}.
 *
 * <p>A test-only subclass bypasses both the OB model lookup ({@code buildObservedEntities})
 * and the OB framework's bulk-import guard ({@code isValidEvent}) so the handler logic
 * can be verified in isolation with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiveFlagPropagationHandlerTest {

  private static final String PARENT_ID = "parent-uuid";
  private static final String CHILD_ID  = "child-uuid";
  private static final String ACTIVE    = "active";
  private static final String PARENT_LINK = "parentLink";
  private static final String CHILD_ENTITY_NAME = "ChildEntity";

  @Mock private Entity parentEntity;
  @Mock private Entity childEntity;
  @Mock private Property parentActiveProp;
  @Mock private Property parentLinkProp;
  @Mock private Property childActiveProp;
  @Mock private BaseOBObject parentRecord;
  @Mock private BaseOBObject childRecord;
  @Mock private EntityUpdateEvent event;
  @SuppressWarnings("rawtypes")
  @Mock private OBQuery obQuery;

  /** Handler instance that bypasses OB infrastructure (model + import guard). */
  private ActiveFlagPropagationHandler handler;

  @BeforeEach
  void setUp() {
    // ── Parent entity ────────────────────────────────────────────────────
    when(parentActiveProp.isActiveColumn()).thenReturn(true);
    when(parentActiveProp.getName()).thenReturn(ACTIVE);
    when(parentEntity.getProperties()).thenReturn(List.of(parentActiveProp));

    // ── Child entity ─────────────────────────────────────────────────────
    // parentLinkProp: FK on childEntity pointing to parentEntity (isParent = true)
    when(parentLinkProp.isParent()).thenReturn(true);
    when(parentLinkProp.getTargetEntity()).thenReturn(parentEntity);
    when(parentLinkProp.getName()).thenReturn(PARENT_LINK);

    when(childActiveProp.isActiveColumn()).thenReturn(true);
    when(childActiveProp.getName()).thenReturn(ACTIVE);
    when(childEntity.getProperties()).thenReturn(List.of(parentLinkProp, childActiveProp));
    when(childEntity.getName()).thenReturn(CHILD_ENTITY_NAME);

    // ── Records ──────────────────────────────────────────────────────────
    when(parentRecord.getId()).thenReturn(PARENT_ID);
    when(childRecord.getId()).thenReturn(CHILD_ID);

    // ── Handler: overrides OB-specific infrastructure ────────────────────
    handler = new ActiveFlagPropagationHandler() {
      @Override
      Entity[] buildObservedEntities() {
        return new Entity[]{ parentEntity, childEntity };
      }

      @Override
      protected boolean isValidEvent(EntityPersistenceEvent ev) {
        // Bypass TriggerHandler / OB container check in unit tests.
        Entity observed = ev.getTargetInstance().getEntity();
        for (Entity e : getObservedEntities()) {
          if (e.equals(observed)) return true;
        }
        return false;
      }
    };
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void configureEvent(Boolean prevActive, Boolean newActive) {
    when(event.getTargetInstance()).thenReturn(parentRecord);
    when(parentRecord.getEntity()).thenReturn(parentEntity);
    when(event.getPreviousState(parentActiveProp)).thenReturn(prevActive);
    when(event.getCurrentState(parentActiveProp)).thenReturn(newActive);
  }

  @SuppressWarnings("unchecked")
  private void stubChildQuery(MockedStatic<OBDal> dalMock, List<BaseOBObject> results) {
    OBDal dal = mock(OBDal.class);
    dalMock.when(OBDal::getInstance).thenReturn(dal);
    when(dal.createQuery(anyString(), anyString())).thenReturn(obQuery);
    when(obQuery.setNamedParameter(anyString(), any())).thenReturn(obQuery);
    when(obQuery.setFilterOnReadableOrganization(anyBoolean())).thenReturn(obQuery);
    when(obQuery.list()).thenReturn(results);
  }

  // ── Tests: propagation triggers ──────────────────────────────────────────

  @Test
  void activeYToNPropagatesDeactivationToChildren() {
    configureEvent(Boolean.TRUE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      stubChildQuery(dalMock, List.of(childRecord));

      handler.onUpdate(event);

      verify(childRecord).set(eq(ACTIVE), eq(Boolean.FALSE));
    }
  }

  @Test
  void activeRemainingTrueDoesNotPropagate() {
    configureEvent(Boolean.TRUE, Boolean.TRUE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);

      handler.onUpdate(event);

      verify(dal, never()).createQuery(anyString(), anyString());
    }
  }

  @Test
  void reactivationNToYDoesNotPropagate() {
    configureEvent(Boolean.FALSE, Boolean.TRUE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);

      handler.onUpdate(event);

      verify(dal, never()).createQuery(anyString(), anyString());
    }
  }

  @Test
  void alreadyInactiveParentDoesNotPropagate() {
    // prevActive is already false → not a Y→N transition
    configureEvent(Boolean.FALSE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);

      handler.onUpdate(event);

      verify(dal, never()).createQuery(anyString(), anyString());
    }
  }

  @Test
  void entityNotInObservedListIsSkipped() {
    Entity unknownEntity = mock(Entity.class);
    BaseOBObject unknownRecord = mock(BaseOBObject.class);
    when(event.getTargetInstance()).thenReturn(unknownRecord);
    when(unknownRecord.getEntity()).thenReturn(unknownEntity);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);

      handler.onUpdate(event);

      verify(dal, never()).createQuery(anyString(), anyString());
    }
  }

  // ── Tests: child handling ─────────────────────────────────────────────────

  @Test
  void noChildrenFoundIsNoOp() {
    configureEvent(Boolean.TRUE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      stubChildQuery(dalMock, Collections.emptyList());

      handler.onUpdate(event); // must not throw
    }
  }

  @Test
  void allChildrenAreDeactivated() {
    BaseOBObject child2 = mock(BaseOBObject.class);
    when(child2.getId()).thenReturn("child2-uuid");

    configureEvent(Boolean.TRUE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      stubChildQuery(dalMock, List.of(childRecord, child2));

      handler.onUpdate(event);

      verify(childRecord).set(eq(ACTIVE), eq(Boolean.FALSE));
      verify(child2).set(eq(ACTIVE), eq(Boolean.FALSE));
    }
  }

  @Test
  void queryExceptionIsSwallowedAndHandlerSurvives() {
    configureEvent(Boolean.TRUE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);
      when(dal.createQuery(anyString(), anyString()))
          .thenThrow(new RuntimeException("simulated DB error"));

      handler.onUpdate(event); // must not re-throw
    }
  }

  // ── Tests: recursion ─────────────────────────────────────────────────────

  @Test
  void grandchildrenAreDeactivatedRecursively() {
    // Set up a grandchild entity whose parent is childEntity
    Entity grandchildEntity = mock(Entity.class);
    Property grandchildLinkProp = mock(Property.class);
    Property grandchildActiveProp = mock(Property.class);
    BaseOBObject grandchildRecord = mock(BaseOBObject.class);

    when(grandchildLinkProp.isParent()).thenReturn(true);
    when(grandchildLinkProp.getTargetEntity()).thenReturn(childEntity);
    when(grandchildLinkProp.getName()).thenReturn("childLink");
    when(grandchildActiveProp.isActiveColumn()).thenReturn(true);
    when(grandchildActiveProp.getName()).thenReturn(ACTIVE);
    when(grandchildEntity.getProperties())
        .thenReturn(List.of(grandchildLinkProp, grandchildActiveProp));
    when(grandchildEntity.getName()).thenReturn("GrandchildEntity");
    when(grandchildRecord.getId()).thenReturn("grandchild-uuid");

    // Override handler to include grandchild entity in the model
    handler = new ActiveFlagPropagationHandler() {
      @Override
      Entity[] buildObservedEntities() {
        return new Entity[]{ parentEntity, childEntity, grandchildEntity };
      }

      @Override
      protected boolean isValidEvent(EntityPersistenceEvent ev) {
        Entity observed = ev.getTargetInstance().getEntity();
        for (Entity e : getObservedEntities()) {
          if (e.equals(observed)) return true;
        }
        return false;
      }
    };

    configureEvent(Boolean.TRUE, Boolean.FALSE);

    try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
      OBDal dal = mock(OBDal.class);
      dalMock.when(OBDal::getInstance).thenReturn(dal);

      @SuppressWarnings({ "rawtypes", "unchecked" })
      OBQuery<BaseOBObject> childQuery = mock(OBQuery.class);
      @SuppressWarnings({ "rawtypes", "unchecked" })
      OBQuery<BaseOBObject> grandchildQuery = mock(OBQuery.class);

      // First createQuery call (for childEntity) returns childRecord
      when(dal.createQuery(eq(CHILD_ENTITY_NAME), anyString())).thenReturn(childQuery);
      when(childQuery.setNamedParameter(anyString(), any())).thenReturn(childQuery);
      when(childQuery.setFilterOnReadableOrganization(anyBoolean())).thenReturn(childQuery);
      when(childQuery.list()).thenReturn(List.of(childRecord));

      // Second createQuery call (for grandchildEntity) returns grandchildRecord
      when(dal.createQuery(eq("GrandchildEntity"), anyString())).thenReturn(grandchildQuery);
      when(grandchildQuery.setNamedParameter(anyString(), any())).thenReturn(grandchildQuery);
      when(grandchildQuery.setFilterOnReadableOrganization(anyBoolean())).thenReturn(grandchildQuery);
      when(grandchildQuery.list()).thenReturn(List.of(grandchildRecord));

      handler.onUpdate(event);

      verify(childRecord).set(eq(ACTIVE), eq(Boolean.FALSE));
      verify(grandchildRecord).set(eq(ACTIVE), eq(Boolean.FALSE));
    }
  }
}
