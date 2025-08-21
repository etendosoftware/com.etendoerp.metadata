package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.MetadataTestConstants.API_DATA_PATH;
import static com.etendoerp.metadata.MetadataTestConstants.JWT_TOKEN_HASH;
import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.test.base.OBBaseTest;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

/**
 * Unit tests for the ForwarderServlet class.
 *
 * <p>This class tests the functionality of the ForwarderServlet, ensuring that it
 * correctly handles legacy HTML requests, manages JWT tokens, and processes
 * request contexts. It uses Mockito for mocking dependencies and JUnit for assertions.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class ForwarderServletTest extends OBBaseTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private HttpSession session;

  @Mock
  private RequestDispatcher requestDispatcher;

  @Mock
  private PrintWriter printWriter;

  @Mock
  private RequestContext requestContext;

  @Mock
  private DecodedJWT decodedJWT;

  @Mock
  private Claim claim;

  private ForwarderServlet forwarderServlet;

  /**
   * Sets up the test environment before each test method execution.
   *
   * <p>This method initializes the ForwarderServlet instance and configures
   * mock objects with default behaviors. It sets up the response writer,
   * session, and request dispatcher mocks to return appropriate values.</p>
   *
   * @throws Exception
   *     if setup fails due to servlet initialization issues
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    forwarderServlet = new ForwarderServlet();
    StringWriter stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    when(response.getWriter()).thenReturn(printWriter);
    when(request.getSession()).thenReturn(session);
    when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
  }

  /**
   * Tests that the service method correctly handles legacy HTML requests.
   *
   * <p>This test verifies that when a request with an HTML path is received,
   * the servlet properly identifies it as a legacy request and forwards it
   * to the appropriate request dispatcher for processing.</p>
   *
   * @throws Exception
   *     if servlet processing fails or mock setup encounters issues
   */
  @Test
  public void serviceShouldHandleLegacyHtmlRequest() throws Exception {
    String path = SALES_INVOICE_HEADER_EDITION_HTML;
    when(request.getPathInfo()).thenReturn(path);

    when(request.getParameter(TOKEN)).thenReturn(null);
    when(session.getAttribute(JWT_TOKEN_HASH)).thenReturn(null);

    try (MockedStatic<RequestContext> contextMock = mockStatic(RequestContext.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      contextMock.when(RequestContext::get).thenReturn(requestContext);

      forwarderServlet.service(request, response);

      verify(requestDispatcher).include(any(), any());
    }
  }

  /**
   * Tests the legacy request detection logic for various path patterns.
   *
   * <p>This test validates that the isLegacyRequest method correctly identifies
   * HTML files as legacy requests while properly rejecting non-HTML paths.
   * It tests both uppercase and lowercase HTML extensions.</p>
   *
   * @throws Exception
   *     if reflection access to private method fails
   */
  @Test
  public void isLegacyRequestShouldReturnTrueForHtmlFiles() throws Exception {
    assertTrue(invokeIsLegacyRequest("/test/page.html"));
    assertTrue(invokeIsLegacyRequest("/test/page.HTML"));
    assertTrue(invokeIsLegacyRequest(SALES_INVOICE_HEADER_EDITION_HTML));

    assertFalse(invokeIsLegacyRequest(API_DATA_PATH));
    assertFalse(invokeIsLegacyRequest("/test/page.jsp"));
    assertFalse(invokeIsLegacyRequest("/test/page"));
  }

  /**
   * Tests token storage functionality when a token parameter is provided.
   *
   * <p>This test verifies that when a JWT token is passed as a request parameter,
   * the handleTokenConsistency method properly stores it in the HTTP session
   * for future use.</p>
   *
   * @throws Exception
   *     if reflection access fails or token handling encounters issues
   */
  @Test
  public void handleTokenConsistencyShouldStoreTokenWhenProvided() throws Exception {
    String token = "test-jwt-token";
    when(request.getParameter(TOKEN)).thenReturn(token);

    HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);

    invokeHandleTokenConsistency(request, wrapper);

    verify(session).setAttribute(JWT_TOKEN_HASH, token);
  }

  /**
   * Tests token decoding from session when no parameter token is provided.
   *
   * <p>This test validates that when no token parameter is present, the method
   * retrieves the token from the session and properly decodes it using the
   * SecureWebServicesUtils. It verifies that JWT claims are correctly extracted.</p>
   *
   * @throws Exception
   *     if token decoding fails or reflection access encounters issues
   */
  @Test
  public void handleTokenConsistencyShouldDecodeTokenFromSession() throws Exception {
    String sessionToken = "session-jwt-token";
    String sessionId = "test-session-id";

    when(request.getParameter(TOKEN)).thenReturn(null);
    when(session.getAttribute(JWT_TOKEN_HASH)).thenReturn(sessionToken);

    try (MockedStatic<SecureWebServicesUtils> swsUtilsMock = mockStatic(SecureWebServicesUtils.class)) {
      swsUtilsMock.when(() -> SecureWebServicesUtils.decodeToken(sessionToken))
          .thenReturn(decodedJWT);
      when(decodedJWT.getClaims()).thenReturn(java.util.Map.of("jti", claim));
      when(claim.asString()).thenReturn(sessionId);

      HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
      invokeHandleTokenConsistency(request, wrapper);

      swsUtilsMock.verify(() -> SecureWebServicesUtils.decodeToken(sessionToken));
    }
  }

  /**
   * Tests exception handling when token decoding fails.
   *
   * <p>This test ensures that when an invalid JWT token is encountered during
   * decoding, the method properly throws an OBException. This is critical for
   * security as it prevents processing of malformed or tampered tokens.</p>
   */
  @Test
  public void handleTokenConsistencyShouldThrowExceptionOnDecodeError() {
    String sessionToken = "invalid-jwt-token";

    when(request.getParameter(TOKEN)).thenReturn(null);
    when(session.getAttribute(JWT_TOKEN_HASH)).thenReturn(sessionToken);

    try (MockedStatic<SecureWebServicesUtils> swsUtilsMock = mockStatic(SecureWebServicesUtils.class)) {
      swsUtilsMock.when(() -> SecureWebServicesUtils.decodeToken(sessionToken))
          .thenThrow(new RuntimeException("Invalid token"));

      HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);

      try {
        invokeHandleTokenConsistency(request, wrapper);
        fail("Expected OBException");
      } catch (Exception e) {
        // Expected due to reflection wrapping, check the cause
        assertTrue(e.getCause() instanceof OBException ||
            (e.getCause() != null && e.getCause().getCause() instanceof OBException));
      }
    }
  }

  /**
   * Tests record identifier storage when all required parameters are present.
   *
   * <p>This test validates that when inpKey, inpwindowId, and inpkeyColumnId
   * parameters are all provided, the method correctly stores the record identifier
   * in the session using the proper key format (windowId|columnId).</p>
   *
   * @throws Exception
   *     if reflection access fails or parameter handling encounters issues
   */
  @Test
  public void handleRecordIdentifierShouldStoreWhenAllParametersPresent() throws Exception {
    String inpKeyId = "key-123";
    String inpWindowId = "window-456";
    String inpKeyColumnId = "column-789";

    HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
    when(wrapper.getParameter("inpKey")).thenReturn(inpKeyId);
    when(wrapper.getParameter("inpwindowId")).thenReturn(inpWindowId);
    when(wrapper.getParameter("inpkeyColumnId")).thenReturn(inpKeyColumnId);
    when(wrapper.getSession()).thenReturn(session);

    invokeHandleRecordIdentifier(wrapper);

    verify(session).setAttribute(inpWindowId + "|" + inpKeyColumnId.toUpperCase(), inpKeyId);
  }

  /**
   * Tests that record identifier is not stored when required parameters are missing.
   *
   * <p>This test ensures that when any of the required parameters (inpKey, inpwindowId,
   * or inpkeyColumnId) are missing, no session attribute is set. This prevents
   * incomplete or invalid record identifiers from being stored.</p>
   *
   * @throws Exception
   *     if reflection access fails or parameter validation encounters issues
   */
  @Test
  public void handleRecordIdentifierShouldNotStoreWhenParametersMissing() throws Exception {
    HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
    when(wrapper.getParameter("inpKey")).thenReturn("key-123");
    when(wrapper.getParameter("inpwindowId")).thenReturn(null);
    when(wrapper.getParameter("inpkeyColumnId")).thenReturn("column-789");

    invokeHandleRecordIdentifier(wrapper);

    verify(session, never()).setAttribute(anyString(), anyString());
  }

  /**
   * Tests the request context setup functionality.
   *
   * <p>This test validates that the handleRequestContext method properly configures
   * the RequestContext and OBContext with the provided request and response objects.
   * It also verifies that RequestVariables are correctly set up for secure app processing.</p>
   *
   * @throws Exception
   *     if context setup fails or reflection access encounters issues
   */
  @Test
  public void handleRequestContextShouldSetupContext() throws Exception {
    HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);

    try (MockedStatic<RequestContext> contextMock = mockStatic(RequestContext.class);
         MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {

      contextMock.when(RequestContext::get).thenReturn(requestContext);

      invokeHandleRequestContext(response, wrapper);

      verify(requestContext).setRequest(wrapper);
      verify(requestContext).setResponse(response);
      verify(requestContext).setVariableSecureApp(any(RequestVariables.class));
      obContextMock.verify(() -> OBContext.setOBContext(wrapper));
    }
  }

  /**
   * Tests legacy class validation for existing and non-existing classes.
   *
   * <p>This test verifies that the maybeValidateLegacyClass method properly validates
   * the existence of legacy WAD servlet classes. When a class doesn't exist, it should
   * throw an OBException with a descriptive message indicating the missing class.</p>
   *
   * @throws Exception
   *     if class validation fails or reflection access encounters issues
   */
  @Test
  public void maybeValidateLegacyClassShouldValidateExistingClass() throws Exception {
    String pathInfo = SALES_INVOICE_HEADER_EDITION_HTML;

    // This should not throw exception for existing classes
    try {
      invokeMaybeValidateLegacyClass(pathInfo);
    } catch (OBException e) {
      // If class doesn't exist, we expect this exception with specific message
      assertTrue(e.getMessage().contains("Legacy WAD servlet not found"));
      assertTrue(e.getMessage().contains("org.openbravo.erpWindows.SalesInvoice.Header"));
    }
  }

  /**
   * Tests that non-legacy paths are ignored during legacy class validation.
   *
   * <p>This test ensures that paths that don't correspond to legacy HTML files
   * are not processed by the legacy class validation logic, preventing unnecessary
   * class lookup attempts for modern API endpoints.</p>
   *
   * @throws Exception
   *     if validation logic fails or reflection access encounters issues
   */
  @Test
  public void maybeValidateLegacyClassShouldIgnoreNonLegacyPaths() throws Exception {
    String pathInfo = API_DATA_PATH;

    // Should not throw exception for non-legacy paths
    invokeMaybeValidateLegacyClass(pathInfo);
  }

  /**
   * Tests the legacy class name derivation logic.
   *
   * <p>This test validates that the deriveLegacyClass method correctly transforms
   * HTML file paths into their corresponding Java class names following the
   * org.openbravo.erpWindows package convention. It also tests handling of invalid paths.</p>
   *
   * @throws Exception
   *     if class name derivation fails or reflection access encounters issues
   */
  @Test
  public void deriveLegacyClassShouldCreateCorrectClassName() throws Exception {
    String result1 = invokeDerivateLegacyClass(SALES_INVOICE_HEADER_EDITION_HTML);
    assertEquals("org.openbravo.erpWindows.SalesInvoice.Header", result1);

    String result2 = invokeDerivateLegacyClass("/ProductMgmt/Product.html");
    assertEquals("org.openbravo.erpWindows.ProductMgmt.Product", result2);

    String result3 = invokeDerivateLegacyClass("/invalid");
    assertNull(result3);
  }

  /**
   * Tests JavaScript code injection with regex pattern matching.
   *
   * <p>This test validates that the injectCodeAfterFunctionCall method can properly
   * inject new JavaScript code after existing function calls using regex patterns.
   * This is useful for adding message handling or other functionality to legacy pages.</p>
   *
   * @throws Exception
   *     if code injection fails or reflection access encounters issues
   */
  @Test
  public void injectCodeAfterFunctionCallShouldInjectWithRegex() throws Exception {
    String original = "submitThisPage(param); doSomething();";
    String functionCall = "submitThisPage\\(([^)]+)\\);";
    String newCall = "sendMessage('processOrder');";

    String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, true);

    assertTrue(result.contains("submitThisPage(param);sendMessage('processOrder');"));
  }

  /**
   * Tests JavaScript code injection with literal string matching.
   *
   * <p>This test validates that the injectCodeAfterFunctionCall method can inject
   * new JavaScript code after existing function calls using exact string matching
   * (non-regex mode). This provides a simpler alternative when regex patterns are not needed.</p>
   *
   * @throws Exception
   *     if code injection fails or reflection access encounters issues
   */
  @Test
  public void injectCodeAfterFunctionCallShouldInjectWithoutRegex() throws Exception {
    String original = "closeThisPage(); doSomething();";
    String functionCall = "closeThisPage();";
    String newCall = "sendMessage('closeModal');";

    String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, false);

    assertTrue(result.contains("closeThisPage();sendMessage('closeModal');"));
  }

  /**
   * Tests the generation of receive and post message JavaScript script.
   *
   * <p>This test validates that the generateReceiveAndPostMessageScript method
   * produces a complete JavaScript script containing event listeners for message
   * handling between frames and windows. The script should include both receiving
   * and posting message functionality.</p>
   *
   * @throws Exception
   *     if script generation fails or reflection access encounters issues
   */
  @Test
  public void generateReceiveAndPostMessageScriptShouldReturnCorrectScript() throws Exception {
    String script = invokeGenerateReceiveAndPostMessageScript();

    assertTrue(script.contains("window.addEventListener"));
    assertTrue(script.contains("message"));
    assertTrue(script.contains("fromForm"));
    assertTrue(script.contains("fromIframe"));
    assertTrue(script.contains("window.parent.postMessage"));
  }

  /**
   * Tests the generation of post message JavaScript script.
   *
   * <p>This test validates that the generatePostMessageScript method produces
   * a JavaScript script containing the sendMessage function for posting messages
   * to parent windows. This is essential for iframe communication in the application.</p>
   *
   * @throws Exception
   *     if script generation fails or reflection access encounters issues
   */
  @Test
  public void generatePostMessageScriptShouldReturnCorrectScript() throws Exception {
    String script = invokeGeneratePostMessageScript();

    assertTrue(script.contains("sendMessage"));
    assertTrue(script.contains("fromForm"));
    assertTrue(script.contains("window.parent.postMessage"));
  }

  /**
   * Helper method to invoke the private isLegacyRequest method via reflection.
   *
   * @param path
   *     the request path to test
   * @return true if the path represents a legacy request, false otherwise
   * @throws Exception
   *     if reflection access fails
   */
  private boolean invokeIsLegacyRequest(String path) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("isLegacyRequest", String.class);
    method.setAccessible(true);
    return (Boolean) method.invoke(forwarderServlet, path);
  }

  /**
   * Helper method to invoke the private handleTokenConsistency method via reflection.
   *
   * @param req
   *     the HTTP servlet request
   * @param wrapper
   *     the HTTP servlet request wrapper
   * @throws Exception
   *     if reflection access fails or token handling encounters issues
   */
  private void invokeHandleTokenConsistency(HttpServletRequest req,
      HttpServletRequestWrapper wrapper) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("handleTokenConsistency",
        HttpServletRequest.class, HttpServletRequestWrapper.class);
    method.setAccessible(true);
    method.invoke(null, req, wrapper);
  }

  /**
   * Helper method to invoke the private handleRecordIdentifier method via reflection.
   *
   * @param wrapper
   *     the HTTP servlet request wrapper
   * @throws Exception
   *     if reflection access fails or record identifier handling encounters issues
   */
  private void invokeHandleRecordIdentifier(HttpServletRequestWrapper wrapper) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("handleRecordIdentifier",
        HttpServletRequestWrapper.class);
    method.setAccessible(true);
    method.invoke(null, wrapper);
  }

  /**
   * Helper method to invoke the private handleRequestContext method via reflection.
   *
   * @param res
   *     the HTTP servlet response
   * @param wrapper
   *     the HTTP servlet request wrapper
   * @throws Exception
   *     if reflection access fails or context setup encounters issues
   */
  private void invokeHandleRequestContext(HttpServletResponse res, HttpServletRequestWrapper wrapper) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("handleRequestContext",
        HttpServletResponse.class, HttpServletRequestWrapper.class);
    method.setAccessible(true);
    method.invoke(null, res, wrapper);
  }

  /**
   * Helper method to invoke the private maybeValidateLegacyClass method via reflection.
   *
   * @param pathInfo
   *     the request path information
   * @throws Exception
   *     if reflection access fails or class validation encounters issues
   */
  private void invokeMaybeValidateLegacyClass(String pathInfo) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("maybeValidateLegacyClass",
        String.class);
    method.setAccessible(true);
    method.invoke(forwarderServlet, pathInfo);
  }

  /**
   * Helper method to invoke the private deriveLegacyClass method via reflection.
   *
   * @param pathInfo
   *     the request path information
   * @return the derived legacy class name, or null if derivation fails
   * @throws Exception
   *     if reflection access fails
   */
  private String invokeDerivateLegacyClass(String pathInfo) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
    method.setAccessible(true);
    return (String) method.invoke(forwarderServlet, pathInfo);
  }

  /**
   * Helper method to invoke the private injectCodeAfterFunctionCall method via reflection.
   *
   * @param original
   *     the original JavaScript code
   * @param functionCall
   *     the function call pattern to match
   * @param newCall
   *     the new code to inject
   * @param isRegex
   *     whether to use regex matching
   * @return the modified JavaScript code with injected content
   * @throws Exception
   *     if reflection access fails or code injection encounters issues
   */
  private String invokeInjectCodeAfterFunctionCall(String original, String functionCall, String newCall,
      Boolean isRegex) throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("injectCodeAfterFunctionCall",
        String.class, String.class, String.class, Boolean.class);
    method.setAccessible(true);
    return (String) method.invoke(forwarderServlet, original, functionCall, newCall, isRegex);
  }

  /**
   * Helper method to invoke the private generateReceiveAndPostMessageScript method via reflection.
   *
   * @return the generated JavaScript script for message handling
   * @throws Exception
   *     if reflection access fails or script generation encounters issues
   */
  private String invokeGenerateReceiveAndPostMessageScript() throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("generateReceiveAndPostMessageScript");
    method.setAccessible(true);
    return (String) method.invoke(forwarderServlet);
  }

  /**
   * Helper method to invoke the private generatePostMessageScript method via reflection.
   *
   * @return the generated JavaScript script for posting messages
   * @throws Exception
   *     if reflection access fails or script generation encounters issues
   */
  private String invokeGeneratePostMessageScript() throws Exception {
    java.lang.reflect.Method method = ForwarderServlet.class.getDeclaredMethod("generatePostMessageScript");
    method.setAccessible(true);
    return (String) method.invoke(forwarderServlet);
  }
}
