package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.LANGUAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.MENU_PATH;
import static com.etendoerp.metadata.utils.Constants.MESSAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.SERVLET_PATH;
import static com.etendoerp.metadata.utils.Constants.SESSION_PATH;
import static com.etendoerp.metadata.utils.Constants.TAB_PATH;
import static com.etendoerp.metadata.utils.Constants.TOOLBAR_PATH;
import static com.etendoerp.metadata.utils.Constants.WINDOW_PATH;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.authentication.AuthenticationManager;

/**
 * @author luuchorocha
 */
public class ServiceFactory {
    public static MetadataService getService(final HttpServletRequest req, final HttpServletResponse res) {
        final String path = req.getPathInfo();
        req.removeAttribute(AuthenticationManager.STATELESS_REQUEST_PARAMETER);

        if (path.startsWith(SERVLET_PATH)) {
            return new ServletService(req, res);
        } else if (path.equals(SESSION_PATH)) {
            return new SessionService(req, res);
        } else if (path.equals(MENU_PATH)) {
            return new MenuService(req, res);
        } else if (path.startsWith(WINDOW_PATH)) {
            return new WindowService(req, res);
        } else if (path.startsWith(TAB_PATH)) {
            return new TabService(req, res);
        } else if (path.startsWith(TOOLBAR_PATH)) {
            return new ToolbarService(req, res);
        } else if (path.startsWith(LANGUAGE_PATH)) {
            return new LanguageService(req, res);
        } else if (path.equals(MESSAGE_PATH)) {
            return new MessageService(req, res);
        } else {
            return new ServletService(req, res);
        }
    }
}

