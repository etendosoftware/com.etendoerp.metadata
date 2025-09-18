package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.service.datasource.DataSourceServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the new simplified ForwarderServlet.
 * This test class validates that all incoming requests are correctly delegated
 * to the DataSourceServlet, as per the new design.
 */
@RunWith(MockitoJUnitRunner.class)
public class ForwarderServletTest {

  private ForwarderServlet forwarderServlet;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private DataSourceServlet dataSourceServlet;

  @Before
  public void setUp() {
    forwarderServlet = new ForwarderServlet();
    // No need to mock response writer or sessions as the new servlet doesn't use them.
  }

  /**
   * Tests that the `process` method correctly delegates the request to the `DataSourceServlet`.
   * This is the primary and only responsibility of the new ForwarderServlet.
   */
  @Test
  public void processShouldDelegateToDataSourceServlet() throws ServletException, IOException {
    try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
      weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
              .thenReturn(dataSourceServlet);

      forwarderServlet.process(request, response);

      // Verify that the doGet method of DataSourceServlet was called with the original request and response.
      verify(dataSourceServlet).doGet(request, response);
    }
  }

  /**
   * Tests that `doGet` method correctly calls the `process` method.
   */
  @Test
  public void doGetShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doGet("/some/path", request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that `doPost` method correctly calls the `process` method.
   */
  @Test
  public void doPostShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doPost("/some/path", request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that `doPut` method correctly calls the `process` method.
   */
  @Test
  public void doPutShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doPut("/some/path", request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that `doDelete` method correctly calls the `process` method.
   */
  @Test
  public void doDeleteShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doDelete("/some/path", request, response);

    verify(spyServlet).process(request, response);
  }
}