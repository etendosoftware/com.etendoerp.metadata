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

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.WindowBuilder;

/**
 * @author luuchorocha
 */
public class WindowService extends MetadataService {
    public WindowService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String path = getRequest().getPathInfo();
        String windowId = extractWindowId(path);

        if (windowId == null || windowId.isEmpty()) {
            throw new OBException("Invalid window id in URL: " + path);
        }

        try {
            OBContext.setAdminMode(true);
            write(new WindowBuilder(windowId).toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Extract the id of the window from path
     * Handle paths like: /com.etendoerp.metadata.meta/window/115
     */
    private String extractWindowId(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }

        int windowIndex = pathInfo.indexOf("/window/");
        if (windowIndex == -1) {
            return null;
        }

        String windowIdPart = pathInfo.substring(windowIndex + 8); // +8 para saltar "/window/"

        int queryIndex = windowIdPart.indexOf('?');
        if (queryIndex != -1) {
            windowIdPart = windowIdPart.substring(0, queryIndex);
        }

        if (windowIdPart.endsWith("/")) {
            windowIdPart = windowIdPart.substring(0, windowIdPart.length() - 1);
        }

        return windowIdPart;
    }
}
