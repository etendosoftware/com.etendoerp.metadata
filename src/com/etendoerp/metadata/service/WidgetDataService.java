package com.etendoerp.metadata.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WidgetDataService extends MetadataService {

    public WidgetDataService(HttpServletRequest req, HttpServletResponse res) {
        super(req, res);
    }

    @Override
    public void process() {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
