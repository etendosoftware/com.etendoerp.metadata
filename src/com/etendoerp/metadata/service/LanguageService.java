package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.LanguageBuilder;

/**
 * @author luuchorocha
 */
public class LanguageService extends MetadataService {
    public LanguageService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        try {
            OBContext.setAdminMode(true);
            write(new LanguageBuilder().toJSON());
        } finally {
            OBContext.restorePreviousMode();
        }
    }
}
