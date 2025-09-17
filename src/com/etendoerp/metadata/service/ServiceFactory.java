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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.exceptions.NotFoundException;

import static com.etendoerp.metadata.utils.Constants.*;

/**
 * @author luuchorocha
 */
public class ServiceFactory {

    public static MetadataService getService(final HttpServletRequest req, final HttpServletResponse res) {
        final String path = req.getPathInfo().replace("/com.etendoerp.metadata.meta/", "/");

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
        } else {
            throw new NotFoundException();
        }
    }
}
