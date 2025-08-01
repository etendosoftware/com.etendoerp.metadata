/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import java.util.List;
import java.util.stream.Collectors;

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

/**
 * @author luuchorocha
 */
public class MenuBuilder extends Builder {
    private static final ThreadLocal<MenuManager> manager = ThreadLocal.withInitial(MenuManager::new);

    public MenuBuilder() {
        try {
            manager.get().getMenu();
        } catch (NullPointerException e) {
            manager.get().setGlobalMenuOptions(new GlobalMenu());
        }
    }

    private JSONObject toJSON(MenuOption entry) {
        JSONObject json = new JSONObject();

        try {
            Language language = OBContext.getOBContext().getLanguage();
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

            if (!children.isEmpty()) {
                json.put("children", children.stream().map(this::toJSON).collect(Collectors.toList()));
            }

        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();
        MenuOption menu = manager.get().getMenu();
        result.put("menu", menu.getChildren().stream().map(this::toJSON).collect(Collectors.toList()));

        return result;
    }
}
