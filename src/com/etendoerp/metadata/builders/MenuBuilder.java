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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.builders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.GlobalMenu;
import org.openbravo.client.application.MenuManager;
import org.openbravo.client.application.MenuManager.MenuOption;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.domain.ModelImplementationMapping;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Form;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.redis.interfaces.CachedConcurrentMap;

/**
 * Builder class for generating Menu metadata in JSON format.
 *
 * @author luuchorocha
 */
public class MenuBuilder extends Builder {
    private static final ThreadLocal<MenuManager> manager = ThreadLocal.withInitial(MenuManager::new);

    private static final String MENU_CACHE = "MENU_METADATA";
    private static final String CACHE_KEY_SEPARATOR = "_";
    private static final String DEFAULT_MAPPINGS_HQL = "select mim from ADModelImplementationMapping mim "
        + "where mim.default = true and mim.modelObject.process is not null";
    private static final CachedConcurrentMap<String, JSONObject> menuCache = new CachedConcurrentMap<>(MENU_CACHE);

    private Map<String, ModelImplementationMapping> defaultMappingsByProcess;

    /**
     * Constructor for MenuBuilder. Points the thread-local {@link MenuManager} at the shared,
     * CDI-managed {@link GlobalMenu} singleton so it participates in the standard menu-cache
     * invalidation.
     */
    public MenuBuilder() {
        // Point the (thread-local) MenuManager at the CDI @ApplicationScoped GlobalMenu singleton
        // (the same instance MenuCacheHandler invalidates) instead of a per-thread new GlobalMenu().
        // A per-thread instance is invisible to the classic menu-cache invalidation, so its internal
        // tree would go stale and the rebuilt menu JSON would show outdated names/structure depending
        // on which pooled thread served the request. Resolved via WeldUtils because MenuBuilder is
        // instantiated outside a CDI scope that would allow @Inject (same pattern as LabelsBuilder).
        manager.get().setGlobalMenuOptions(WeldUtils.getInstanceFromStaticBeanManager(GlobalMenu.class));
    }

    /**
     * Removes the MenuManager from the current thread.
     */
    public void unload() {
        manager.remove(); // Compliant
    }

    /**
     * Clears the cached menu JSON for every role and language. Invoked when Application
     * Dictionary entities that compose the menu change (see
     * {@code MenuCacheInvalidationObserver}) or on a full metadata cache reset
     * (see {@code MetadataCacheManager#invalidateAll()}).
     */
    public static void clearMenuCache() {
        menuCache.clear();
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
                    // NOTE: Reports always open in a js modal
                    modal = true;
                } else if (entry.getType() == MenuManager.MenuEntryType.ProcessManual) {
                    // NOTE: ProcessManual always open in a js modal
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
     * Retrieves the default model implementation mapping for a given process from the batch
     * loaded map, resolving it in O(1). The map is loaded lazily on the first process entry
     * and reused for the rest of the menu, so the whole build performs a single query instead
     * of the previous per-process N+1 lazy iteration.
     *
     * @param process The process to search for mappings.
     * @return The default ModelImplementationMapping, or null if none is found.
     */
    private ModelImplementationMapping getDefaultMapping(org.openbravo.model.ad.ui.Process process) {
        if (defaultMappingsByProcess == null) {
            defaultMappingsByProcess = loadDefaultMappings();
        }
        return defaultMappingsByProcess.get(process.getId());
    }

    /**
     * Loads every default {@link ModelImplementationMapping} in a single query and indexes it
     * by process id. Uses the raw Hibernate session (as {@link #addViewInfo} does) so no
     * active/client/organization filter is applied, preserving the semantics of the original
     * lazy-collection iteration (which included inactive rows). When a process has more than one
     * default mapping, the first one returned by the query wins, matching the previous
     * "first default found" behavior.
     *
     * @return A map from process id to its default ModelImplementationMapping.
     */
    private Map<String, ModelImplementationMapping> loadDefaultMappings() {
        Map<String, ModelImplementationMapping> result = new HashMap<>();
        List<ModelImplementationMapping> mappings = OBDal.getInstance().getSession()
            .createQuery(DEFAULT_MAPPINGS_HQL, ModelImplementationMapping.class)
            .list();
        for (ModelImplementationMapping mim : mappings) {
            result.putIfAbsent(mim.getModelObject().getProcess().getId(), mim);
        }
        return result;
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
     * Adds view identifier information to the provided JSON object.
     * Queries the obuiapp_view_impl table to resolve the JS class name for the view,
     * falling back to the view's name when classname is not set.
     *
     * @param json   The JSONObject to populate.
     * @param menuId The ID of the menu entry.
     * @throws JSONException If an error occurs while adding data to the JSON object.
     */
    @SuppressWarnings("unchecked")
    private void addViewInfo(JSONObject json, String menuId) throws JSONException {
        List<Object[]> viewData = OBDal.getInstance().getSession()
            .createNativeQuery(
                "select vi.classname, vi.name from ad_menu m"
                    + " inner join obuiapp_view_impl vi"
                    + " on m.em_obuiapp_view_impl_id = vi.obuiapp_view_impl_id"
                    + " where m.ad_menu_id = :menuId")
            .setParameter("menuId", menuId)
            .list();
        if (viewData.isEmpty()) {
            return;
        }
        Object[] row = viewData.get(0);
        String classname = (String) row[0];
        String name = (String) row[1];
        String identifier = classname != null ? classname : name;
        if (identifier == null) {
            return;
        }
        String simpleViewName = identifier.contains(".")
            ? identifier.substring(identifier.lastIndexOf('.') + 1)
            : identifier;
        json.put("viewId", simpleViewName);
    }

    /**
     * Adds form URL information to the provided JSON object.
     *
     * @param json The JSONObject to populate.
     * @param form The Form associated with the menu entry.
     * @throws JSONException If an error occurs while adding data to the JSON object.
     */
    private void addFormInfo(JSONObject json, Form form) throws JSONException {
        json.put("formId", form.getId());
        String javaClassName = form.getJavaClassName();
        if (javaClassName == null || javaClassName.isEmpty()) {
            return;
        }
        String simpleClassName = javaClassName.contains(".")
            ? javaClassName.substring(javaClassName.lastIndexOf('.') + 1)
            : javaClassName;
        json.put("formUrl", "/ad_forms/" + simpleClassName + ".html");
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
                addWindowType(json, window);
            }

            org.openbravo.model.ad.ui.Process process = menu.getProcess();
            if (null != process) {
                addProcessInfo(json, entry, process, menu);
            }

            Process processDefinition = menu.getOBUIAPPProcessDefinition();
            if (null != processDefinition) {
                json.put("processDefinitionId", processDefinition.getId());
            }

            if (entry.getType() == MenuManager.MenuEntryType.View) {
                addViewInfo(json, menu.getId());
            }

            Form form = menu.getSpecialForm();
            if (null != form) {
                addFormInfo(json, form);
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
     * Emits the {@code windowType} field into the menu entry JSON when the
     * underlying {@link Window} declares a type. The field is optional and is
     * only added when present, mirroring the convention used by other optional
     * keys (e.g. {@code processId}, {@code formId}).
     *
     * <p>The {@code window} reference comes from the cached {@link MenuManager}
     * tree (held in a thread-local and reused across requests), so it may be a
     * Hibernate proxy detached from a closed session: reading {@code getWindowType()}
     * directly on it raises {@code LazyInitializationException}. The window is
     * therefore re-fetched by id within the current session before its type is
     * read, following the same pattern used by {@code WindowBuilder} and
     * {@link #addViewInfo}.
     *
     * @param json   The menu entry JSON being built.
     * @param window The non-null window associated with the menu entry.
     * @throws JSONException If the JSON object rejects the put operation.
     */
    private void addWindowType(JSONObject json, Window window) throws JSONException {
        Window persistentWindow = OBDal.getInstance().get(Window.class, window.getId());
        if (persistentWindow == null) {
            return;
        }
        String windowType = persistentWindow.getWindowType();
        if (windowType != null) {
            json.put(Constants.JSON_WINDOW_TYPE_KEY, windowType);
        }
    }

    /**
     * Generates the complete menu metadata in JSON format. The result is cached per role and
     * language: on a cache hit the menu tree is not traversed again, avoiding the JSON build
     * and its per-entry queries.
     *
     * @return A JSONObject containing the menu metadata.
     * @throws JSONException If an error occurs while generating the JSON.
     */
    @Override
    public JSONObject toJSON() throws JSONException {
        String cacheKey = buildCacheKey(OBContext.getOBContext());
        JSONObject cached = menuCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JSONObject result = buildMenuJson();
        menuCache.put(cacheKey, result);
        return result;
    }

    /**
     * Builds the cache key for the menu JSON. The menu structure depends only on role and
     * language, so both compose the key.
     *
     * @param context The current OB context.
     * @return The cache key {@code "roleId_languageId"}.
     */
    private String buildCacheKey(OBContext context) {
        return context.getRole().getId() + CACHE_KEY_SEPARATOR + context.getLanguage().getId();
    }

    /**
     * Traverses the menu tree and builds its JSON representation. Extracted from
     * {@link #toJSON()} so the traversal (and its queries) run only on a cache miss.
     *
     * @return A JSONObject containing the menu metadata.
     * @throws JSONException If an error occurs while generating the JSON.
     */
    private JSONObject buildMenuJson() throws JSONException {
        JSONObject result = new JSONObject();
        MenuOption menu = manager.get().getMenu();
        result.put("menu", menu.getChildren().stream().map(this::toJSON).collect(Collectors.toList()));
        return result;
    }
}
