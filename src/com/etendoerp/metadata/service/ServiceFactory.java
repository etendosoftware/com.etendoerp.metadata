package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.LANGUAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.MENU_PATH;
import static com.etendoerp.metadata.utils.Constants.MESSAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.SESSION_PATH;
import static com.etendoerp.metadata.utils.Constants.TAB_PATH;
import static com.etendoerp.metadata.utils.Constants.TOOLBAR_PATH;
import static com.etendoerp.metadata.utils.Constants.WINDOW_PATH;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.base.secureApp.HttpSecureAppServlet;

/**
 * @author luuchorocha
 */
public class ServiceFactory {
    public static MetadataService getService(final HttpSecureAppServlet servlet, final HttpServletRequest req,
        final HttpServletResponse res) {
        final String path = req.getPathInfo();

        if (path.equals(SESSION_PATH)) {
            return new SessionService(servlet, req, res);
        } else if (path.equals(MENU_PATH)) {
            return new MenuService(servlet, req, res);
        } else if (path.startsWith(WINDOW_PATH)) {
            return new WindowService(servlet, req, res);
        } else if (path.startsWith(TAB_PATH)) {
            return new TabService(servlet, req, res);
        } else if (path.startsWith(TOOLBAR_PATH)) {
            return new ToolbarService(servlet, req, res);
        } else if (path.startsWith(LANGUAGE_PATH)) {
            return new LanguageService(servlet, req, res);
        } else if (path.equals(MESSAGE_PATH)) {
            return new MessageService(servlet, req, res);
        } else {
            return new ServletService(servlet, req, res);
        }
    }
}

