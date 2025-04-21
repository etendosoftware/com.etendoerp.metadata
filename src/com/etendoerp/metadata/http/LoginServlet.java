package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.exceptions.Utils.getResponseStatus;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.HttpBaseServlet;
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
    @Override
    public final void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        super.service(HttpServletRequestWrapper.wrap(req), res);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            validateConfig();
            OBContext.setAdminMode(true);
            res.getWriter().write(new LoginManager().processLogin(req).toString());
        } catch (Exception e) {
            log4j.error(e.getMessage(), e);
            res.setStatus(getResponseStatus(e));
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
