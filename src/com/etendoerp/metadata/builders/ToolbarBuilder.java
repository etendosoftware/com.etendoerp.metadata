package com.etendoerp.metadata.builders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.base.ConnectionProviderContextListener;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.erpCommon.utility.Utility;

import java.util.HashMap;
import java.util.Map;

public class ToolbarBuilder {
    private static final Logger logger = LogManager.getLogger(ToolbarBuilder.class);

    private final String language;
    private final String windowId;
    private final boolean isNew;
    private final ConnectionProvider connectionProvider;

    public ToolbarBuilder(String language, String windowId, boolean isNew) {
        this.language = language != null ? language : "en_US";
        this.windowId = windowId;
        this.isNew = isNew;
        this.connectionProvider = ConnectionProviderContextListener.getPool();
    }

    public JSONObject toJSON() {
        try {
            Window window = OBDal.getInstance().get(Window.class, windowId);
            return buildToolbarJSON(window);
        } catch (Exception e) {
            logger.error("Error building toolbar for window: {}", windowId, e);
            throw new RuntimeException("Failed to build toolbar", e);
        }
    }

    private JSONObject buildToolbarJSON(Window window) throws Exception {
        JSONObject json = new JSONObject();
        JSONArray buttons = new JSONArray();

        Map<String, ButtonConfig> standardButtons = getStandardButtons();
        for (ButtonConfig config : standardButtons.values()) {
            buttons.put(createButtonJSON(config));
        }

        json.put("buttons", buttons);
        json.put("windowId", windowId);
        json.put("isNew", isNew);

        return json;
    }

    private JSONObject createButtonJSON(ButtonConfig config) throws Exception {
        JSONObject button = new JSONObject();
        button.put("id", config.id);
        button.put("name", Utility.messageBD(connectionProvider, config.name, language));
        button.put("action", config.action);
        button.put("enabled", config.enabled);
        button.put("visible", true);
        button.put("icon", config.icon);
        return button;
    }

    private static class ButtonConfig {
        String id;
        String name;
        String action;
        boolean enabled;
        String icon;

        ButtonConfig(String id, String name, String action, boolean enabled, String icon) {
            this.id = id;
            this.name = name;
            this.action = action;
            this.enabled = enabled;
            this.icon = icon;
        }
    }

    private Map<String, ButtonConfig> getStandardButtons() {
        Map<String, ButtonConfig> buttons = new HashMap<>();
        buttons.put("NEW", new ButtonConfig("NEW", "New", "NEW", true, "plus"));
        buttons.put("SAVE", new ButtonConfig("SAVE", "Save", "SAVE", true, "save"));
        buttons.put("DELETE", new ButtonConfig("DELETE", "Delete", "DELETE", !isNew, "trash"));
        buttons.put("REFRESH", new ButtonConfig("REFRESH", "Refresh", "REFRESH", true, "refresh-cw"));
        buttons.put("FIND", new ButtonConfig("FIND", "Find", "FIND", true, "search"));
        buttons.put("GRID_VIEW", new ButtonConfig("GRID_VIEW", "Grid View", "GRID_VIEW", true, "grid"));
        return buttons;
    }
}