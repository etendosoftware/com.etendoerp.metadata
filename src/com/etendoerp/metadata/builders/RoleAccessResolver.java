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

package com.etendoerp.metadata.builders;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.WindowAccessData;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Resolves the role-based window access configuration (Window Access, Tab Access and Field Access)
 * the same way Classic does in {@code WindowSettingsActionHandler}.
 */
public final class RoleAccessResolver {
  private static final Logger logger = LogManager.getLogger(RoleAccessResolver.class);

  private RoleAccessResolver() {
  }

  /**
   * Returns the active window access of the given role for the given window.
   *
   * @param role   the role whose access is resolved
   * @param window the window to check
   * @return the active window access, or {@code null} if the role has none
   */
  public static WindowAccess findActiveWindowAccess(Role role, Window window) {
    return (WindowAccess) OBDal.getReadOnlyInstance().createCriteria(WindowAccess.class) //
        .add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role)) //
        .add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true)) //
        .add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, window)) //
        .setMaxResults(1) //
        .uniqueResult();
  }

  /**
   * Loads all {@link TabAccess} entries for the given window access, eagerly fetching their
   * associated {@link Tab} in the same round-trip to avoid an N+1 query pattern when the caller
   * iterates and calls {@link TabAccess#getTab()} for every entry.
   *
   * @param windowAccess the window access whose tab accesses should be loaded
   * @return all tab accesses (active and inactive) for the given window access, with tabs
   *         pre-fetched
   */
  public static List<TabAccess> findTabAccesses(WindowAccess windowAccess) {
    Map<String, Object> params = new HashMap<>();
    params.put("windowAccessId", windowAccess.getId());
    return OBDal.getReadOnlyInstance()
        .createQuery(TabAccess.class,
            "as ta left join fetch ta.tab where ta.windowAccess.id = :windowAccessId", params)
        .setFilterOnActive(false)
        .list();
  }

  /**
   * Batch-loads all {@link FieldAccess} entries for the given tab accesses in a single query,
   * eagerly fetching their associated {@link org.openbravo.model.ad.ui.Field}, and groups the
   * result by tab access ID. This avoids the N+1 pattern of calling
   * {@link TabAccess#getADFieldAccessList()} once per tab.
   *
   * @param tabAccesses the tab accesses whose field accesses should be loaded
   * @return a map from tab access ID to its list of field accesses
   */
  public static Map<String, List<FieldAccess>> findFieldAccessesByTabAccessId(List<TabAccess> tabAccesses) {
    if (tabAccesses.isEmpty()) {
      return Collections.emptyMap();
    }

    List<String> tabAccessIds = tabAccesses.stream().map(TabAccess::getId).collect(Collectors.toList());
    Map<String, Object> params = new HashMap<>();
    params.put("tabAccessIds", tabAccessIds);
    List<FieldAccess> fieldAccesses = OBDal.getReadOnlyInstance()
        .createQuery(FieldAccess.class,
            "as fa left join fetch fa.tabAccess left join fetch fa.field where fa.tabAccess.id in (:tabAccessIds)",
            params)
        .setFilterOnActive(false)
        .list();

    return fieldAccesses.stream().collect(Collectors.groupingBy(fa -> fa.getTabAccess().getId()));
  }

  /**
   * Returns the field accesses of the given tab access from an already batch-loaded map.
   *
   * @param tabAccess     the tab access, or {@code null}
   * @param byTabAccessId field accesses grouped by tab access ID
   * @return the field accesses of {@code tabAccess}, or an empty list
   */
  public static List<FieldAccess> fieldAccessesOf(TabAccess tabAccess, Map<String, List<FieldAccess>> byTabAccessId) {
    if (tabAccess == null) {
      return Collections.emptyList();
    }
    return byTabAccessId.getOrDefault(tabAccess.getId(), Collections.emptyList());
  }

  /**
   * Loads the field accesses of a single tab access.
   *
   * @param tabAccess the tab access, or {@code null}
   * @return the field accesses of {@code tabAccess}, or an empty list
   */
  public static List<FieldAccess> findFieldAccesses(TabAccess tabAccess) {
    if (tabAccess == null) {
      return Collections.emptyList();
    }
    return fieldAccessesOf(tabAccess, findFieldAccessesByTabAccessId(List.of(tabAccess)));
  }

  /**
   * Indexes the active tab accesses by the ID of their tab. Inactive rows are ignored, as Classic
   * does.
   *
   * @param tabAccesses the tab accesses to index
   * @return a map from tab ID to its active tab access
   */
  public static Map<String, TabAccess> indexActiveTabAccessesByTabId(List<TabAccess> tabAccesses) {
    Map<String, TabAccess> result = new HashMap<>();
    for (TabAccess tabAccess : tabAccesses) {
      if (Boolean.TRUE.equals(tabAccess.isActive())) {
        result.put(tabAccess.getTab().getId(), tabAccess);
      }
    }
    return result;
  }

  /**
   * Tells whether the tab is read-only for the role of the given window access, reusing Classic's
   * own rule ({@code WindowAccessData.hasReadOnlyAccess}): read-only Table Access, read-only Window
   * Access without an editable Tab Access, or a non-editable Tab Access without editable Field
   * Access.
   *
   * @param windowAccess the active window access of the role, or {@code null} for the implicit
   *                     read-only fallback (no access for this role)
   * @param tab          the tab to check
   * @return {@code true} if the tab must be rendered read-only
   */
  public static boolean isTabReadOnly(WindowAccess windowAccess, Tab tab) {
    if (windowAccess == null) {
      return true;
    }
    try {
      return WindowAccessData.hasReadOnlyAccess(new DalConnectionProvider(false),
          windowAccess.getRole().getId(), tab.getId());
    } catch (ServletException e) {
      // Fail closed: an unknown access state must never expose an editable tab.
      logger.error("Error resolving read-only access for tab {}: {}", tab.getId(), e.getMessage(), e);
      return true;
    }
  }
}
