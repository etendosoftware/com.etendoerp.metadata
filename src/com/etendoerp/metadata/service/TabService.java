package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.TabBuilder;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author luuchorocha
 */
public class TabService extends MetadataService {
    public TabService(HttpSecureAppServlet caller, HttpServletRequest request, HttpServletResponse response) {
        super(caller, request, response);
    }

    @Override
    public void process() throws IOException {
        String tabId = getRequest().getPathInfo().substring(5);
        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        write(new TabBuilder(tab, null).toJSON());
    }
}
