package com.etendoerp.metadata.builders;

import com.etendoerp.metadata.ProcessUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.base.ConnectionProviderContextListener;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.client.application.Process;
import org.openbravo.erpCommon.utility.Utility;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

public class ToolbarBuilder {
    private static final Logger logger = LogManager.getLogger(ToolbarBuilder.class);

    private final String language;
    private final String windowId;
    private final String tabId;
    private final boolean isNew;
    private final ConnectionProvider connectionProvider;
    private final TabBuilder tabBuilder;

    public ToolbarBuilder(String language, String windowId, String tabId,boolean isNew) {
        this.language = language != null ? language : "en_US";
        this.windowId = windowId;
        this.isNew = isNew;
        this.tabId = tabId;
        this.connectionProvider = ConnectionProviderContextListener.getPool();

        Window window = OBDal.getInstance().get(Window.class, windowId);
        Tab mainTab = (tabId != null)
                ? OBDal.getInstance().get(Tab.class, tabId)
                :  window.getADTabList().get(0);
        this.tabBuilder = new TabBuilder(mainTab, null);
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
        JSONObject response = new JSONObject();
        JSONArray buttons = new JSONArray();

        Map<String, ButtonConfig> standardButtons = getStandardButtons();
        for (ButtonConfig config : standardButtons.values()) {
            buttons.put(createButtonJSON(config));
        }

        if (tabId != null) {
            Tab tab = OBDal.getInstance().get(Tab.class, tabId);
            if (tab != null) {
                JSONArray processButtons = getProcessButtons(tab);
                for (int i = 0; i < processButtons.length(); i++) { // Fixed loop condition
                    buttons.put(processButtons.get(i));
                }
            }
        } else {
            for (Tab tab : window.getADTabList()) {
                if (tab.isActive()) {
                    JSONArray processButtons = getProcessButtons(tab);
                    for (int i = 0; i < processButtons.length(); i++) {
                        buttons.put(processButtons.get(i));
                    }
                }
            }
        }

        response.put("buttons", buttons);
        response.put("windowId", windowId);
        response.put("tabId", tabId);
        response.put("isNew", isNew);

        return response;
    }

    private JSONArray getProcessButtons(Tab tab) throws Exception {
        JSONArray buttons = new JSONArray();

        List<Field> processFields = tab.getADFieldList()
                .stream()
                .filter(field ->
                        field.isActive() &&
                                tabBuilder.shouldDisplayField(field) &&
                                tabBuilder.hasAccessToProcess(field, windowId) &&
                                tabBuilder.isProcessField(field))
                .collect(Collectors.toList());

        for (Field field : processFields) {
            Process process = field.getColumn().getOBUIAPPProcess();
            if (process != null) {
                JSONObject button = new JSONObject();
                JSONObject processInfo = ProcessUtils.createProcessJSON(process);

                button.put("id", field.getName());
                button.put("name", Utility.messageBD(connectionProvider, field.getName(), language));
                button.put("action", "PROCESS");
                button.put("processId", process.getId());
                button.put("processInfo", processInfo);
                button.put("displayLogic", field.getDisplayLogic());
                button.put("buttonText", field.getColumn().getName());
                button.put("tabId", tab.getId());


                buttons.put(button);
            }
        }

        return buttons;
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

        buttons.put("NEW", new ButtonConfig(
                "NEW",
                "OBUIAPP_NewDoc",
                "NEW",
                true,
                "plus"
        ));
        buttons.put("SAVE", new ButtonConfig(
                "SAVE",
                "OBUIAPP_SaveRow",
                "SAVE",
                true,
                "save"
        ));
        buttons.put("DELETE", new ButtonConfig(
                "DELETE",
                "OBUIAPP_DeleteRow",
                "DELETE",
                !isNew,
                "trash"
        ));
        buttons.put("REFRESH", new ButtonConfig(
                "REFRESH",
                "OBUIAPP_RefreshData",
                "REFRESH",
                true,
                "refresh-cw"
        ));
        buttons.put("FIND", new ButtonConfig(
                "FIND",
                "OBUIAPP_Find",
                "FIND",
                true,
                "search"
        ));
        buttons.put("EXPORT", new ButtonConfig(
                "EXPORT",
                "OBUIAPP_ExportGrid",
                "EXPORT",
                true,
                "download"
        ));
        buttons.put("ATTACHMENTS", new ButtonConfig(
                "ATTACHMENTS",
                "OBUIAPP_Attachments",
                "ATTACHMENTS",
                true,
                "paperclip"
        ));
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