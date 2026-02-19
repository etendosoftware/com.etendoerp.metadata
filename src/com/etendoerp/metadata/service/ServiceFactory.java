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

package com.etendoerp.metadata.service;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.utils.LegacyPaths;
import com.etendoerp.metadata.utils.LegacyUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static com.etendoerp.metadata.utils.Constants.*;

/**
 * @author luuchorocha
 */
public class ServiceFactory {

    private static final Map<String, BiFunction<HttpServletRequest, HttpServletResponse, MetadataService>> EXACT_MATCH_SERVICES = new LinkedHashMap<>();
    private static final Map<String, BiFunction<HttpServletRequest, HttpServletResponse, MetadataService>> PREFIX_MATCH_SERVICES = new LinkedHashMap<>();

    static {
        // Exact match services
        EXACT_MATCH_SERVICES.put(SESSION_PATH, SessionService::new);
        EXACT_MATCH_SERVICES.put(MENU_PATH, MenuService::new);
        EXACT_MATCH_SERVICES.put(MESSAGE_PATH, MessageService::new);
        EXACT_MATCH_SERVICES.put(LABELS_PATH, LabelsService::new);
        EXACT_MATCH_SERVICES.put(PREFERENCES_PATH, PreferencesService::new);

        // Prefix match services (order matters for overlapping prefixes)
        PREFIX_MATCH_SERVICES.put(WINDOW_PATH, WindowService::new);
        PREFIX_MATCH_SERVICES.put(TAB_PATH, TabService::new);
        PREFIX_MATCH_SERVICES.put(LANGUAGE_PATH, LanguageService::new);
        PREFIX_MATCH_SERVICES.put(LOCATION_PATH, LocationMetadataService::new);
        PREFIX_MATCH_SERVICES.put(TOOLBAR_PATH, ToolbarService::new);
        PREFIX_MATCH_SERVICES.put(REPORT_AND_PROCESS_PATH, ReportAndProcessService::new);
        PREFIX_MATCH_SERVICES.put(PROCESS_EXECUTION_PATH, ProcessExecutionService::new);
        PREFIX_MATCH_SERVICES.put(PROCESS_PATH, ProcessMetadataService::new);
        PREFIX_MATCH_SERVICES.put(LEGACY_PATH, LegacyService::new);
    }

    private static MetadataService buildLegacyForwardService(HttpServletRequest req, HttpServletResponse res,
            String path) {
        return new MetadataService(req, res) {

            @Override
            public void process() throws ServletException, IOException {
                try {
                    handleLegacySession(req, path);
                    forwardRequest(req, res, path);
                } catch (Exception e) {
                    handleException(e);
                }
            }

            private void handleLegacySession(HttpServletRequest req, String path) {
                if (LegacyPaths.USED_BY_LINK.equals(path)) {
                    String mutableSessionAttribute = "143|C_ORDER_ID";
                    String recordId = req.getParameter("recordId");
                    HttpSession session = req.getSession(true);

                    if (!LegacyUtils.isMutableSessionAttribute(mutableSessionAttribute)) {
                        throw new InternalServerException(
                                "Attempt to set forbidden session key: " + mutableSessionAttribute);
                    }

                    session.setAttribute(mutableSessionAttribute, recordId);
                }
            }

            private void forwardRequest(HttpServletRequest req, HttpServletResponse res, String path)
                    throws ServletException, IOException {

                RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher(path);

                if (dispatcher == null) {
                    throw new ServletException("No dispatcher found for path: " + path);
                }

                dispatcher.forward(req, res);
            }

            private void handleException(Exception e) throws ServletException, IOException {
                if (e instanceof ServletException)
                    throw (ServletException) e;
                if (e instanceof IOException)
                    throw (IOException) e;
                throw new InternalServerException("Failed to forward legacy request: " + e.getMessage(), e);
            }
        };
    }

    public static MetadataService getService(final HttpServletRequest req, final HttpServletResponse res) {
        final String path = req.getPathInfo() != null
                ? req.getPathInfo().replace("/com.etendoerp.metadata.meta/", "/")
                : "";

        // Check exact matches first
        if (EXACT_MATCH_SERVICES.containsKey(path)) {
            return EXACT_MATCH_SERVICES.get(path).apply(req, res);
        }

        // Check prefix matches
        for (Map.Entry<String, BiFunction<HttpServletRequest, HttpServletResponse, MetadataService>> entry : PREFIX_MATCH_SERVICES
                .entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue().apply(req, res);
            }
        }

        // Check legacy paths
        if (LegacyUtils.isLegacyPath(path)) {
            return buildLegacyForwardService(req, res, path);
        }

        throw new NotFoundException();
    }
}
