package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.GlobalMenu;
import org.openbravo.client.application.MenuManager;
import org.openbravo.client.application.MenuManager.MenuOption;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import java.util.List;
import java.util.stream.Collectors;

public class MenuBuilder {
    private final static String[] MENU_PROPERTIES = new String[]{Menu.PROPERTY_ID, Menu.PROPERTY_NAME, Menu.PROPERTY_ETMETAICON, Menu.PROPERTY_ACTION, Menu.PROPERTY_WINDOW, Menu.PROPERTY_PROCESS, Menu.PROPERTY_SUMMARYLEVEL, Menu.PROPERTY_SPECIALFORM, Menu.PROPERTY_OPENLINKINBROWSER, Menu.PROPERTY_URL, Menu.PROPERTY_OBUIAPPVIEW, Menu.PROPERTY_OBUIAPPPROCESSDEFINITION, Menu.PROPERTY_OBUIAPPMENUPARAMETERSLIST, Menu.PROPERTY_NAME};
    private final static String[] WINDOW_PROPERTIES = new String[]{Window.PROPERTY_ID, Window.PROPERTY_NAME, Window.PROPERTY_WINDOWTYPE};
    private static final DataToJsonConverter menuConverter = new DataToJsonConverter();
    private static final DataToJsonConverter windowConverter = new DataToJsonConverter();
    private static final Logger logger = LogManager.getLogger();
    private final MenuOption menu;

    public MenuBuilder() {
        MenuManager manager = new MenuManager();
        manager.setGlobalMenuOptions(new GlobalMenu());
        menu = manager.getMenu();
        menuConverter.setSelectedProperties(String.join(",", MENU_PROPERTIES));
        windowConverter.setSelectedProperties(String.join(",", WINDOW_PROPERTIES));
    }

    private static JSONObject toJSON(MenuOption entry) {
        try {
            Menu menu = entry.getMenu();
            JSONObject menuItem = menuConverter.toJsonObject(menu, DataResolvingMode.SHORT);
            Window window = menu.getWindow();
            List<MenuOption> items = entry.getChildren();

            menuItem.put("label", entry.getLabel());
            menuItem.put("label", entry.getLabel());

            if (null != window) {
                menuItem.put("window", windowConverter.toJsonObject(window, DataResolvingMode.SHORT));
            }

            if (!items.isEmpty()) {
                menuItem.put("children", items.stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
            }

            return menuItem;
        } catch (JSONException e) {
            logger.warn(e.getMessage());

            throw new InternalServerException();
        }
    }

    public JSONArray toJSON() {
        return new JSONArray(this.menu.getChildren().stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
    }
}
