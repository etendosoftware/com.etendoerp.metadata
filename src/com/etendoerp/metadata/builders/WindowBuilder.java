package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.NotFoundException;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBSecurityException;
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
    private static final DataToJsonConverter windowConverter = new DataToJsonConverter();
    private static final DataToJsonConverter tabConverter = new DataToJsonConverter();
    private final static String[] WINDOW_PROPERTIES = new String[]{Window.PROPERTY_ID, Window.PROPERTY_NAME, Window.PROPERTY_WINDOWTYPE, Window.PROPERTY_DESCRIPTION};
    private final static String[] TAB_PROPERTIES = new String[]{Tab.PROPERTY_ID, Tab.PROPERTY_NAME, Tab.PROPERTY_TABLEVEL, Tab.PROPERTY_TABLE};

    private final String id;

    public WindowBuilder(String id) {
        this.id = id;
        windowConverter.setSelectedProperties(String.join(",", WINDOW_PROPERTIES));
        tabConverter.setSelectedProperties(String.join(",", TAB_PROPERTIES));
    }

    public JSONObject toJSON() throws JSONException {
        Role role = OBContext.getOBContext().getRole();
        org.openbravo.model.ad.ui.Window adWindow = OBDal.getInstance().get(org.openbravo.model.ad.ui.Window.class, this.id);
        OBCriteria<WindowAccess> windowAccessCriteria = OBDal.getInstance().createCriteria(WindowAccess.class);
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ROLE, role));
        windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_ACTIVE, true));

        if (adWindow != null) {
            windowAccessCriteria.add(Restrictions.eq(WindowAccess.PROPERTY_WINDOW, adWindow));
            windowAccessCriteria.setMaxResults(1);
            WindowAccess windowAccess = (WindowAccess) windowAccessCriteria.uniqueResult();

            if (windowAccess != null) {
                JSONObject window = windowConverter.toJsonObject(windowAccess.getWindow(), DataResolvingMode.FULL_TRANSLATABLE);
                window.put("tabs", getTabsAndFields(windowAccess.getADTabAccessList(), windowAccess.getWindow()));

                return window;
            } else {
                throw new OBSecurityException();
            }
        } else {
            throw new NotFoundException();
        }
    }

    private JSONArray getTabsAndFields(List<TabAccess> tabAccesses, org.openbravo.model.ad.ui.Window window) {
        JSONArray tabs = new JSONArray();

        if (tabAccesses.isEmpty()) {
            for (Tab tab : window.getADTabList().stream().filter(Tab::isActive).toList()) {
                if (isTabAllowed(tab)) {
                    tabs.put(new TabBuilder(tab, null).toJSON());
                }
            }
        } else {
            for (TabAccess tabAccess : tabAccesses.stream().filter(tabAccess -> tabAccess.isActive() && tabAccess.isAllowRead() && tabAccess.getTab().isActive() && tabAccess.getTab().isAllowRead()).toList()) {
                if (isTabAllowed(tabAccess.getTab())) {
                    tabs.put(new TabBuilder(tabAccess.getTab(), tabAccess).toJSON());
                }
            }
        }

        return tabs;
    }

    private boolean isTabAllowed(Tab tab) {
        return tab.getDisplayLogic() == null || tab.getDisplayLogic().trim().isEmpty();
    }
}
