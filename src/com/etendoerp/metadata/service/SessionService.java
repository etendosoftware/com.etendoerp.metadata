package com.etendoerp.metadata.service;

import com.etendoerp.metadata.SessionManager;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.data.RequestVariables;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SessionService extends BaseService {
    private final RequestVariables vars;

    public SessionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        this.vars = SessionManager.initializeSession(request, false);
    }

    @Override
    public void process() throws IOException {
        write(new SessionBuilder(vars).toJSON());
    }
}
