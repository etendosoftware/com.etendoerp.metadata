/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.SessionInfo;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handles GET/POST /meta/recent-items
 *
 * GET  -> {"items": [...], "size": n}
 * POST body: {"items": [...]} -> {"items": [...], "size": n}
 *
 * Persists the recently opened menu entries of the new UI as an AD_Preference, mirroring how the
 * classic UI stores its recent lists (StorePropertyActionHandler): one list property row per
 * client + organization + role + user. The list is ordered newest first and trimmed to the
 * UINAVBA_RecentListSize preference (default 3), so the oldest entries are discarded.
 */
public class RecentItemsService extends MetadataService {

    static final String RECENT_ITEMS_PROPERTY = "ETMETA_RecentItemsList";
    static final String RECENT_LIST_SIZE_PROPERTY = "UINAVBA_RecentListSize";
    static final int DEFAULT_RECENT_LIST_SIZE = 3;
    static final String ITEMS = "items";
    static final String SIZE = "size";

    private static final Logger log = LogManager.getLogger(RecentItemsService.class);

    /**
     * Creates a new RecentItemsService for the given request/response pair.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    public RecentItemsService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String method = getRequest().getMethod();

        try {
            OBContext.setAdminMode(true);
            if ("GET".equalsIgnoreCase(method)) {
                write(list());
            } else if ("POST".equalsIgnoreCase(method)) {
                write(save(Utils.getRequestData(getRequest())));
            } else {
                throw new NotFoundException();
            }
        } catch (IOException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.getMessage(), e);
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    private JSONObject list() throws JSONException {
        int size = readListSize();
        return buildResponse(trim(readItems(), size), size);
    }

    private JSONObject save(JSONObject body) throws JSONException {
        int size = readListSize();
        JSONArray items = trim(body.getJSONArray(ITEMS), size);
        storeItems(items);
        return buildResponse(items, size);
    }

    private static JSONObject buildResponse(JSONArray items, int size) throws JSONException {
        return new JSONObject().put(ITEMS, items).put(SIZE, size);
    }

    /**
     * Reads the configured size of the recent list, falling back to the classic default when the
     * preference is missing, conflicting or not a positive integer (as ob-recent-utilities.js does).
     */
    static int readListSize() {
        try {
            String value = readPreference(RECENT_LIST_SIZE_PROPERTY);
            int size = value == null ? 0 : Integer.parseInt(value.trim());
            if (size > 0) {
                return size;
            }
        } catch (PropertyException | NumberFormatException e) {
            log.debug("Using default recent list size: {}", e.getMessage());
        }
        return DEFAULT_RECENT_LIST_SIZE;
    }

    /** Reads the stored recent items, returning an empty list when none (or an invalid one) exists. */
    static JSONArray readItems() {
        try {
            String value = readPreference(RECENT_ITEMS_PROPERTY);
            if (value != null) {
                return new JSONArray(value);
            }
        } catch (PropertyException | JSONException e) {
            log.debug("No stored recent items: {}", e.getMessage());
        }
        return new JSONArray();
    }

    /** Keeps only the first {@code size} entries, i.e. discards the oldest ones. */
    static JSONArray trim(JSONArray items, int size) throws JSONException {
        JSONArray trimmed = new JSONArray();
        int limit = Math.min(items.length(), size);
        for (int i = 0; i < limit; i++) {
            trimmed.put(items.get(i));
        }
        return trimmed;
    }

    private static String readPreference(String property) throws PropertyException {
        OBContext context = OBContext.getOBContext();
        return Preferences.getPreferenceValue(property, true, context.getCurrentClient(),
                context.getCurrentOrganization(), context.getUser(), context.getRole(), null);
    }

    /** Stores the list scoped like the classic StorePropertyActionHandler: client + org + user + role. */
    private static void storeItems(JSONArray items) {
        OBContext context = OBContext.getOBContext();
        Preferences.setPreferenceValue(RECENT_ITEMS_PROPERTY, items.toString(), true, context.getCurrentClient(),
                context.getCurrentOrganization(), context.getUser(), context.getRole(), null, null);
        SessionInfo.auditThisThread(false);
        OBDal.getInstance().flush();
    }
}
