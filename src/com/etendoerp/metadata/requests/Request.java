package com.etendoerp.metadata.requests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class Request {
    protected final Logger logger;
    protected final HttpServletRequest request;
    protected final HttpServletResponse response;

    public Request(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
        this.logger = LogManager.getLogger(this.getClass());
    }

    public abstract void process();
}
