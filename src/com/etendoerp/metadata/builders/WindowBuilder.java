package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.openbravo.service.json.DataToJsonConverter;

import java.util.List;

public class WindowBuilder {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private final String id;
    private final DataToJsonConverter converter = new DataToJsonConverter();

    public WindowBuilder(String id) {
        this.id = id;
    }

    public JSONObject toJSON() {
        WindowAccess windowAccess = getWindowAccess();
        Window window = windowAccess.getWindow();
        JSONObject windowJson = converter.toJsonObject(window, DataResolvingMode.FULL_TRANSLATABLE);

        try {
            windowJson.put("tabs", getTabsAndFields(windowAccess.getADTabAccessList(), windowAccess.getWindow()));
        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
        }

        return windowJson;
    }

    private WindowAccess getWindowAccess() {
        Role role = OBContext.getOBContext().getRole();
        OBCriteria<WindowAccess> windowAccessCriteria = OBDal.getInstance().createCriteria(WindowAccess.class);
        Window adWindow = OBDal.getInstance().get(Window.class, this.id);

        if (adWindow == null) {
            throw new NotFoundException();
        }

        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
        windowAccessCriteria.setMaxResults(1);

        WindowAccess windowAccess = (WindowAccess) windowAccessCriteria.uniqueResult();
        if (windowAccess == null) {
            throw new UnauthorizedException();
        }

        return windowAccess;
    }

    private JSONArray getTabsAndFields(List<TabAccess> tabAccesses, org.openbravo.model.ad.ui.Window window) {
        JSONArray tabs = new JSONArray();

        if (tabAccesses.isEmpty()) {
            addTabs(tabs, window.getADTabList());
        } else {
            addTabAccess(tabs, tabAccesses);
        }

        return tabs;
    }

    private void addTabs(JSONArray tabs, List<Tab> tabList) {
        for (Tab tab : tabList) {
            if (tab.isActive() && isTabAllowed(tab)) {
                tabs.put(createTabJson(tab, null));
            }
        }
    }

    private void addTabAccess(JSONArray tabs, List<TabAccess> tabAccesses) {
        for (TabAccess tabAccess : tabAccesses) {
            if (isTabAccessAllowed(tabAccess)) {
                tabs.put(createTabJson(tabAccess.getTab(), tabAccess));
            }
        }
    }

    private JSONObject createTabJson(Tab tab, TabAccess tabAccess) {
        return new TabBuilder(tab, tabAccess).toJSON();
    }

    private boolean isTabAccessAllowed(TabAccess tabAccess) {
        return tabAccess.isActive() && tabAccess.isAllowRead() && isTabAllowed(tabAccess.getTab());
    }

    private boolean isTabAllowed(Tab tab) {
        String displayLogic = tab.getDisplayLogic();
        return displayLogic == null || displayLogic.trim().isEmpty();
    }
}
