package com.etendoerp.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class MetadataService {

    protected final Logger logger = LogManager.getLogger(this.getClass());
    protected final HttpServletResponse response;
    protected HttpServletRequest request;

    public MetadataService(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    protected void write(JSONObject data) throws IOException {
        response.getWriter().write(data.toString());
    }

    public abstract void process() throws Exception;

    protected HttpServletRequest getRequest() {
        return request;
    }

    protected void setRequest(HttpServletRequest request) {
        this.request = request;
    }
}
