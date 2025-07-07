package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.utils.Constants.LABELS_PATH;
import static com.etendoerp.metadata.utils.Constants.LANGUAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.MENU_PATH;
import static com.etendoerp.metadata.utils.Constants.MESSAGE_PATH;
import static com.etendoerp.metadata.utils.Constants.SESSION_PATH;
import static com.etendoerp.metadata.utils.Constants.TAB_PATH;
import static com.etendoerp.metadata.utils.Constants.WINDOW_PATH;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * @author luuchorocha
 */
public class ServiceFactory {
    private static final String LOCATION_PATH = "/location";

    public static MetadataService getService(final HttpServletRequest req, final HttpServletResponse res) {
        final String path = req.getPathInfo();

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
        } else {
            throw new NotFoundException();
        }
    }
}