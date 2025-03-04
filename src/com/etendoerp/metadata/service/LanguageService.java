package com.etendoerp.metadata.service;

import com.etendoerp.metadata.builders.LanguageBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LanguageService extends BaseService {
    public LanguageService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        write(new LanguageBuilder().toJSON());
    }
}
