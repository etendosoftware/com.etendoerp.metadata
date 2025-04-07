package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.LanguageBuilder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class LanguageService extends MetadataService {
    public LanguageService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        write(new LanguageBuilder().toJSON());
    }
}
