package com.etendoerp.metadata.http;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.ContentType;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.secureApp.AllowedCrossDomainsHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonUtils;

import com.etendoerp.metadata.auth.LoginManager;
import com.etendoerp.metadata.exceptions.UnauthorizedException;
import com.etendoerp.metadata.utils.Utils;
import com.smf.securewebservices.SWSConfig;

/**
 * Unit tests for the LoginServlet class, which handles user authentication
 * and login requests in the Etendo ERP system.
 *
 * <p>This class contains comprehensive tests for various scenarios including
 * CORS handling, GET and POST request processing, error handling, and configuration
 * validation. It uses Mockito for mocking dependencies and JUnit for assertions.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginServletTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private PrintWriter printWriter;

  @Mock
  private AllowedCrossDomainsHandler corsHandler;

  @Mock
  private LoginManager loginManager;

  @Mock
  private SWSConfig swsConfig;

  private LoginServlet servlet;

  /**
   * Sets up the test environment before each test method execution.
   *
   * <p>This method initializes the LoginServlet instance and configures
   * mock objects with default behaviors. It sets up the response writer
   * to return the mocked PrintWriter for testing response content.</p>
   *
   * @throws Exception
   *     if servlet initialization fails or mock setup encounters issues
   */
  @Before
  public void setUp() throws Exception {
    servlet = new LoginServlet();
    when(response.getWriter()).thenReturn(printWriter);
  }

  /**
   * Tests that the OPTIONS method correctly sets CORS headers.
   *
   * <p>This test validates that when an HTTP OPTIONS request is received
   * (typically a preflight request for CORS), the servlet properly delegates
   * to the AllowedCrossDomainsHandler to set the appropriate CORS headers.
   * This is essential for enabling cross-origin requests from web clients.</p>
   */
  @Test
  public void doOptionsShouldSetCORSHeaders() {
    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class)) {
      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doOptions(request, response);

      corsHandlerMock.verify(AllowedCrossDomainsHandler::getInstance);
      verify(corsHandler).setCORSHeaders(request, response);
    }
  }

  /**
   * Tests that GET requests with JSON format parameter return JSON error responses.
   *
   * <p>This test validates that when a GET request is made with the isc_dataFormat
   * parameter set to "json", the servlet responds with a properly formatted JSON
   * error message indicating that POST method should be used instead. This ensures
   * API consistency for JSON-based clients.</p>
   *
   * @throws Exception
   *     if JSON error response generation fails or CORS handling encounters issues
   */
  @Test
  public void doGetWithJsonFormatShouldReturnJsonError() throws Exception {
    when(request.getParameter("isc_dataFormat")).thenReturn("json");

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doGet(request, response);

      corsHandlerMock.verify(AllowedCrossDomainsHandler::getInstance);
      verify(corsHandler).setCORSHeaders(request, response);
      utilsMock.verify(() -> Utils.writeJsonErrorResponse(response,
          HttpServletResponse.SC_METHOD_NOT_ALLOWED,
          "Use POST with JSON body to login at /meta/login"));
    }
  }

  /**
   * Tests case-insensitive handling of JSON format parameter in GET requests.
   *
   * <p>This test ensures that the servlet correctly handles the isc_dataFormat
   * parameter regardless of case (e.g., "JSON", "json", "Json"). This provides
   * better user experience and API robustness by accepting common case variations.</p>
   *
   * @throws Exception
   *     if JSON error response generation fails or CORS handling encounters issues
   */
  @Test
  public void doGetWithJsonFormatCaseInsensitiveShouldReturnJsonError() throws Exception {
    when(request.getParameter("isc_dataFormat")).thenReturn("JSON");

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doGet(request, response);

      utilsMock.verify(() -> Utils.writeJsonErrorResponse(response,
          HttpServletResponse.SC_METHOD_NOT_ALLOWED,
          "Use POST with JSON body to login at /meta/login"));
    }
  }

  /**
   * Tests that GET requests with non-JSON format return plain text error responses.
   *
   * <p>This test validates that when a GET request is made with a non-JSON
   * isc_dataFormat parameter (e.g., "xml"), the servlet responds with a plain
   * text error message. This ensures appropriate response formatting based on
   * the client's expected data format.</p>
   *
   * @throws Exception
   *     if plain text error response generation fails or CORS handling encounters issues
   */
  @Test
  public void doGetWithNonJsonFormatShouldReturnPlainTextError() throws Exception {
    when(request.getParameter("isc_dataFormat")).thenReturn("xml");

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class)) {
      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doGet(request, response);

      verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      verify(response).setContentType("text/plain");
      verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
      verify(printWriter).write("Method GET not allowed on /meta/login. Use POST.");
      verify(printWriter).flush();
    }
  }

  /**
   * Tests that GET requests with empty isc_dataFormat parameter return plain text responses.
   *
   * <p>This test ensures that when the isc_dataFormat parameter is present but empty,
   * the servlet defaults to plain text error response format. This handles edge cases
   * where the parameter is provided but not properly populated.</p>
   *
   * @throws Exception
   *     if plain text error response generation fails or CORS handling encounters issues
   */
  @Test
  public void doGetWithEmptyIscDataFormatShouldReturnPlainText() throws Exception {
    when(request.getParameter("isc_dataFormat")).thenReturn("");

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class)) {
      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);

      servlet.doGet(request, response);

      verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      verify(response).setContentType("text/plain");
      verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
      verify(printWriter).write("Method GET not allowed on /meta/login. Use POST.");
    }
  }

  /**
   * Tests successful login processing via POST method.
   *
   * <p>This test validates the complete successful login flow, including:
   * CORS header setting, admin mode activation, login manager processing,
   * JSON response formatting, and proper context cleanup. It ensures that
   * successful authentication returns the expected JWT token and user information.</p>
   *
   * @throws Exception
   *     if login processing fails, JSON serialization encounters issues,
   *     or context management fails
   */
  @Test
  public void doPostShouldProcessLoginSuccessfully() throws Exception {
    JSONObject loginResult = new JSONObject();
    loginResult.put("token", "test-jwt-token");
    loginResult.put("success", true);

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("test-private-key");

      setFieldWithReflection("manager", servlet, loginManager);
      when(loginManager.processLogin(request)).thenReturn(loginResult);

      servlet.doPost(request, response);

      verify(corsHandler).setCORSHeaders(request, response);
      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);
      verify(response).setContentType(ContentType.APPLICATION_JSON.getMimeType());
      verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
      verify(printWriter).write(loginResult.toString());
    }
  }

  /**
   * Tests exception handling when LoginManager throws UnauthorizedException.
   *
   * <p>This test validates that when the LoginManager throws an UnauthorizedException
   * (e.g., for invalid credentials), the servlet properly handles the exception by:
   * setting the appropriate HTTP status code, converting the exception to JSON format,
   * and ensuring context cleanup occurs even in error scenarios.</p>
   *
   * @throws Exception
   *     if exception handling fails, JSON conversion encounters issues,
   *     or context management fails during error processing
   */
  @Test
  public void doPostShouldHandleLoginManagerException() throws Exception {
    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("test-private-key");

      setFieldWithReflection("manager", servlet, loginManager);
      UnauthorizedException loginException = new UnauthorizedException("Invalid credentials");
      when(loginManager.processLogin(request)).thenThrow(loginException);

      utilsMock.when(() -> Utils.getHttpStatusFor(loginException))
          .thenReturn(HttpServletResponse.SC_UNAUTHORIZED);
      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(loginException))
          .thenReturn("{\"error\":\"Invalid credentials\"}");

      servlet.doPost(request, response);

      verify(corsHandler).setCORSHeaders(request, response);
      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);
      verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      verify(printWriter).write("{\"error\":\"Invalid credentials\"}");
    }
  }

  /**
   * Tests exception handling for generic runtime exceptions.
   *
   * <p>This test validates that when unexpected runtime exceptions occur during
   * login processing, the servlet properly handles them by setting an internal
   * server error status, converting the exception to JSON format, and ensuring
   * proper context cleanup. This ensures graceful degradation for unexpected errors.</p>
   *
   * @throws Exception
   *     if exception handling fails, JSON conversion encounters issues,
   *     or context management fails during error processing
   */
  @Test
  public void doPostShouldHandleGenericException() throws Exception {
    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("test-private-key");

      setFieldWithReflection("manager", servlet, loginManager);
      RuntimeException runtimeException = new RuntimeException("Unexpected error");
      when(loginManager.processLogin(request)).thenThrow(runtimeException);

      utilsMock.when(() -> Utils.getHttpStatusFor(runtimeException))
          .thenReturn(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(runtimeException))
          .thenReturn("{\"error\":\"Unexpected error\"}");

      servlet.doPost(request, response);

      verify(corsHandler).setCORSHeaders(request, response);
      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);
      verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      verify(printWriter).write("{\"error\":\"Unexpected error\"}");
    }
  }

  /**
   * Tests that context cleanup always occurs even when early exceptions happen.
   *
   * <p>This test ensures that the OBContext.restorePreviousMode() method is always
   * called in the finally block, even when exceptions occur during configuration
   * validation or other early stages of request processing. This prevents context
   * leaks and ensures proper resource cleanup.</p>
   *
   * @throws Exception
   *     if context management fails or exception handling encounters issues
   */
  @Test
  public void doPostShouldAlwaysRestorePreviousMode() throws Exception {
    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenThrow(new RuntimeException("Config error"));

      utilsMock.when(() -> Utils.getHttpStatusFor(any(RuntimeException.class)))
          .thenReturn(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(RuntimeException.class)))
          .thenReturn("{\"error\":\"Config error\"}");

      servlet.doPost(request, response);

      // Should still restore previous mode in finally block
      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);
    }
  }

  /**
   * Tests that POST responses have correct content type and character encoding.
   *
   * <p>This test validates that successful POST responses are properly configured
   * with JSON content type and UTF-8 character encoding. This ensures that clients
   * can correctly interpret the response content and handle international characters.</p>
   *
   * @throws Exception
   *     if content type setting fails, login processing encounters issues,
   *     or response configuration fails
   */
  @Test
  public void doPostShouldSetCorrectContentTypeAndEncoding() throws Exception {
    JSONObject loginResult = new JSONObject();
    loginResult.put("token", "test-jwt-token");

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> ignored = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("test-private-key");

      setFieldWithReflection("manager", servlet, loginManager);
      when(loginManager.processLogin(request)).thenReturn(loginResult);

      servlet.doPost(request, response);

      verify(response).setContentType(ContentType.APPLICATION_JSON.getMimeType());
      verify(response).setCharacterEncoding(StandardCharsets.UTF_8.name());
    }
  }

  /**
   * Tests serialization of complex login response objects.
   *
   * <p>This test validates that complex JSON objects containing multiple fields
   * (token, user information, expiration, roles) are properly serialized and
   * written to the response. This ensures that rich authentication responses
   * are correctly handled and transmitted to clients.</p>
   *
   * @throws Exception
   *     if JSON serialization fails, login processing encounters issues,
   *     or response writing fails
   */
  @Test
  public void doPostWithComplexLoginResponseShouldSerializeCorrectly() throws Exception {
    JSONObject loginResult = new JSONObject();
    loginResult.put("token", "complex-jwt-token-with-claims");
    loginResult.put("user", "testuser");
    loginResult.put("expires", 3600);
    loginResult.put("roles", new String[]{ "admin", "user" });

    try (MockedStatic<AllowedCrossDomainsHandler> corsHandlerMock = mockStatic(AllowedCrossDomainsHandler.class);
         MockedStatic<OBContext> ignored = mockStatic(OBContext.class);
         MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {

      corsHandlerMock.when(AllowedCrossDomainsHandler::getInstance).thenReturn(corsHandler);
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("test-private-key");

      setFieldWithReflection("manager", servlet, loginManager);
      when(loginManager.processLogin(request)).thenReturn(loginResult);

      servlet.doPost(request, response);

      verify(printWriter).write(loginResult.toString());
    }
  }

  /**
   * Tests that the LoginManager field is properly initialized.
   *
   * <p>This test validates that the servlet's LoginManager field is not null
   * after initialization, ensuring that the authentication component is properly
   * set up and ready to process login requests. This is critical for the servlet's
   * core functionality.</p>
   *
   * @throws Exception
   *     if reflection access fails or field initialization encounters issues
   */
  @Test
  public void loginManagerFieldShouldBeInitialized() throws Exception {
    LoginManager manager = getFieldWithReflection("manager", servlet, LoginManager.class);
    assertNotNull("LoginManager should be initialized", manager);
  }

  /**
   * Tests configuration validation when private key is available.
   *
   * <p>This test validates that the validateConfig method passes successfully
   * when the SWSConfig contains a valid private key. This ensures that the
   * servlet can properly validate its configuration dependencies before
   * processing authentication requests.</p>
   *
   * @throws Exception
   *     if configuration validation fails or SWSConfig access encounters issues
   */
  @Test
  public void validateConfigShouldPassWhenPrivateKeyExists() throws Exception {
    try (MockedStatic<SWSConfig> configMock = mockStatic(SWSConfig.class)) {
      configMock.when(SWSConfig::getInstance).thenReturn(swsConfig);
      when(swsConfig.getPrivateKey()).thenReturn("valid-private-key");

      // Should not throw exception
      invokeValidateConfig();
    }
  }

  /**
   * Helper method to set private fields via reflection for testing purposes.
   *
   * <p>This utility method allows tests to inject mock objects into private
   * fields of the servlet instance, enabling comprehensive testing of internal
   * components without requiring public setters.</p>
   *
   * @param fieldName
   *     the name of the field to set
   * @param targetObject
   *     the object containing the field
   * @param value
   *     the value to set in the field
   * @throws Exception
   *     if reflection access fails or field setting encounters issues
   */
  private void setFieldWithReflection(String fieldName, Object targetObject, Object value) throws Exception {
    java.lang.reflect.Field field = LoginServlet.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(targetObject, value);
  }

  /**
   * Helper method to get private field values via reflection for testing purposes.
   *
   * <p>This utility method allows tests to access private fields of the servlet
   * instance for validation, enabling verification of internal state without
   * requiring public getters.</p>
   *
   * @param <T>
   *     the expected type of the field value
   * @param fieldName
   *     the name of the field to retrieve
   * @param targetObject
   *     the object containing the field
   * @param fieldType
   *     the expected class type of the field
   * @return the value of the specified field cast to the expected type
   * @throws Exception
   *     if reflection access fails or field retrieval encounters issues
   */
  private <T> T getFieldWithReflection(String fieldName, Object targetObject, Class<T> fieldType) throws Exception {
    java.lang.reflect.Field field = LoginServlet.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return fieldType.cast(field.get(targetObject));
  }

  /**
   * Helper method to invoke the private validateConfig method via reflection.
   *
   * <p>This utility method allows tests to directly invoke the private
   * validateConfig method to test configuration validation logic in isolation.</p>
   *
   * @throws Exception
   *     if reflection access fails or configuration validation encounters issues
   */
  private void invokeValidateConfig() throws Exception {
    java.lang.reflect.Method method = LoginServlet.class.getDeclaredMethod("validateConfig");
    method.setAccessible(true);
    method.invoke(servlet);
  }
}
