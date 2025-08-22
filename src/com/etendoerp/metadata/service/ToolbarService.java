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

import com.etendoerp.metadata.builders.ToolbarBuilder;
import com.etendoerp.metadata.exceptions.InternalServerException;
import org.codehaus.jettison.json.JSONException;
import org.openbravo.dal.core.OBContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Toolbar Service - Provides toolbar metadata in JSON format.
 */
public class ToolbarService extends MetadataService {
  /**
   * Constructor for ToolbarService. Initializes the service with the given HTTP request and response.
   *
   * @param request  Request object containing client request information.
   * @param response Response object for sending data back to the client.
   */
  public ToolbarService(HttpServletRequest request, HttpServletResponse response) {
    super(request, response);
  }

  /**
   * Processes the request to retrieve toolbar metadata and writes it to the response.
   * The method sets the OBContext to admin mode to ensure it has the necessary permissions
   * to access the toolbar data. It then constructs a ToolbarBuilder instance, converts it to
   * JSON, and writes the JSON to the response. If a JSONException occurs during this process,
   * it throws an InternalServerException. Finally, it restores the previous OBContext mode.
   *
   * @throws IOException
   */
  @Override
  public void process() throws IOException {
    try {
      OBContext.setAdminMode(true);
      write(new ToolbarBuilder().toJSON());
    } catch (JSONException e) {
      throw new InternalServerException(e.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
