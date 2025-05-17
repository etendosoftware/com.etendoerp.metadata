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
import com.smf.securewebservices.SWSConfig;

/**
 * @author luuchorocha
 */
public class LoginServlet extends HttpBaseServlet {
  private final LoginManager manager = new LoginManager();

  @Override
  public final void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    super.service(HttpServletRequestWrapper.wrap(req), res);
  }

  @Override
  public final void doOptions(HttpServletRequest req, HttpServletResponse res) {
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    AllowedCrossDomainsHandler.getInstance().setCORSHeaders(req, res);

    try {
      OBContext.setAdminMode(true);
      validateConfig();
      res.setContentType(ContentType.APPLICATION_JSON.getMimeType());
      res.setCharacterEncoding(StandardCharsets.UTF_8.name());
      res.getWriter().write(manager.processLogin(HttpServletRequestWrapper.wrap(req)).toString());
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
