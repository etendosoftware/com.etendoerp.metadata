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
import java.util.Optional;

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
        Optional<Window> adWindowOptional = Optional.ofNullable(OBDal.getInstance().get(Window.class, this.id));
        Window adWindow = adWindowOptional.orElseThrow(NotFoundException::new);

        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
        windowAccessCriteria.setMaxResults(1);

        Optional<WindowAccess> windowAccessOptional = Optional.ofNullable((WindowAccess) windowAccessCriteria.uniqueResult());

        return windowAccessOptional.orElseThrow(UnauthorizedException::new);
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
        tabList.stream().filter(Tab::isActive).filter(this::isTabAllowed).map(tab -> createTabJson(tab, null)).forEach(tabs::put);
    }

    private void addTabAccess(JSONArray tabs, List<TabAccess> tabAccesses) {
        tabAccesses.stream().filter(this::isTabAccessAllowed).map(tabAccess -> createTabJson(tabAccess.getTab(), tabAccess)).forEach(tabs::put);
    }

    private JSONObject createTabJson(Tab tab, TabAccess tabAccess) {
        return new TabBuilder(tab, tabAccess).toJSON();
    }

    private boolean isTabAccessAllowed(TabAccess tabAccess) {
        return tabAccess.isActive() && tabAccess.isAllowRead() && isTabAllowed(tabAccess.getTab());
    }

    private boolean isTabAllowed(Tab tab) {
        return tab.getDisplayLogic() == null || tab.getDisplayLogic().trim().isEmpty();
    }
}
