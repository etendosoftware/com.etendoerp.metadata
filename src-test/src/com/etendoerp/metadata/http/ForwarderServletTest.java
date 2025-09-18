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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for the new simplified {@link ForwarderServlet}.
 * <p>
 * This test class validates that all incoming requests are correctly delegated
 * to the {@link DataSourceServlet}, as per the new design.
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class ForwarderServletTest {

  private static final String TEST_PATH = "/some/path";

  private ForwarderServlet forwarderServlet;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private DataSourceServlet dataSourceServlet;

  /**
   * Initializes the {@link ForwarderServlet} instance before each test.
   * <p>
   * No request writers or sessions are mocked because the new
   * {@link ForwarderServlet} does not depend on them.
   * </p>
   */
  @Before
  public void setUp() {
    forwarderServlet = new ForwarderServlet();
  }

  /**
   * Tests that the {@link ForwarderServlet#process(HttpServletRequest, HttpServletResponse)}
   * method correctly delegates the request to the {@link DataSourceServlet}.
   * <p>
   * The {@link DataSourceServlet#doGet(HttpServletRequest, HttpServletResponse)}
   * method is expected to be called with the original request and response.
   * </p>
   *
   * @throws ServletException if the delegation fails
   * @throws IOException      if an I/O error occurs during delegation
   */
  @Test
  public void processShouldDelegateToDataSourceServlet() throws ServletException, IOException {
    try (MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {
      weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(DataSourceServlet.class))
              .thenReturn(dataSourceServlet);

      // Call the protected method directly since we're testing the base functionality
      forwarderServlet.process(request, response);

      verify(dataSourceServlet).doGet(request, response);
    }
  }

  /**
   * Tests that the {@link ForwarderServlet#doGet(String, HttpServletRequest, HttpServletResponse)}
   * method correctly calls the {@link ForwarderServlet#process(HttpServletRequest, HttpServletResponse)} method.
   *
   * @throws Exception if an error occurs during method invocation
   */
  @Test
  public void doGetShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doGet(TEST_PATH, request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that the {@link ForwarderServlet#doPost(String, HttpServletRequest, HttpServletResponse)}
   * method correctly calls the {@link ForwarderServlet#process(HttpServletRequest, HttpServletResponse)} method.
   *
   * @throws Exception if an error occurs during method invocation
   */
  @Test
  public void doPostShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doPost(TEST_PATH, request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that the {@link ForwarderServlet#doPut(String, HttpServletRequest, HttpServletResponse)}
   * method correctly calls the {@link ForwarderServlet#process(HttpServletRequest, HttpServletResponse)} method.
   *
   * @throws Exception if an error occurs during method invocation
   */
  @Test
  public void doPutShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doPut(TEST_PATH, request, response);

    verify(spyServlet).process(request, response);
  }

  /**
   * Tests that the {@link ForwarderServlet#doDelete(String, HttpServletRequest, HttpServletResponse)}
   * method correctly calls the {@link ForwarderServlet#process(HttpServletRequest, HttpServletResponse)} method.
   *
   * @throws Exception if an error occurs during method invocation
   */
  @Test
  public void doDeleteShouldCallProcessMethod() throws Exception {
    ForwarderServlet spyServlet = spy(forwarderServlet);

    spyServlet.doDelete(TEST_PATH, request, response);

    verify(spyServlet).process(request, response);
  }
}