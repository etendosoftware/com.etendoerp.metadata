package com.etendoerp.metadata.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

public class WindowBuilder extends Builder {
    private static final Map<String, Boolean> tabAllowedCache = new ConcurrentHashMap<>();
    private final String id;

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
            throw new UnauthorizedException("Role " + role.getName() + " is not authorized for window " + id);
        }

        return windowAccess;
    }

    private static JSONArray createTabsJson(List<TabAccess> tabAccesses, List<Tab> tabs) {
        final int tabsSize = tabs.size();
        final int tabAccessSize = tabAccesses.size();
        final int max = Math.max(tabAccessSize, tabsSize);
        final List<JSONObject> tabJsonObjects = new ArrayList<>(tabAccessSize + tabsSize);

        for (int i = 0; i < max; i++) {
            if (i < tabAccessSize) {
                TabAccess tabAccess = tabAccesses.get(i);
                JSONObject json = getTabJson(tabAccess, tabAccess.getTab());

                if (json != null) {
                    tabJsonObjects.add(json);
                }
            }
            if (i < tabsSize) {
                Tab tab = tabs.get(i);

                if (isTabAllowedCached(tab)) {
                    tabJsonObjects.add(new TabBuilder(tab, null).toJSON());
                }
            }
        }

        return new JSONArray(tabJsonObjects);
    }

    private static JSONObject getTabJson(TabAccess tabAccess, Tab tab) {
        if (tabAccess.isActive() && tabAccess.isAllowRead() && isTabAllowedCached(tab)) {
            return new TabBuilder(tab, tabAccess).toJSON();
        }

        return null;
    }

    private static boolean isTabAllowedCached(Tab tab) {
        return tabAllowedCache.computeIfAbsent(tab.getId(), id -> {
            String displayLogic = tab.getDisplayLogic();
            return displayLogic == null || displayLogic.trim().isEmpty();
        });
    }

    public JSONObject toJSON() {
        WindowAccess windowAccess = getWindowAccess(id);
        Window window = windowAccess.getWindow();
        JSONObject windowJson = converter.toJsonObject(window, DataResolvingMode.FULL_TRANSLATABLE);

        try {
            windowJson.put("tabs", createTabsJson(windowAccess.getADTabAccessList(), window.getADTabList()));
        } catch (JSONException e) {
            logger.error("Error creating JSON for window tabs: {}", e.getMessage(), e);
        }

        return windowJson;
    }
}
