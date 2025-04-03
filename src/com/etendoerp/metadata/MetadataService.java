package com.etendoerp.metadata;

import com.etendoerp.metadata.exceptions.InternalServerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class MetadataService {

  protected final Logger logger = LogManager.getLogger(this.getClass());
  protected HttpServletRequest request;
  protected final HttpServletResponse response;

  public MetadataService(HttpServletRequest request, HttpServletResponse response) {
    this.request = request;
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

  protected HttpServletRequest getRequest() {
    return request;
  }

  protected void setRequest(HttpServletRequest request) {
    this.request = request;
  }
}
