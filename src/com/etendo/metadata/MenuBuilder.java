package com.etendo.metadata;

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

import java.util.List;
import java.util.stream.Collectors;

public class MenuBuilder {
    private static final Logger log = LogManager.getLogger();
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
            Window window = menu != null ? menu.getWindow() : null;

            menuItem.put("type", entry.getType());
            menuItem.put("readOnly", entry.getReadOnlyStringValue());
            menuItem.put("tabId", tab != null ? tab.getId() : null);
            menuItem.put("isVisible", entry.isVisible());
            menuItem.put("isProcess", entry.isProcess());
            menuItem.put("label", entry.getLabel());
            menuItem.put("sequenceNumber", entry.getTreeNode().getSequenceNumber());

            if (null != menu) {
                menuItem.put("id", menu.getId());
                menuItem.put("name", menu.getName());
                menuItem.put("form", menu.getSpecialForm());
                menuItem.put("view", menu.getObuiappView());
                menuItem.put("identifier", menu.getIdentifier());
                menuItem.put("process", menu.getProcess());
                menuItem.put("action", menu.getAction());
                menuItem.put("url", menu.getURL());
                menuItem.put("description", menu.getDescription());
                menuItem.put("windowId", window != null ? window.getId() : null);
                menuItem.put("icon", menu.getETMETAIcon());
            }

            List<MenuOption> items = entry.getChildren();

            if (!items.isEmpty()) {
                menuItem.put("children", items.stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
            }
        } catch (JSONException e) {
            log.warn(e.getMessage());
        }

        return menuItem;
    }

    public JSONArray toJSON() {
        return new JSONArray(this.menu.getChildren().stream().map(MenuBuilder::toJSON).collect(Collectors.toList()));
    }
}
