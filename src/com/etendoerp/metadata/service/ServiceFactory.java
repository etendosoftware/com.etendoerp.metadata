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
            public void process() {
                try {
                    String recordsParam = req.getParameter("sessionParams");
                    if (recordsParam != null && !recordsParam.isEmpty()) {
                        ObjectMapper mapper = new ObjectMapper();

                        List<Map<String, String>> records = mapper.readValue(
                                recordsParam,
                                new TypeReference<List<Map<String, String>>>() {}
                        );

                        HttpSession session = req.getSession(true);

                        for (Map<String, String> record : records) {
                            for (Map.Entry<String, String> entry : record.entrySet()) {
                                session.setAttribute(entry.getKey(), entry.getValue());
                            }
                        }
                    }

                    RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher(path);

                    if (dispatcher == null) {
                        throw new ServletException("No dispatcher found for path: " + path);
                    }

                    dispatcher.forward(req, res);
                } catch (IOException e) {
                    throw new InternalServerException("Failed to parse 'records' JSON: " + e.getMessage());
                } catch (Exception e) {
                    throw new InternalServerException("Failed to forward legacy request: " + e.getMessage());
                }
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
