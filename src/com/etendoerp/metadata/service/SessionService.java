package com.etendoerp.metadata.service;

import com.etendoerp.metadata.SessionManager;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.data.RequestVariables;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SessionService extends BaseService {
    public SessionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() {
        write(new SessionBuilder().toJSON());
    }
}
