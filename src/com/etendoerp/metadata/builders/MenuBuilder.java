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
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

import java.util.List;

public class MenuBuilder {
    private final Logger logger;
    private final MenuOption menu;
    private final Language language;

    public MenuBuilder() {
        MenuManager manager = new MenuManager();
        manager.setGlobalMenuOptions(new GlobalMenu());
        menu = manager.getMenu();
        language = OBContext.getOBContext().getLanguage();
        logger = LogManager.getLogger(this.getClass());
    }

    private JSONObject toJSON(MenuOption entry) {
        try {
            JSONObject json = new JSONObject();
            Menu menu = entry.getMenu();
            String id = menu.getId();
            Window window = menu.getWindow();
            List<MenuOption> children = entry.getChildren();

            json.put("id", id);
            json.put("type", entry.getType());
            json.put("icon", menu.get(Menu.PROPERTY_ETMETAICON, language, id));
            json.put("name", menu.get(Menu.PROPERTY_NAME, language, id));
            json.put("description", menu.get(Menu.PROPERTY_DESCRIPTION, language, id));
            json.put("url", menu.get(Menu.PROPERTY_URL, language, id));
            json.put("process", menu.get(Menu.PROPERTY_PROCESS, language, id));
            json.put("processDefinition", menu.get(Menu.PROPERTY_OBUIAPPPROCESSDEFINITION, language, id));
            json.put("openLinkInBrowser", menu.get(Menu.PROPERTY_OPENLINKINBROWSER, language, id));

            if (null != window) {
                json.put("windowId", window.getId());
            }

            if (!children.isEmpty()) {
                json.put("children", children.stream().map(this::toJSON).toList());
            }

            return json;
        } catch (JSONException e) {
            logger.error(e.getMessage());

            throw new InternalServerException();
        }
    }

    public JSONArray toJSON() {
        return new JSONArray(this.menu.getChildren().stream().map(this::toJSON).toList());
    }
}
