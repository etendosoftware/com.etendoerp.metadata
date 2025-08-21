package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.MetadataTestConstants.ADDRESS1;
import static com.etendoerp.metadata.MetadataTestConstants.ADDRESS_123_MAIN_ST;
import static com.etendoerp.metadata.MetadataTestConstants.ADDRESS_123_MAIN_STREET;
import static com.etendoerp.metadata.MetadataTestConstants.APT_1;
import static com.etendoerp.metadata.MetadataTestConstants.CREATE;
import static com.etendoerp.metadata.MetadataTestConstants.JSON_ADDRESS_SAMPLE;
import static com.etendoerp.metadata.MetadataTestConstants.SPRINGFIELD;
import static com.etendoerp.metadata.MetadataTestConstants.ZIP_CODE_12345;
import static com.etendoerp.metadata.utils.Constants.LOCATION_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.service.json.JsonUtils;
import org.openbravo.test.base.OBBaseTest;

import com.etendoerp.metadata.exceptions.UnprocessableContentException;
import com.etendoerp.metadata.service.LocationService.LocationData;
import com.etendoerp.metadata.utils.Utils;

/**
 * Unit tests for the LocationMetadataService class, covering various scenarios
 * including successful location creation, error handling, and validation logic.
 *
 * <p>This test suite ensures that the LocationMetadataService behaves correctly
 * under different conditions, validating both business logic and integration with
 * external services.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LocationMetadataServiceTest extends OBBaseTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private LocationService locationService;

  @Mock
  private PrintWriter printWriter;

  private LocationMetadataService locationMetadataService;

  /**
   * Sets up the test environment before each test method execution.
   *
   * <p>This method initializes the LocationMetadataService instance with mocked
   * request and response objects, configures the response writer, and injects
   * the mock LocationService for testing business logic interactions.</p>
   *
   * @throws Exception
   *     if service initialization fails, mock setup encounters issues,
   *     or reflection-based field injection fails
   */
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    when(response.getWriter()).thenReturn(printWriter);

    locationMetadataService = new LocationMetadataService(request, response);
    setLocationService(locationMetadataService, locationService);
  }

  /**
   * Helper method to inject mock LocationService via reflection.
   *
   * <p>This utility method uses reflection to set the private locationService field
   * in the LocationMetadataService instance, enabling comprehensive testing of
   * service interactions without requiring public setters.</p>
   *
   * @param service
   *     the LocationMetadataService instance to modify
   * @param mockService
   *     the mock LocationService to inject
   * @throws RuntimeException
   *     if reflection access fails or field injection encounters issues
   */
  private void setLocationService(LocationMetadataService service, LocationService mockService) {
    try {
      java.lang.reflect.Field field = LocationMetadataService.class.getDeclaredField("locationService");
      field.setAccessible(true);
      field.set(service, mockService);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock LocationService", e);
    }
  }

  /**
   * Tests successful location creation processing.
   *
   * <p>This test validates the complete successful flow for creating a location,
   * including JSON request parsing, LocationService interaction, context management,
   * and proper HTTP response formatting. It ensures that all components work
   * together correctly for valid location creation requests.</p>
   *
   * @throws IOException
   *     if request body reading fails or response writing encounters issues
   */
  @Test
  public void processShouldHandleCreateLocationSuccessfully() throws IOException {
    String requestBody = JSON_ADDRESS_SAMPLE;
    Map<String, Object> locationResult = createLocationResult();

    when(request.getPathInfo()).thenReturn(LOCATION_PATH + CREATE);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));
    when(locationService.createLocation(any(LocationData.class))).thenReturn(locationResult);

    try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
      locationMetadataService.process();

      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);

      verify(locationService).createLocation(any(LocationData.class));
      verify(response).setContentType("application/json");
      verify(response).setCharacterEncoding("UTF-8");
    }
  }

  /**
   * Tests handling of invalid path requests.
   *
   * <p>This test validates that when an invalid path is requested, the service
   * properly handles the error by returning a 404 Not Found status and appropriate
   * error message. This ensures proper API endpoint validation and error reporting.</p>
   *
   * @throws IOException
   *     if response writing fails or error handling encounters issues
   */
  @Test
  public void processShouldThrowNotFoundExceptionForInvalidPath() throws IOException {
    when(request.getPathInfo()).thenReturn("/invalid/path");

    try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(Exception.class)))
          .thenReturn("{\"error\":\"Not found\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(any(Exception.class)))
          .thenReturn(404);

      locationMetadataService.process();

      verify(response).setStatus(404);
      verify(printWriter).write("{\"error\":\"Not found\"}");
    }
  }

  /**
   * Tests validation of required fields during location creation.
   *
   * <p>This test validates that when required fields are missing from the location
   * creation request, the service properly validates the input and returns a 422
   * Unprocessable Content status with appropriate validation error messages.</p>
   *
   * @throws IOException
   *     if request processing fails or error response writing encounters issues
   */
  @Test
  public void handleCreateLocationShouldValidateRequiredFields() throws IOException {
    String requestBodyMissingFields = "{\"address2\":\"Apt 1\"}";

    when(request.getPathInfo()).thenReturn(LOCATION_PATH + CREATE);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBodyMissingFields)));

    try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(Exception.class)))
          .thenReturn("{\"error\":\"Validation failed\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(any(Exception.class)))
          .thenReturn(422);

      locationMetadataService.process();

      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);

      verify(response).setStatus(422);
      verify(printWriter).write("{\"error\":\"Validation failed\"}");
    }
  }

  /**
   * Tests handling of malformed JSON requests.
   *
   * <p>This test validates that when invalid JSON is provided in the request body,
   * the service properly handles the JSONException and returns a 422 Unprocessable
   * Content status with an appropriate error message indicating JSON parsing failure.</p>
   *
   * @throws IOException
   *     if request processing fails or error response writing encounters issues
   */
  @Test
  public void handleCreateLocationShouldHandleJSONException() throws IOException {
    String invalidJson = "{invalid json}";

    when(request.getPathInfo()).thenReturn(LOCATION_PATH + CREATE);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(invalidJson)));

    try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(Exception.class)))
          .thenReturn("{\"error\":\"Invalid JSON\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(any(Exception.class)))
          .thenReturn(422);

      locationMetadataService.process();

      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);

      verify(response).setStatus(422);
      verify(printWriter).write("{\"error\":\"Invalid JSON\"}");
    }
  }

  /**
   * Tests handling of OBException during location creation.
   *
   * <p>This test validates that when the LocationService throws an OBException
   * (typically database or business logic errors), the service properly handles
   * the exception and returns a 500 Internal Server Error status with appropriate
   * error details while ensuring proper context cleanup.</p>
   *
   * @throws IOException
   *     if request processing fails or error response writing encounters issues
   */
  @Test
  public void handleCreateLocationShouldHandleOBException() throws IOException {
    String requestBody = JSON_ADDRESS_SAMPLE;

    when(request.getPathInfo()).thenReturn(LOCATION_PATH + CREATE);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));
    when(locationService.createLocation(any(LocationData.class)))
        .thenThrow(new OBException("Database error"));

    try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(Exception.class)))
          .thenReturn("{\"error\":\"Database error\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(any(Exception.class)))
          .thenReturn(500);

      locationMetadataService.process();

      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);

      verify(response).setStatus(500);
      verify(printWriter).write("{\"error\":\"Database error\"}");
    }
  }

  /**
   * Tests handling of unexpected runtime exceptions.
   *
   * <p>This test validates that when unexpected RuntimeExceptions occur during
   * location creation, the service properly handles them and returns a 500
   * Internal Server Error status while ensuring proper context cleanup and
   * error reporting.</p>
   *
   * @throws IOException
   *     if request processing fails or error response writing encounters issues
   */
  @Test
  public void handleCreateLocationShouldHandleUnexpectedException() throws IOException {
    String requestBody = JSON_ADDRESS_SAMPLE;

    when(request.getPathInfo()).thenReturn(LOCATION_PATH + CREATE);
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));
    when(locationService.createLocation(any(LocationData.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
         MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(any(Exception.class)))
          .thenReturn("{\"error\":\"Unexpected error\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(any(Exception.class)))
          .thenReturn(500);

      locationMetadataService.process();

      contextMock.verify(() -> OBContext.setAdminMode(true));
      contextMock.verify(OBContext::restorePreviousMode);

      verify(response).setStatus(500);
      verify(printWriter).write("{\"error\":\"Unexpected error\"}");
    }
  }

  /**
   * Tests correct mapping of JSON fields to LocationData object.
   *
   * <p>This test validates that the buildLocationData method properly maps all
   * JSON fields from the request to the corresponding LocationData properties,
   * ensuring data integrity during the conversion process.</p>
   *
   * @throws JSONException
   *     if JSON object creation or field access fails
   */
  @Test
  public void buildLocationDataShouldMapJsonFieldsCorrectly() throws JSONException {
    JSONObject jsonRequest = new JSONObject();
    jsonRequest.put(ADDRESS1, ADDRESS_123_MAIN_STREET);
    jsonRequest.put("address2", "Suite 100");
    jsonRequest.put("postal", ZIP_CODE_12345);
    jsonRequest.put("city", SPRINGFIELD);
    jsonRequest.put("countryId", "US");
    jsonRequest.put("regionId", "CA");

    LocationData result = invokePrivateBuildLocationData(jsonRequest);

    assertEquals(ADDRESS_123_MAIN_STREET, result.getAddress1());
    assertEquals("Suite 100", result.getAddress2());
    assertEquals(ZIP_CODE_12345, result.getPostal());
    assertEquals(SPRINGFIELD, result.getCity());
    assertEquals("US", result.getCountryId());
    assertEquals("CA", result.getRegionId());
  }

  /**
   * Tests handling of missing optional fields in JSON requests.
   *
   * <p>This test validates that the buildLocationData method properly handles
   * JSON requests where optional fields are missing, setting appropriate default
   * values (empty strings for optional text fields, null for optional ID fields).</p>
   *
   * @throws JSONException
   *     if JSON object creation or field access fails
   */
  @Test
  public void buildLocationDataShouldHandleMissingFields() throws JSONException {
    JSONObject jsonRequest = new JSONObject();
    jsonRequest.put(ADDRESS1, ADDRESS_123_MAIN_STREET);
    jsonRequest.put("city", SPRINGFIELD);

    LocationData result = invokePrivateBuildLocationData(jsonRequest);

    assertEquals(ADDRESS_123_MAIN_STREET, result.getAddress1());
    assertEquals("", result.getAddress2());
    assertEquals("", result.getPostal());
    assertEquals(SPRINGFIELD, result.getCity());
    assertNull(result.getCountryId());
    assertNull(result.getRegionId());
  }

  /**
   * Tests handling of completely empty JSON requests.
   *
   * <p>This test validates that the buildLocationData method gracefully handles
   * empty JSON objects by setting all fields to their default values (empty
   * strings for text fields, null for ID fields).</p>
   */
  @Test
  public void buildLocationDataShouldHandleEmptyJson() {
    JSONObject jsonRequest = new JSONObject();

    LocationData result = invokePrivateBuildLocationData(jsonRequest);

    assertEquals("", result.getAddress1());
    assertEquals("", result.getAddress2());
    assertEquals("", result.getPostal());
    assertEquals("", result.getCity());
    assertNull(result.getCountryId());
    assertNull(result.getRegionId());
  }

  /**
   * Tests validation passing for valid location data.
   *
   * <p>This test validates that the validateLocationData method accepts valid
   * location data without throwing exceptions, ensuring that properly formatted
   * location information passes validation checks.</p>
   */
  @Test
  public void validateLocationDataShouldPassForValidData() {
    LocationData validData = new LocationData(
        ADDRESS_123_MAIN_ST, APT_1, ZIP_CODE_12345, SPRINGFIELD, "US", "CA"
    );

    // Should not throw exception
    invokePrivateValidateLocationData(validData);
  }

  /**
   * Tests validation failure for missing address1 field.
   *
   * <p>This test validates that the validateLocationData method throws an
   * UnprocessableContentException when the required address1 field is missing
   * or empty, ensuring proper enforcement of required field validation.</p>
   *
   * @throws UnprocessableContentException
   *     when address1 field is missing (expected)
   */
  @Test(expected = UnprocessableContentException.class)
  public void validateLocationDataShouldFailForMissingAddress1() {
    LocationData invalidData = new LocationData(
        "", APT_1, ZIP_CODE_12345, SPRINGFIELD, "US", "CA"
    );

    invokePrivateValidateLocationData(invalidData);
  }

  /**
   * Tests validation failure for missing city field.
   *
   * <p>This test validates that the validateLocationData method throws an
   * UnprocessableContentException when the required city field is missing
   * or empty, ensuring proper enforcement of required field validation.</p>
   *
   * @throws UnprocessableContentException
   *     when city field is missing (expected)
   */
  @Test(expected = UnprocessableContentException.class)
  public void validateLocationDataShouldFailForMissingCity() {
    LocationData invalidData = new LocationData(
        ADDRESS_123_MAIN_ST, APT_1, ZIP_CODE_12345, "", "US", "CA"
    );

    invokePrivateValidateLocationData(invalidData);
  }

  /**
   * Tests validation failure for missing country field.
   *
   * <p>This test validates that the validateLocationData method throws an
   * UnprocessableContentException when the required countryId field is missing
   * or empty, ensuring proper enforcement of required field validation.</p>
   *
   * @throws UnprocessableContentException
   *     when countryId field is missing (expected)
   */
  @Test(expected = UnprocessableContentException.class)
  public void validateLocationDataShouldFailForMissingCountry() {
    LocationData invalidData = new LocationData(
        ADDRESS_123_MAIN_ST, APT_1, ZIP_CODE_12345, SPRINGFIELD, "", "CA"
    );

    invokePrivateValidateLocationData(invalidData);
  }

  /**
   * Tests aggregation of multiple validation errors.
   *
   * <p>This test validates that the validateLocationData method can detect and
   * report multiple validation errors in a single exception, providing comprehensive
   * feedback about all missing required fields rather than failing on the first error.</p>
   */
  @Test
  public void validateLocationDataShouldAggregateMultipleErrors() {
    LocationData invalidData = new LocationData(
        "", APT_1, ZIP_CODE_12345, "", "", "CA"
    );

    try {
      invokePrivateValidateLocationData(invalidData);
      fail("Expected UnprocessableContentException");
    } catch (UnprocessableContentException e) {
      String message = e.getMessage();
      assertTrue(message.contains("Address line 1 is required"));
      assertTrue(message.contains("City is required"));
      assertTrue(message.contains("Country is required"));
    }
  }

  /**
   * Tests that optional fields are properly handled during validation.
   *
   * <p>This test validates that the validateLocationData method accepts location
   * data where optional fields (address2, postal, regionId) are null or empty,
   * ensuring that only truly required fields are enforced during validation.</p>
   */
  @Test
  public void validateLocationDataShouldAllowOptionalFields() {
    LocationData validData = new LocationData(
        ADDRESS_123_MAIN_ST, null, null, SPRINGFIELD, "US", null
    );

    // Should not throw exception
    invokePrivateValidateLocationData(validData);
  }

  /**
   * Tests reading of request body content.
   *
   * <p>This test validates that the readRequestBody method correctly reads and
   * returns the complete content from the HTTP request body, ensuring proper
   * handling of JSON request data.</p>
   *
   * @throws IOException
   *     if request body reading fails or stream access encounters issues
   */
  @Test
  public void readRequestBodyShouldReturnCorrectContent() throws IOException {
    String expectedContent = "{\"test\":\"data\"}";
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(expectedContent)));

    String result = invokePrivateReadRequestBody(request);

    assertEquals(expectedContent, result);
  }

  /**
   * Tests handling of empty request body content.
   *
   * <p>This test validates that the readRequestBody method properly handles
   * empty request bodies by returning an empty string, ensuring graceful
   * handling of requests without body content.</p>
   *
   * @throws IOException
   *     if request body reading fails or stream access encounters issues
   */
  @Test
  public void readRequestBodyShouldHandleEmptyContent() throws IOException {
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

    String result = invokePrivateReadRequestBody(request);

    assertEquals("", result);
  }

  /**
   * Tests handling of multi-line request body content.
   *
   * <p>This test validates that the readRequestBody method correctly processes
   * multi-line JSON content by joining lines appropriately, ensuring proper
   * handling of formatted JSON requests.</p>
   *
   * @throws IOException
   *     if request body reading fails or stream access encounters issues
   */
  @Test
  public void readRequestBodyShouldHandleMultiLineContent() throws IOException {
    String multiLineContent = "{\n  \"test\": \"data\",\n  \"another\": \"value\"\n}";
    when(request.getReader()).thenReturn(new BufferedReader(new StringReader(multiLineContent)));

    String result = invokePrivateReadRequestBody(request);

    assertEquals("{  \"test\": \"data\",  \"another\": \"value\"}", result);
  }

  /**
   * Tests error response handling and formatting.
   *
   * <p>This test validates that the handleError method properly formats exceptions
   * into JSON error responses and sets appropriate HTTP status codes, ensuring
   * consistent error reporting across all error scenarios.</p>
   */
  @Test
  public void handleErrorShouldWriteErrorResponse() {
    Exception testException = new RuntimeException("Test error");

    try (MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
         MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

      jsonUtilsMock.when(() -> JsonUtils.convertExceptionToJson(testException))
          .thenReturn("{\"error\":\"Test error\"}");
      utilsMock.when(() -> Utils.getHttpStatusFor(testException))
          .thenReturn(500);

      invokePrivateHandleError(testException);

      verify(response).setStatus(500);
      verify(printWriter).write("{\"error\":\"Test error\"}");
    }
  }

  /**
   * Helper method to invoke the private buildLocationData method via reflection.
   *
   * <p>This utility method allows tests to directly invoke the private
   * buildLocationData method to test JSON-to-LocationData conversion logic
   * in isolation.</p>
   *
   * @param jsonRequest
   *     the JSON object to convert to LocationData
   * @return the resulting LocationData object
   * @throws RuntimeException
   *     if reflection access fails or method invocation encounters issues
   */
  private LocationData invokePrivateBuildLocationData(JSONObject jsonRequest) {
    try {
      java.lang.reflect.Method method = LocationMetadataService.class
          .getDeclaredMethod("buildLocationData", JSONObject.class);
      method.setAccessible(true);
      return (LocationData) method.invoke(locationMetadataService, jsonRequest);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke buildLocationData", e);
    }
  }

  /**
   * Helper method to invoke the private validateLocationData method via reflection.
   *
   * <p>This utility method allows tests to directly invoke the private
   * validateLocationData method to test validation logic in isolation.
   * It properly unwraps InvocationTargetException to preserve the original
   * exception type for test assertions.</p>
   *
   * @param locationData
   *     the LocationData object to validate
   * @throws RuntimeException
   *     if validation fails or reflection access encounters issues
   */
  private void invokePrivateValidateLocationData(LocationData locationData) {
    try {
      java.lang.reflect.Method method = LocationMetadataService.class
          .getDeclaredMethod("validateLocationData", LocationData.class);
      method.setAccessible(true);
      method.invoke(locationMetadataService, locationData);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException("Failed to invoke validateLocationData", e);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke validateLocationData", e);
    }
  }

  /**
   * Helper method to invoke the private readRequestBody method via reflection.
   *
   * <p>This utility method allows tests to directly invoke the private
   * readRequestBody method to test request body reading logic in isolation.</p>
   *
   * @param request
   *     the HTTP servlet request to read from
   * @return the request body content as a string
   * @throws RuntimeException
   *     if reflection access fails or method invocation encounters issues
   */
  private String invokePrivateReadRequestBody(HttpServletRequest request) {
    try {
      java.lang.reflect.Method method = LocationMetadataService.class
          .getDeclaredMethod("readRequestBody", HttpServletRequest.class);
      method.setAccessible(true);
      return (String) method.invoke(locationMetadataService, request);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke readRequestBody", e);
    }
  }

  /**
   * Helper method to invoke the private handleError method via reflection.
   *
   * <p>This utility method allows tests to directly invoke the private
   * handleError method to test error handling and response formatting
   * logic in isolation.</p>
   *
   * @param exception
   *     the exception to handle and format
   * @throws RuntimeException
   *     if reflection access fails or method invocation encounters issues
   */
  private void invokePrivateHandleError(Exception exception) {
    try {
      java.lang.reflect.Method method = LocationMetadataService.class
          .getDeclaredMethod("handleError", Exception.class);
      method.setAccessible(true);
      method.invoke(locationMetadataService, exception);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke handleError", e);
    }
  }

  /**
   * Helper method to create a sample location result for testing.
   *
   * <p>This utility method creates a mock location result map that simulates
   * the response from LocationService.createLocation(), providing consistent
   * test data for successful location creation scenarios.</p>
   *
   * @return a Map containing sample location data with all expected fields
   */
  private Map<String, Object> createLocationResult() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", "test-location-id");
    result.put("_identifier", "123 Main St - Springfield - US");
    result.put(ADDRESS1, ADDRESS_123_MAIN_ST);
    result.put("address2", "");
    result.put("postal", "");
    result.put("city", SPRINGFIELD);
    result.put("countryId", "US");
    result.put("regionId", null);
    return result;
  }
}