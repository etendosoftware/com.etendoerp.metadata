package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.MenuBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MenuService extends BaseService {
    public MenuService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        write(new MenuBuilder().toJSON());
    }
}
