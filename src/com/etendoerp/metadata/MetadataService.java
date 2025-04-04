package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.http.ServletRequestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.weld.module.web.servlet.SessionHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class MetadataService {
    protected final Logger logger = LogManager.getLogger(this.getClass());
    protected final HttpServletResponse response;
    protected final ServletRequestWrapper request;

    public MetadataService(HttpServletRequest request, HttpServletResponse response) {
        ServletRequestWrapper wrapped = new ServletRequestWrapper(request);
        SessionHolder.requestInitialized(wrapped);
        this.request = wrapped;
        this.response = response;
    }

    protected void write(JSONObject data) {
        try {
            response.setHeader("Content-type", "application/json");
            response.getWriter().write(data.toString());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw new InternalServerException(e.toString());
        }
    }

    public abstract void process();
}
