package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.utils.Utils.formatMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.ConnectionProviderContextListener;
import org.openbravo.client.application.Process;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.data.ButtonConfig;
import com.etendoerp.metadata.exceptions.InternalServerException;

public class ToolbarBuilder {
    private static final Logger logger = LogManager.getLogger(ToolbarBuilder.class);

    private final String language;
    private final String windowId;
    private final String tabId;
    private final boolean isNew;
    private final ConnectionProvider connectionProvider;
    private final Tab tab;
    private final Window window;

    public ToolbarBuilder(String language, String windowId, String tabId, boolean isNew) {
        this.language = language != null ? language : "en_US";
        this.windowId = windowId;
        this.isNew = isNew;
        this.tabId = tabId;
        this.connectionProvider = ConnectionProviderContextListener.getPool();

        if (tabId != null) {
            this.tab = OBDal.getInstance().get(Tab.class, tabId);
        } else {
            this.tab = null;
        }

        if (windowId != null) {
            this.window = OBDal.getInstance().get(Window.class, windowId);
        } else {
            this.window = null;
        }
    }

    public JSONObject toJSON() {
        try {
            return buildToolbarJSON(window);
        } catch (Exception e) {
            logger.error("Error building toolbar for window: {} - tab: {}", windowId, tabId, e);
            throw new InternalServerException(formatMessage(
                    "Error building toolbar for window: {} - tab: {} - error: {}",
                    windowId,
                    tabId,
                    e));
        }
    }

    private JSONObject buildToolbarJSON(Window window) throws Exception {
        JSONObject response = new JSONObject();
        JSONArray buttons = new JSONArray();

        Map<String, ButtonConfig> standardButtons = getStandardButtons();
        for (ButtonConfig config : standardButtons.values()) {
            buttons.put(createButtonJSON(config));
        }

        if (tab != null) {
            addProcessButtons(buttons, tab);
        } else {
            for (Tab tab : window.getADTabList()) {
                if (tab.isActive()) {
                    addProcessButtons(buttons, tab);
                }
            }
        }

        response.put("buttons", buttons);
        response.put("windowId", windowId);
        response.put("tabId", tabId);
        response.put("isNew", isNew);

        return response;
    }

    private void addProcessButtons(JSONArray buttons, Tab tab) throws Exception {
        JSONArray processButtons = getProcessButtons(tab);
        for (int i = 0; i < processButtons.length(); i++) {
            buttons.put(processButtons.get(i));
        }
    }

    private JSONArray getProcessButtons(Tab tab) throws Exception {
        JSONArray buttons = new JSONArray();

        List<Field> processFields = tab.getADFieldList().stream().filter(field -> field.isActive() &&
                                                                                  TabBuilder.hasAccessToProcess(field,
                                                                                                                windowId) &&
                                                                                  FieldBuilder.isProcessField(field))
                                       .collect(Collectors.toList());

        for (Field field : processFields) {
            DataToJsonConverter converter = new DataToJsonConverter();
            JSONObject button = new FieldBuilder(field, null).toJSON();
            Process processDefinition = field.getColumn().getOBUIAPPProcess();
            org.openbravo.model.ad.ui.Process processAction = field.getColumn().getProcess();

            button.put("id", field.getName());
            button.put("name", Utility.messageBD(connectionProvider, field.getName(), language));
            button.put("action", "PROCESS");
            button.put("displayLogic", field.getDisplayLogic());
            button.put("buttonText", field.getColumn().getName());

            if (processDefinition != null) {
                button.put("processDefinition", ProcessDefinitionBuilder.getFieldProcess(field));
            }

            if (processAction != null) {
                button.put("processAction", ProcessActionBuilder.getFieldProcess(field));
            }

            button.put("tabId", tab.getId());
            button.put("field", new FieldBuilder(field, null).toJSON());


            buttons.put(button);
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

    private Map<String, ButtonConfig> getStandardButtons() {
        Map<String, ButtonConfig> buttons = new HashMap<>();

        buttons.put("NEW", new ButtonConfig("NEW", "OBUIAPP_NewDoc", "NEW", true, "plus"));
        buttons.put("SAVE", new ButtonConfig("SAVE", "OBUIAPP_SaveRow", "SAVE", true, "save"));
        buttons.put("DELETE", new ButtonConfig("DELETE", "OBUIAPP_DeleteRow", "DELETE", !isNew, "trash"));
        buttons.put("REFRESH", new ButtonConfig("REFRESH", "OBUIAPP_RefreshData", "REFRESH", true, "refresh-cw"));
        buttons.put("FIND", new ButtonConfig("FIND", "OBUIAPP_Find", "FIND", true, "search"));
        buttons.put("EXPORT", new ButtonConfig("EXPORT", "OBUIAPP_ExportGrid", "EXPORT", true, "download"));
        buttons.put("ATTACHMENTS",
                    new ButtonConfig("ATTACHMENTS", "OBUIAPP_Attachments", "ATTACHMENTS", true, "paperclip"));
        buttons.put("GRID_VIEW", new ButtonConfig("GRID_VIEW", "OBUIAPP_GridView", "GRID_VIEW", true, "grid"));

        return buttons;
    }

}