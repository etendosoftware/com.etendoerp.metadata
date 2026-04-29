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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;

import org.openbravo.dal.core.OBContext;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

/**
 * Builds a JSON representation of a window including its tabs and role-based access permissions.
 */
public class WindowBuilder extends Builder {
    private static final Map<String, Boolean> tabAllowedCache = new ConcurrentHashMap<>();
    private final String id;

    /**
     * Creates a new WindowBuilder for the window with the given ID.
     *
     * @param id the database ID of the window to build
     */
    public WindowBuilder(String id) {
        this.id = id;
    }

    private static WindowAccess getWindowAccess(String id) {
        OBDal dal = OBDal.getReadOnlyInstance();
        Window adWindow = dal.get(Window.class, id);

        if (adWindow == null) {
            throw new NotFoundException("Window with ID " + id + " not found.");
        }

        Role role = OBContext.getOBContext().getRole();
        WindowAccess windowAccess = (WindowAccess) dal.createCriteria(WindowAccess.class) //
                .add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role)) //
                .add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true)) //
                .add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow))//
                .setMaxResults(1) //
                .uniqueResult();

        if (windowAccess == null) {
            boolean windowExistsForSomeRole = dal.createCriteria(WindowAccess.class) //
                    .add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true)) //
                    .add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow)) //
                    .setMaxResults(1) //
                    .uniqueResult() != null;

            if (!windowExistsForSomeRole) {
                throw new UnauthorizedException("Role " + role.getName() + " is not authorized for window " + id);
            }

            // Role has no explicit access configured: signal implicit read-only access
            return null;
        }

        return windowAccess;
    }

    private static JSONArray createTabsJson(List<TabAccess> tabAccesses, List<Tab> tabs, boolean isWindowReadOnly) {
        final List<JSONObject> result = new ArrayList<>();
        final Set<String> processedTabIds = new HashSet<>();

        for (TabAccess tabAccess : tabAccesses) {
            Tab tab = tabAccess.getTab();
            if (tabAccess.isActive() && tabAccess.isAllowRead() && isTabAllowedCached(tab)) {
                result.add(new TabBuilder(tab, tabAccess, isWindowReadOnly).toJSON());
                processedTabIds.add(tab.getId());
            }
        }

        for (Tab tab : tabs) {
            if (!processedTabIds.contains(tab.getId()) && isTabAllowedCached(tab)) {
                result.add(new TabBuilder(tab, null, isWindowReadOnly).toJSON());
            }
        }

        return new JSONArray(result);
    }

    /**
     * Clears the cache used to store whether a tab is allowed for the current context.
     * This forces a re-evaluation of tab access permissions on subsequent requests.
     */
    public static void clearTabAllowedCache() {
        tabAllowedCache.clear();
    }

    private static boolean isTabAllowedCached(Tab tab) {
        // Implement proper displayLogic evaluation with parent tab context
        return tabAllowedCache.computeIfAbsent(tab.getId(), id -> true);
    }

    public JSONObject toJSON() {
        WindowAccess windowAccess = getWindowAccess(id);
        boolean isReadOnly = (windowAccess == null) || !windowAccess.isEditableField();

        Window window = windowAccess != null
                ? windowAccess.getWindow()
                : OBDal.getReadOnlyInstance().get(Window.class, id);

        List<TabAccess> tabAccesses = windowAccess != null
                ? windowAccess.getADTabAccessList()
                : Collections.emptyList();

        JSONObject windowJson = converter.toJsonObject(window, DataResolvingMode.FULL_TRANSLATABLE);

        try {
            windowJson.put("id", window.getId());
            windowJson.put("tabs", createTabsJson(tabAccesses, window.getADTabList(), isReadOnly));
        } catch (JSONException e) {
            logger.error("Error creating JSON for window tabs: {}", e.getMessage(), e);
        }

        return windowJson;
    }
}
