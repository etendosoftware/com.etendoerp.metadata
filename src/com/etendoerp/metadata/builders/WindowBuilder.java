package com.etendoerp.metadata.builders;

import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.TabAccess;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;

public class WindowBuilder extends Builder {
    private final String id;

    public WindowBuilder(String id) {
        this.id = id;
    }

    public JSONObject toJSON() {
        WindowAccess windowAccess = getWindowAccess();
        Window window = windowAccess.getWindow();
        JSONObject windowJson = converter.toJsonObject(window, DataResolvingMode.FULL_TRANSLATABLE);

        try {
            List<TabAccess> tabAccesses = windowAccess.getADTabAccessList();
            List<Tab> tabs = window.getADTabList();
            JSONArray tabsJson = createTabsJson(tabAccesses, tabs);
            windowJson.put("tabs", tabsJson);
        } catch (JSONException e) {
            logger.error("Error creating JSON for window tabs: {}", e.getMessage(), e);
        }

        return windowJson;
    }

    private WindowAccess getWindowAccess() {
        Window adWindow = OBDal.getInstance().get(Window.class, this.id);
        if (adWindow == null) {
            throw new NotFoundException("Window with ID " + id + " not found.");
        }

        Role role = OBContext.getOBContext().getRole();
        OBCriteria<WindowAccess> criteria = OBDal.getInstance().createCriteria(WindowAccess.class);
        criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        criteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));
        criteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
        criteria.setMaxResults(1);

        WindowAccess windowAccess = (WindowAccess) criteria.uniqueResult();
        if (windowAccess == null) {
            throw new UnauthorizedException("Role " + role.getName() + " is not authorized for window " + id);
        }

        return windowAccess;
    }

    private JSONArray createTabsJson(List<TabAccess> tabAccesses, List<Tab> tabs) {
        JSONArray result = new JSONArray();

        try {
            if (tabAccesses.isEmpty()) {
                for (Tab tab : tabs) {
                    if (isTabAllowed(tab)) {
                        result.put(new TabBuilder(tab, null).toJSON());
                    }
                }
            } else {
                for (TabAccess tabAccess : tabAccesses) {
                    if (isTabAccessAllowed(tabAccess)) {
                        result.put(new TabBuilder(tabAccess.getTab(), tabAccess).toJSON());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            return result;
        }
    }

    private boolean isTabAccessAllowed(TabAccess tabAccess) {
        return tabAccess.isActive() && tabAccess.isAllowRead() && isTabAllowed(tabAccess.getTab());
    }

    private boolean isTabAllowed(Tab tab) {
        String displayLogic = tab.getDisplayLogic();
        return displayLogic == null || displayLogic.trim().isEmpty();
    }
}