package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.metadata.builders.TabBuilder;
import com.etendoerp.metadata.exceptions.NotFoundException;

/**
 * @author luuchorocha
 */
public class TabService extends MetadataService {
    public TabService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            String pathInfo = getRequest().getPathInfo();
            String tabId = extractTabId(pathInfo);

            if (tabId == null || tabId.isEmpty()) {
                throw new NotFoundException("Invalid tab path: " + pathInfo);
            }

            Tab tab = OBDal.getInstance().get(Tab.class, tabId);

            if (tab == null) {
                throw new NotFoundException("Tab not found: " + tabId);
            }

            write(new TabBuilder(tab, null).toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Extract the ID of the tab path from request
     * Handles paths like: /etendo/sws/com.etendoerp.metadata.meta/tab/3ACD18ADFBA8406086852B071250C481
     * Or just: /tab/3ACD18ADFBA8406086852B071250C481
     */
    private String extractTabId(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        int tabIndex = pathInfo.indexOf("/tab/");
        if (tabIndex == -1) {
            return null;
        }

        String tabIdPart = pathInfo.substring(tabIndex + 5);

        int queryIndex = tabIdPart.indexOf('?');
        if (queryIndex != -1) {
            tabIdPart = tabIdPart.substring(0, queryIndex);
        }

        if (tabIdPart.endsWith("/")) {
            tabIdPart = tabIdPart.substring(0, tabIdPart.length() - 1);
        }

        return tabIdPart;
    }
}