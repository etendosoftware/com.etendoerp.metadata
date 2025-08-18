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

package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.utils.Utils.getHttpStatusFor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.openbravo.base.HttpBaseServlet;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonUtils;

import com.etendoerp.metadata.auth.LoginManager;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.Utils;
import com.smf.securewebservices.SWSConfig;

/**
 * @author luuchorocha
 */
public class LoginServlet extends HttpBaseServlet {
  private final LoginManager manager = new LoginManager();

  @Override
  public final void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    super.service(req, res);
  }

  @Override
  public final void doOptions(HttpServletRequest req, HttpServletResponse res) {
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);
    String iscFormat = req.getParameter("isc_dataFormat");
    boolean wantsJson = "json".equalsIgnoreCase(iscFormat);

    if (wantsJson) {
      Utils.writeJsonErrorResponse(res, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
          "Use POST with JSON body to login at /meta/login");
    } else {
      res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      res.setContentType("text/plain");
      res.setCharacterEncoding(StandardCharsets.UTF_8.name());
      res.getWriter().write("Method GET not allowed on /meta/login. Use POST.");
      res.getWriter().flush();
    }
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);
      OBContext.setAdminMode(true);
      validateConfig();
      res.setContentType(ContentType.APPLICATION_JSON.getMimeType());
      res.setCharacterEncoding(StandardCharsets.UTF_8.name());
      res.getWriter().write(manager.processLogin(req).toString());
    } catch (Exception e) {
      log4j.error(e.getMessage(), e);
      res.setStatus(getHttpStatusFor(e));
      res.getWriter().write(JsonUtils.convertExceptionToJson(e));
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void validateConfig() {
    if (SWSConfig.getInstance().getPrivateKey() == null) {
      throw new InternalServerException(Constants.SWS_SWS_ARE_MISCONFIGURED);
    }
  }
}
