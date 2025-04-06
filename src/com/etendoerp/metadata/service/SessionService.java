package com.etendoerp.metadata.service;

import com.etendoerp.metadata.auth.SessionManager;
import com.etendoerp.metadata.builders.SessionBuilder;
import com.etendoerp.metadata.data.RequestVariables;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SessionService extends BaseService {
    public SessionService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
    }

    @Override
    public void process() throws IOException {
        RequestVariables vars = SessionManager.initializeSession(request);
        write(new SessionBuilder(vars).toJSON());
    }
}
