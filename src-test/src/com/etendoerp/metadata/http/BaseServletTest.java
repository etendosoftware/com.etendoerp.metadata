package com.etendoerp.metadata.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.authentication.AuthenticationManager;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.Utils;

/**
 * Test class for BaseServlet, which is a base class for handling HTTP requests in Etendo.
 * This class tests the service method and other functionalities of BaseServlet.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class BaseServletTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private HttpSession session;

  @Mock
  private ServletConfig config;

  @Mock
  private ServletContext servletContext;

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private OBContext obContext;

  @Mock
  private RequestContext requestContext;

  @Mock
  private VariablesSecureApp variablesSecureApp;

  @Mock
  private AllowedCrossDomainsHandler corsHandler;

  @Mock
  private Language language;

  @Mock
  private Client client;

  @Mock
  private Role role;

  @Mock
  private Organization organization;

  @Mock
  private Warehouse warehouse;

  @Mock
  private User user;

  @Mock
  private HttpServletRequestWrapper requestWrapper;

  private BaseServlet servlet;

  /**
   * Sets up the test environment before each test.
   * It initializes the BaseServlet and mocks the necessary objects.
   * This method is called before each test to ensure a clean state.
   *
   * @throws IOException if an I/O error occurs during setup
   */
  @BeforeEach
  void setUp() throws IOException {
    servlet = new BaseServlet();
    StringWriter responseWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(responseWriter);

    when(response.getWriter()).thenReturn(printWriter);
    when(request.getSession(false)).thenReturn(session);
    when(request.getMethod()).thenReturn("GET");
    when(config.getServletContext()).thenReturn(servletContext);
    when(servletContext.getRealPath(anyString())).thenReturn("/test/path");

    when(obContext.getUser()).thenReturn(user);
    when(user.getId()).thenReturn("testUserId");
    when(obContext.getLanguage()).thenReturn(language);
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getRole()).thenReturn(role);
    when(obContext.getCurrentOrganization()).thenReturn(organization);
    when(obContext.getWarehouse()).thenReturn(warehouse);
    when(obContext.isRTL()).thenReturn(false);

    when(language.getLanguage()).thenReturn("en_US");
    when(client.getId()).thenReturn("clientId");
    when(role.getId()).thenReturn("roleId");
    when(organization.getId()).thenReturn("orgId");
    when(warehouse.getId()).thenReturn("warehouseId");
  }

  /**
   * Tests the service method of BaseServlet.
   * It checks if the service method is called with the correct parameters and if it handles the request correctly.
   *
   * @throws IOException if an I/O error occurs during the test
   * @throws ServletException if a servlet-specific error occurs during the test
   */
  @Test
  void testServiceMethodCallsServiceWithDefaults() throws IOException, ServletException {
    BaseServlet spyServlet = spy(servlet);
    doNothing().when(spyServlet).service(any(HttpServletRequest.class), any(HttpServletResponse.class), anyBoolean(), anyBoolean());

    spyServlet.service(request, response);

    verify(spyServlet).service(any(HttpServletRequest.class), eq(response), eq(true), eq(true));
  }

  /**
   * Tests the service method of BaseServlet with a successful service call.
   * It checks if the service method is called without throwing exceptions.
   *
   * @throws IOException if an I/O error occurs during the test
   */
  @Test
  void testServiceWithOptionsMethod() throws IOException {
    when(request.getMethod()).thenReturn("OPTIONS");

    try (MockedStatic<HttpServletRequestWrapper> wrapperMock = mockStatic(HttpServletRequestWrapper.class);
         MockedStatic<AllowedCrossDomainsHandler> corsMock = mockStatic(AllowedCrossDomainsHandler.class)) {

      wrapperMock.when(() -> HttpServletRequestWrapper.wrap(request)).thenReturn(requestWrapper);
      when(requestWrapper.getMethod()).thenReturn("OPTIONS");
      corsMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.service(request, response, false, false);

      verify(corsHandler).setCORSHeaders(requestWrapper, response);
    }
  }

  /**
   * Tests that the service method of BaseServlet handles exceptions correctly.
   * <p>
   * When an exception is thrown during request processing, the method should set the HTTP status code
   * and write a JSON error response if the response is not already committed.
   * </p>
   *
   * @throws IOException if an I/O error occurs during the test
   */
  @Test
  void testServiceHandlesException() throws IOException {
    when(request.getMethod()).thenReturn("GET");
    when(response.isCommitted()).thenReturn(false);

    try (MockedStatic<HttpServletRequestWrapper> wrapperMock = mockStatic(HttpServletRequestWrapper.class);
         MockedStatic<AllowedCrossDomainsHandler> corsMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      RuntimeException testException = new RuntimeException("Test exception");
      wrapperMock.when(() -> HttpServletRequestWrapper.wrap(request)).thenThrow(testException);
      corsMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      utilsMock.when(() -> Utils.getHttpStatusFor(testException)).thenReturn(500);

      JSONObject jsonError = new JSONObject();
      jsonError.put("error", "Test exception");
      utilsMock.when(() -> Utils.convertToJson(testException)).thenReturn(jsonError);

      servlet.service(request, response, false, false);

      verify(response).setStatus(500);
      verify(response).getWriter();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Tests that the service method of BaseServlet does not write a response when the response is already committed.
   * <p>
   * When the response is committed, the service method should not attempt to write any further output.
   * </p>
   *
   * @throws IOException if an I/O error occurs during the test
   */
  @Test
  void testServiceDoesNotWriteResponseWhenCommitted() throws IOException {
    when(request.getMethod()).thenReturn("GET");
    when(response.isCommitted()).thenReturn(true);

    try (MockedStatic<HttpServletRequestWrapper> wrapperMock = mockStatic(HttpServletRequestWrapper.class)) {
      RuntimeException testException = new RuntimeException("Test exception");
      wrapperMock.when(() -> HttpServletRequestWrapper.wrap(request)).thenThrow(testException);

      servlet.service(request, response, false, false);

      verify(response, never()).setStatus(anyInt());
      verify(response, never()).getWriter();
    }
  }

  /**
   * Tests the initialization of the session in BaseServlet.
   * It checks if the OBContext is set correctly and if the session is initialized with the correct parameters.
   */
  @Test
  void testInitializeSessionWithNullContext() {
    try (MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
      obContextMock.when(OBContext::getOBContext).thenReturn(null);

      assertThrows(InternalServerException.class, BaseServlet::initializeSession);
    }
  }

  /**
   * Tests the doOptions method of BaseServlet.
   * It checks if the CORS headers are set correctly when the OPTIONS method is called.
   */
  @Test
  void testDoOptions() {
    try (MockedStatic<AllowedCrossDomainsHandler> corsMock = mockStatic(AllowedCrossDomainsHandler.class)) {
      corsMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doOptions(request, response);

      verify(corsHandler).setCORSHeaders(request, response);
    }
  }

  /**
   * Tests the service method of BaseServlet with a successful service call.
   * It checks if the service method is called without throwing exceptions.
   */
  @Test
  void testBypassCSRFWithNullSession() throws IOException, ServletException {
    when(request.getSession(false)).thenReturn(null);

    setupSuccessfulService();

    assertDoesNotThrow(() -> servlet.service(request, response, false, true));
  }

  /**
   * Sets up a successful service call for the BaseServlet by initializing all required mocks and static mocks.
   * This method prepares the environment to simulate a successful HTTP request handling,
   * including authentication, context, and utility classes.
   *
   * @throws IOException if an I/O error occurs during setup
   * @throws ServletException if a servlet-specific error occurs during setup
   */
  private void setupSuccessfulService() throws IOException, ServletException {
    setupMocksForService();

    try (MockedStatic<HttpServletRequestWrapper> wrapperMock = mockStatic(HttpServletRequestWrapper.class);
         MockedStatic<AllowedCrossDomainsHandler> corsMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class);
         MockedStatic<AuthenticationManager> authManagerMock = mockStatic(AuthenticationManager.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      setupCommonMocks(wrapperMock, corsMock, requestContextMock, authManagerMock, utilsMock);
      obContextMock.when(OBContext::getOBContext).thenReturn(obContext);
    }
  }

  /**
   * Sets up the necessary mocks for the BaseServlet service method.
   * This includes mocking the authentication manager, request context, and request wrapper.
   *
   * @throws ServletException if a servlet-specific error occurs during setup
   * @throws IOException if an I/O error occurs during setup
   */
  private void setupMocksForService() throws ServletException, IOException {
    when(authenticationManager.authenticate(any(HttpServletRequest.class), eq(response))).thenReturn("testUserId");
    when(requestContext.getVariablesSecureApp()).thenReturn(variablesSecureApp);
    when(requestWrapper.getMethod()).thenReturn("GET");
  }

  /**
   * Sets up common mocks used across multiple tests in BaseServlet.
   * This method initializes the static mocks and sets up the necessary behavior for the request wrapper,
   * CORS handler, request context, authentication manager, and utility methods.
   *
   * @param wrapperMock the mocked static HttpServletRequestWrapper
   * @param corsMock the mocked static AllowedCrossDomainsHandler
   * @param requestContextMock the mocked static RequestContext
   * @param authManagerMock the mocked static AuthenticationManager
   * @param utilsMock the mocked static Utils
   */
  private void setupCommonMocks(MockedStatic<HttpServletRequestWrapper> wrapperMock,
      MockedStatic<AllowedCrossDomainsHandler> corsMock,
      MockedStatic<RequestContext> requestContextMock,
      MockedStatic<AuthenticationManager> authManagerMock,
      MockedStatic<Utils> utilsMock) {

    wrapperMock.when(() -> HttpServletRequestWrapper.wrap(request)).thenReturn(requestWrapper);
    corsMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
    requestContextMock.when(RequestContext::get).thenReturn(requestContext);
    authManagerMock.when(() -> AuthenticationManager.getAuthenticationManager(any())).thenReturn(authenticationManager);
    utilsMock.when(() -> Utils.setContext(requestWrapper)).thenAnswer(invocation -> null);
  }
}
