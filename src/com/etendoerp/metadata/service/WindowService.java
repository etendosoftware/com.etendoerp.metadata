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

import com.etendoerp.metadata.builders.WindowBuilder;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */

/**
 * Creates a new {@code WindowService} instance using the given request and response.
 */
public class WindowService extends MetadataService {
    public WindowService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    /**
     * Processes the service request for a Window metadata.
     * <p>
     * This method extracts the window identifier from the request path, validates it,
     * and writes the corresponding Window metadata in JSON format to the response.
     * </p>
     *
     * @throws IOException if an error occurs while writing the response
     * @throws OBException if the window identifier is missing or invalid
     */
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
     * Extracts the window identifier from the given request path.
     * <p>
     * The method splits the path into segments and searches for the "window"
     * segment. The window identifier is expected to be the following segment.
     * If the identifier contains query parameters, they are removed.
     * </p>
     *
     * <pre>
     * Example:
     * Input path: /com.etendoerp.metadata.meta/window/115
     * Segments: ["", "com.etendoerp.metadata.meta", "window", "115"]
     * Result: "115"
     * </pre>
     *
     * @param pathInfo the path info from the HTTP request (may be {@code null})
     * @return the extracted window identifier, or {@code null} if not found
     */
    private String extractWindowId(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String[] segments = pathInfo.split("/");
        for (int i = 0; i < segments.length; i++) {
            if ("window".equals(segments[i]) && i + 1 < segments.length && !segments[i + 1].isEmpty()) {
                String id = segments[i + 1];
                int idx = id.indexOf('?');
                if (idx != -1) {
                    id = id.substring(0, idx);
                }
                return id;
            }
        }
        return null;
    }
}
