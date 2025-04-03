package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.http.ServletRequestWrapper;
import org.jboss.weld.module.web.servlet.SessionHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseService extends MetadataService {

    public BaseService(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        HttpServletRequest wrapped = new ServletRequestWrapper(request);
        SessionHolder.requestInitialized(wrapped);
        setRequest(wrapped);
    }

    public abstract void process();
}
