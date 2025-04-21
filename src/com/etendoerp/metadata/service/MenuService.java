package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.MenuBuilder;

/**
 * @author luuchorocha
 */
public class MenuService extends MetadataService {
    public MenuService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode();
            write(new MenuBuilder().toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
