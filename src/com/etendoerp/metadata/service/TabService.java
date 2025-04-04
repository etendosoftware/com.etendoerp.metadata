package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.builders.TabBuilder;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.ui.Tab;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TabService extends MetadataService {
    public TabService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        String tabId = request.getPathInfo().substring(5);
        Tab tab = OBDal.getInstance().get(Tab.class, tabId);
        write(new TabBuilder(tab, null).toJSON());
    }
}
