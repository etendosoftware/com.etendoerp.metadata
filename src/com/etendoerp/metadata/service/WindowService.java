package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.WindowBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WindowService extends BaseService {
    public WindowService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        String id = request.getPathInfo().substring(8);
        write(new WindowBuilder(id).toJSON());
    }
}
