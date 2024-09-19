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
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

import java.util.ArrayList;
import java.util.List;

public class MenuBuilder {
    private static final Logger logger = LogManager.getLogger(MenuBuilder.class);
    private final MenuOption menu;

    public MenuBuilder() {
        MenuManager manager = new MenuManager();
        manager.setGlobalMenuOptions(new GlobalMenu());
        menu = manager.getMenu();
    }

    private JSONObject toJSON(MenuOption entry) {
        try {
            JSONObject json = new JSONObject();
            Menu menu = entry.getMenu();
            Window window = menu.getWindow();
            List<MenuOption> children = entry.getChildren();

            json.put("id", menu.getId());
            json.put("name", menu.get(Menu.PROPERTY_NAME, OBContext.getOBContext().getLanguage(), menu.getId()));
            json.put("icon", menu.getETMETAIcon());
            json.put("action", menu.getAction());
            json.put("type", entry.getType());
            json.put("label", entry.getLabel());

            if (null != window) {
                json.put("windowId", window.getId());
            }

            if (!children.isEmpty()) {
                List<JSONObject> list = new ArrayList<>();

                for (MenuOption item : children) {
                    JSONObject child = toJSON(item);
                    list.add(child);
                }

                json.put("children", list);
            }

            return json;
        } catch (JSONException e) {
            logger.warn(e.getMessage());

            throw new InternalServerException();
        }
    }

    public JSONArray toJSON() {
        List<JSONObject> list = new ArrayList<>();

        for (MenuOption menuOption : this.menu.getChildren()) {
            JSONObject json = toJSON(menuOption);
            list.add(json);
        }

        return new JSONArray(list);
    }
}
