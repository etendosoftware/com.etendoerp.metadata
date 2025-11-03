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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.etendoerp.metadata.utils.Constants.*;

/**
 * @author luuchorocha
 */
public class ServiceFactory {

    private static MetadataService buildLegacyForwardService(HttpServletRequest req, HttpServletResponse res, String path) {
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
                if (e instanceof ServletException) throw (ServletException) e;
                if (e instanceof IOException) throw (IOException) e;
                throw new InternalServerException("Failed to forward legacy request: " + e.getMessage(), e);
            }
        };
    }

    public static MetadataService getService(final HttpServletRequest req, final HttpServletResponse res) {
        final String path = req.getPathInfo() != null ? req.getPathInfo().replace("/com.etendoerp.metadata.meta/", "/") : "";

        if (path.equals(SESSION_PATH)) {
            return new SessionService(req, res);
        } else if (path.equals(MENU_PATH)) {
            return new MenuService(req, res);
        } else if (path.startsWith(WINDOW_PATH)) {
            return new WindowService(req, res);
        } else if (path.startsWith(TAB_PATH)) {
            return new TabService(req, res);
        } else if (path.startsWith(LANGUAGE_PATH)) {
            return new LanguageService(req, res);
        } else if (path.equals(MESSAGE_PATH)) {
            return new MessageService(req, res);
        } else if (path.equals(LABELS_PATH)) {
            return new LabelsService(req, res);
        } else if (path.startsWith(LOCATION_PATH)) {
            return new LocationMetadataService(req, res);
        } else if (path.startsWith(TOOLBAR_PATH)) {
            return new ToolbarService(req, res);
        } else if (path.startsWith(LEGACY_PATH)) {
            return new LegacyService(req, res);
        } else if (LegacyUtils.isLegacyPath(path)) {
            return buildLegacyForwardService(req, res, path);
        } else {
            throw new NotFoundException();
        }
    }
}
