package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.builders.WindowBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WindowService extends MetadataService {
    public WindowService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        String id = request.getPathInfo().substring(8);
        write(new WindowBuilder(id).toJSON());
    }
}
