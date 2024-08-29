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
import org.openbravo.model.ad.ui.Window;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WindowBuilder {
    private static final Logger log = LogManager.getLogger();
    private final Window window;

    public WindowBuilder(Window window) {
        this.window = window;
    }

    public JSONObject toJSON() {
        JSONObject result = new JSONObject();

        try {
            result.put("id", this.window.getId());
            Arrays.stream(Window.class.getDeclaredFields()).forEach(field -> {
                try {
                    result.put(field.getName(), window.get(field.getName()));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (JSONException e) {
            log.warn(e.getMessage());
        }

        return result;
    }
}
