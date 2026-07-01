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

import com.etendoerp.metadata.builders.MenuBuilder;

/**
 * Observes changes to the Application Dictionary entities that compose the navigation menu and
 * invalidates the new-UI cached menu JSON ({@link MenuBuilder#clearMenuCache()}).
 * <p>
 * Mirrors the entity set of the classic {@code MenuCacheHandler} (Menu, TreeNode, Window, View
 * Definition, Form, Process, Process Definition and their access records) so the new-UI menu cache
 * is invalidated in the same situations as the classic {@code GlobalMenu} cache. Additionally it
 * observes Model Object and Model Object Mapping, because the new-UI menu resolves the default
 * mapping URL from them (a case the classic handler does not cover).
 * <p>
 * The base class {@link EntityPersistenceEventObserver#isValidEvent} already skips events during
 * bulk imports (when TriggerHandler is disabled).
 */
class MenuCacheInvalidationObserver extends EntityPersistenceEventObserver {

  private static final String MENU_TABLE_ID = "116";
  private static final String TREENODE_TABLE_ID = "289";

  private static final String WINDOW_TABLE_ID = "105";
  private static final String VIEWDEFINITION_TABLE_ID = "79127717F4514B459D9014C91E793CE9";
  private static final String FORM_TABLE_ID = "376";
  private static final String PROCESS_TABLE_ID = "284";
  private static final String PROCESSDEFINITION_TABLE_ID = "FF80818132D7FB620132D8129D1A0028";

  private static final String WINDOW_ACCESS_TABLE_ID = "201";
  private static final String VIEWDEFINITION_ACCESS_TABLE_ID = "E6F29F8A30BC4603B1D1195051C4F3A6";
  private static final String FORM_ACCESS_TABLE_ID = "378";
  private static final String PROCESS_ACCESS_TABLE_ID = "197";
  private static final String PROCESSDEFINITION_ACCESS_TABLE_ID = "FF80818132D85DB50132D860924E0004";

  private static final String MODEL_OBJECT_TABLE_ID = "800148";
  private static final String MODEL_OBJECT_MAPPING_TABLE_ID = "800149";

  private static final Entity[] entities = {
      ModelProvider.getInstance().getEntityByTableId(MENU_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(TREENODE_TABLE_ID),

      ModelProvider.getInstance().getEntityByTableId(WINDOW_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(VIEWDEFINITION_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(FORM_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(PROCESS_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(PROCESSDEFINITION_TABLE_ID),

      ModelProvider.getInstance().getEntityByTableId(WINDOW_ACCESS_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(VIEWDEFINITION_ACCESS_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(FORM_ACCESS_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(PROCESS_ACCESS_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(PROCESSDEFINITION_ACCESS_TABLE_ID),

      ModelProvider.getInstance().getEntityByTableId(MODEL_OBJECT_TABLE_ID),
      ModelProvider.getInstance().getEntityByTableId(MODEL_OBJECT_MAPPING_TABLE_ID) };

  public void onNew(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MenuBuilder.clearMenuCache();
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MenuBuilder.clearMenuCache();
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    MenuBuilder.clearMenuCache();
  }

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }
}
