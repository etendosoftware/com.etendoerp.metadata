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
        JSONObject response = new JSONObject();
        JSONArray buttons = new JSONArray();

        Map<String, ButtonConfig> standardButtons = getStandardButtons();
        for (ButtonConfig config : standardButtons.values()) {
            buttons.put(createButtonJSON(config));
        }

        response.put("buttons", buttons);
        response.put("windowId", windowId);
        response.put("isNew", isNew);

        json.put("response", response);
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
        // Base buttons used among all the windows
        Map<String, ButtonConfig> buttons = new HashMap<>();
        // NEW_DOC button
        buttons.put("NEW", new ButtonConfig(
                "NEW",
                "OBUIAPP_NewDoc",
                "NEW",
                true,
                "plus"
        ));
        // SAVE button
        buttons.put("SAVE", new ButtonConfig(
                "SAVE",
                "OBUIAPP_SaveRow",
                "SAVE",
                true,
                "save"
        ));
        // DELETE button
        buttons.put("DELETE", new ButtonConfig(
                "DELETE",
                "OBUIAPP_DeleteRow",
                "DELETE",
                !isNew,
                "trash"
        ));

        // REFRESH button
        buttons.put("REFRESH", new ButtonConfig(
                "REFRESH",
                "OBUIAPP_RefreshData",
                "REFRESH",
                true,
                "refresh-cw"
        ));
        // FIND button
        buttons.put("FIND", new ButtonConfig(
                "FIND",
                "OBUIAPP_Find",
                "FIND",
                true,
                "search"
        ));
        // EXPORT button
        buttons.put("EXPORT", new ButtonConfig(
                "EXPORT",
                "OBUIAPP_ExportGrid",
                "EXPORT",
                true,
                "download"
        ));
        // ATTACHMENTS button
        buttons.put("ATTACHMENTS", new ButtonConfig(
                "ATTACHMENTS",
                "OBUIAPP_Attachments",
                "ATTACHMENTS",
                true,
                "paperclip"
        ));
        // GRID_VIEW button
        buttons.put("GRID_VIEW", new ButtonConfig(
                "GRID_VIEW",
                "OBUIAPP_GridView",
                "GRID_VIEW",
                true,
                "grid"
        ));

        return buttons;
    }
}