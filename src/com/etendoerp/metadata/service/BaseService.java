package com.etendoerp.metadata.service;

import com.etendoerp.metadata.MetadataService;
import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.http.HttpServletRequestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.weld.module.web.servlet.SessionHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseService extends MetadataService {

    public BaseService(HttpServletRequest request, HttpServletResponse response) {
      super(request, response);
      HttpServletRequest wrapped = new HttpServletRequestWrapper(request);
      SessionHolder.requestInitialized(wrapped);
      setRequest(wrapped);
    }

    public abstract void process();
}
