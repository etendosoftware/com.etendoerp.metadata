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
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Form;
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
            Form form = menu.getSpecialForm();
            Process processDefinition = menu.getOBUIAPPProcessDefinition();
            org.openbravo.model.ad.ui.Process process = menu.getProcess();

            json.put("id", id);
            json.put("type", entry.getType());
            json.put("icon", menu.get(Menu.PROPERTY_ETMETAICON, language, id));
            json.put("name", menu.get(Menu.PROPERTY_NAME, language, id));
            json.put("description", menu.get(Menu.PROPERTY_DESCRIPTION, language, id));
            json.put("url", menu.getURL());
            json.put("action", menu.getAction());

            if (null != window) json.put("windowId", window.getId());
            if (null != process) json.put("processId", process.getId());
            if (null != processDefinition) json.put("processDefinitionId", processDefinition.getId());
            if (null != form) json.put("formId", form.getId());

            if (!children.isEmpty()) json.put("children", children.stream().map(this::toJSON).toList());

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
