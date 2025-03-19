package com.etendoerp.metadata.service;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.weld.module.web.servlet.SessionHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseService {
    protected final Logger logger = LogManager.getLogger(this.getClass());
    protected final HttpServletRequest request;
    protected final HttpServletResponse response;

    public BaseService(HttpServletRequest request, HttpServletResponse response) {
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request);
        SessionHolder.requestInitialized(wrapped);

        this.request = wrapped;
        this.response = response;
    }

    protected void write(JSONObject data) {
        try {
            response.getWriter().write(data.toString());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException(e.toString());
        }
    }

    public abstract void process();
}
