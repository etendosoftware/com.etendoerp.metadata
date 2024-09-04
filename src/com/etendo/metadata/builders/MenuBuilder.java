package com.etendo.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.GlobalMenu;
import org.openbravo.client.application.MenuManager;
import org.openbravo.client.application.MenuManager.MenuOption;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.ad.utility.TreeNode;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import java.util.List;
import java.util.stream.Collectors;

public class MenuBuilder {
    private static final DataToJsonConverter converter = new DataToJsonConverter();
    private static final Logger logger = LogManager.getLogger();
    private final MenuOption menu;

    public MenuBuilder() {
        MenuManager manager = new MenuManager();
        manager.setGlobalMenuOptions(new GlobalMenu());
        this.menu = manager.getMenu();
    }

    private static JSONObject toJSON(MenuOption entry) {
        JSONObject menuItem = new JSONObject();

        try {
            Menu menu = entry.getMenu();
            Tab tab = entry.getTab();
            TreeNode treeNode = entry.getTreeNode();
            Window window = menu != null ? menu.getWindow() : null;
            List<MenuOption> items = entry.getChildren();

            if (null != treeNode) {
                menuItem.put("treeNode", converter.toJsonObject(treeNode, DataResolvingMode.FULL_TRANSLATABLE));
            }

            if (null != menu) {
                menuItem.put("menu", converter.toJsonObject(menu, DataResolvingMode.FULL_TRANSLATABLE));
            }

            if (null != window) {
                menuItem.put("window", converter.toJsonObject(window, DataResolvingMode.FULL_TRANSLATABLE));
            }

            if (null != tab) {
                menuItem.put("tab", converter.toJsonObject(tab, DataResolvingMode.FULL_TRANSLATABLE));
            }

            if (!items.isEmpty()) {
                menuItem.put("children", items.stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
            }
        } catch (JSONException e) {
            logger.warn(e.getMessage());
        }

        return menuItem;
    }

    public JSONArray toJSON() {
        return new JSONArray(this.menu.getChildren().stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
    }
}
