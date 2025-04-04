package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.builders.MenuBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MenuService extends MetadataService {
    public MenuService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        write(new MenuBuilder().toJSON());
    }
}
