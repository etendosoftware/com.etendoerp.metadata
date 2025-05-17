package com.etendoerp.metadata.http;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.service.ServiceFactory;

/**
 * @author luuchorocha
 */
public class MetadataServlet extends BaseServlet {
  private void process(HttpServletRequest req, HttpServletResponse res) throws IOException {
    ServiceFactory.getService(req, res).process();
  }

  @Override
  public final void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    process(req, res);
  }

  @Override
  public final void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    process(req, res);
  }

  @Override
  public final void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
    process(req, res);
  }

  @Override
  public final void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
    process(req, res);
  }
}
