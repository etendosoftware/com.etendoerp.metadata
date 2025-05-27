package com.etendoerp.metadata.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openbravo.dal.core.OBContext;

import com.etendoerp.metadata.builders.WindowBuilder;

/**
 * @author luuchorocha
 */
public class WindowService extends MetadataService {
  public WindowService(HttpServletRequest request, HttpServletResponse response) {
    super(request, response);
  }

  @Override
  public void process() throws IOException {
    String id = getRequest().getPathInfo().substring(8);

    try {
      OBContext.setAdminMode(true);
      write(new WindowBuilder(id).toJSON());
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
