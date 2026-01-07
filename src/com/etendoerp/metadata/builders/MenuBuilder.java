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
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Form;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

/**
 * Builder class for generating Menu metadata in JSON format.
 *
 * @author luuchorocha
 */
public class MenuBuilder extends Builder {
    private static final ThreadLocal<MenuManager> manager = ThreadLocal.withInitial(MenuManager::new);

    /**
     * Constructor for MenuBuilder.
     * Initializes the MenuManager and sets global menu options if necessary.
     */
    public MenuBuilder() {
        try {
            manager.get().getMenu();
        } catch (NullPointerException e) {
            manager.get().setGlobalMenuOptions(new GlobalMenu());
        }
    }

    /**
     * Removes the MenuManager from the current thread.
     */
    public void unload() {
        manager.remove(); // Compliant
    }

    /**
     * Adds process-related information to the provided JSON object.
     *
     * @param json    The JSONObject to populate.
     * @param entry   The MenuOption entry.
     * @param process The process associated with the menu entry.
     * @param menu    The menu entry.
     * @throws JSONException If an error occurs while adding data to the JSON object.
     */
    private void addProcessInfo(JSONObject json, MenuOption entry, org.openbravo.model.ad.ui.Process process, Menu menu) throws JSONException {
        json.put("processId", process.getId());
        String url = null;
        boolean modal = false;
        boolean report = false;

        if (process.isActive()) {
            ModelImplementationMapping defaultMapping = getDefaultMapping(process);
            if (defaultMapping != null) {
                url = defaultMapping.getMappingName();
                if ("Standard".equals(process.getUIPattern())) {
                    modal = Utility.isModalProcess(process.getId());
                } else if (process.isReport() || process.isJasperReport()) {
                    report = true;
                } else if (entry.getType() == MenuManager.MenuEntryType.ProcessManual) {
                    // NOTE: ProcessManual always open in a modal
                    modal = true;
                }
            } else if ("P".equals(menu.getAction()) || "R".equals(menu.getAction())) {
                modal = Utility.isModalProcess(process.getId());
                url = getProcessUrl(process);
            }
        }

        json.put("processUrl", url);
        json.put("isModalProcess", modal);
        json.put("isReport", report);
    }

    /**
     * Retrieves the default model implementation mapping for a given process.
     *
     * @param process The process to search for mappings.
     * @return The default ModelImplementationMapping, or null if none is found.
     */
    private ModelImplementationMapping getDefaultMapping(org.openbravo.model.ad.ui.Process process) {
        for (ModelImplementation mi : process.getADModelImplementationList()) {
            for (ModelImplementationMapping mim : mi.getADModelImplementationMappingList()) {
                if (mim.isDefault()) {
                    return mim;
                }
            }
        }
        return null;
    }

    /**
     * Determines the URL for a given process based on its configuration.
     *
     * @param process The process to determine the URL for.
     * @return The URL string for the process.
     */
    private String getProcessUrl(org.openbravo.model.ad.ui.Process process) {
        if (Boolean.TRUE.equals(process.isExternalService()) && "PS".equals(process.getServiceType())) {
            return "/utility/OpenPentaho.html?inpadProcessId=" + process.getId();
        } else if ("S".equals(process.getUIPattern()) && !process.isJasperReport() && process.getProcedure() == null) {
            return "/ad_actionButton/ActionButtonJava_Responser.html";
        } else {
            return "/ad_actionButton/ActionButton_Responser.html";
        }
    }

    /**
     * Adds basic menu information to the provided JSON object.
     *
     * @param json     The JSONObject to populate.
     * @param entry    The MenuOption entry.
     * @param menu     The Menu object.
     * @param language The current language.
     * @param id       The ID of the menu entry.
     * @throws JSONException If an error occurs while adding data to the JSON object.
     */
    private void addBasicMenuInfo(JSONObject json, MenuOption entry, Menu menu, Language language, String id) throws JSONException {
        json.put("id", id);
        json.put("type", entry.getType());
        json.put("icon", menu.get(Menu.PROPERTY_ETMETAICON, language, id));
        json.put("name", menu.get(Menu.PROPERTY_NAME, language, id));
        json.put("description", menu.get(Menu.PROPERTY_DESCRIPTION, language, id));
        json.put("url", menu.getURL());
        json.put("action", menu.getAction());
    }

    /**
     * Converts a MenuOption entry to its JSON representation.
     *
     * @param entry The MenuOption entry to convert.
     * @return A JSONObject representing the menu entry.
     */
    private JSONObject toJSON(MenuOption entry) {
        JSONObject json = new JSONObject();

        try {
            Language language = OBContext.getOBContext().getLanguage();
            Menu menu = entry.getMenu();
            String id = menu.getId();

            addBasicMenuInfo(json, entry, menu, language, id);

            Window window = menu.getWindow();
            if (null != window) {
                json.put("windowId", window.getId());
            }

            org.openbravo.model.ad.ui.Process process = menu.getProcess();
            if (null != process) {
                addProcessInfo(json, entry, process, menu);
            }

            Process processDefinition = menu.getOBUIAPPProcessDefinition();
            if (null != processDefinition) {
                json.put("processDefinitionId", processDefinition.getId());
            }

            Form form = menu.getSpecialForm();
            if (null != form) {
                json.put("formId", form.getId());
            }

            List<MenuOption> children = entry.getChildren();
            if (!children.isEmpty()) {
                json.put("children", children.stream().map(this::toJSON).collect(Collectors.toList()));
            }

        } catch (JSONException e) {
            logger.error(e.getMessage(), e);
        }

        return json;
    }

    /**
     * Generates the complete menu metadata in JSON format.
     *
     * @return A JSONObject containing the menu metadata.
     * @throws JSONException If an error occurs while generating the JSON.
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();
        MenuOption menu = manager.get().getMenu();
        result.put("menu", menu.getChildren().stream().map(this::toJSON).collect(Collectors.toList()));

        return result;
    }
}
