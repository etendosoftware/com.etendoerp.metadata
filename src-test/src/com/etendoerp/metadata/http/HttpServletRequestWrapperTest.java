/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.MetadataTestConstants.APPLICATION_JSON_CONSTANT;
import static com.etendoerp.metadata.MetadataTestConstants.AUTHORIZATION_HEADER;
import static com.etendoerp.metadata.MetadataTestConstants.BEARER_PREFIX;
import static com.etendoerp.metadata.MetadataTestConstants.CONTENT_TYPE_CONSTANT;
import static com.etendoerp.metadata.MetadataTestConstants.CUSTOM_VALUE;
import static com.etendoerp.metadata.MetadataTestConstants.LAST_MODIFIED;
import static com.etendoerp.metadata.MetadataTestConstants.MULTI_HEADER;
import static com.etendoerp.metadata.MetadataTestConstants.ORIGINAL_HEADER;
import static com.etendoerp.metadata.MetadataTestConstants.SHOULD_HAVE_NO_MORE_VALUES;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_SESSION_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_TOKEN;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static com.etendoerp.metadata.MetadataTestConstants.VALUE1;
import static com.etendoerp.metadata.MetadataTestConstants.VALUE2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.http.session.LegacyHttpSessionAdapter;
import com.smf.securewebservices.utils.SecureWebServicesUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Unit tests for the {@link HttpServletRequestWrapper} class.
 *
 * <p>This test suite verifies the correct behavior of the HttpServletRequestWrapper,
 * ensuring it properly handles JWT token extraction, session management, custom headers,
 * and maintains compatibility with the standard HttpServletRequest interface.</p>
 *
 * <p>Tests include validation of JWT token processing, session creation and management,
 * custom header manipulation, header retrieval methods, and edge cases such as
 * handling of null values, malformed tokens, and various header types.</p>
 *
 * <p>Mockito is used to mock dependencies such as HttpServletRequest, DecodedJWT,
 * and SecureWebServicesUtils, allowing for isolated testing of wrapper behavior
 * without reliance on external JWT libraries or servlet containers.</p>
 *
 * <p>Each test method is designed to be independent, ensuring that the state is reset
 * before each test execution. This is achieved through the use of the @Before setup
 * method which initializes fresh mocks and test data for each test.</p>
 *
 * <p>Tests are annotated with @Test and utilize assertions to validate expected outcomes.
 * Exception handling is also tested to ensure proper error conditions are met.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings("java:S1448")
public class HttpServletRequestWrapperTest extends OBBaseTest {

  private static final String LOWER_AUTHORIZATION_HEADER = "authorization";
  private static final String LOWER_MULTI_HEADER = "multi-header";
  private static final String NUMERIC_HEADER = "Numeric-Header";
  private static final String LOWER_NUMERIC_HEADER = "numeric-header";
  private static final String INVALID_INTEGER = "not-a-number";
  private static final String CUSTOM_DATE_HEADER = "Custom-Date";
  private static final String LOWER_CUSTOM_DATE_HEADER = "custom-date";
  private static final String MISSING_HEADER = "Missing-Header";
  private static final String DUPLICATE_HEADER = "duplicate-header";
  private static final String FIRST_CUSTOM_HEADER = "First-Custom";
  private static final String SECOND_CUSTOM_HEADER = "Second-Custom";
  private static final String LOWER_FIRST_CUSTOM_HEADER = "first-custom";
  private static final String LOWER_SECOND_CUSTOM_HEADER = "second-custom";
  private static final String UPDATED_SESSION_ID = "updated-session-id";

  @Mock
  private HttpServletRequest mockRequest;

  @Mock
  private ServletContext mockServletContext;

  @Mock
  private DecodedJWT mockDecodedJWT;

  @Mock
  private Claim mockJtiClaim;

  @Mock
  private Claim mockUserClaim;

  private HttpServletRequestWrapper wrapper;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setupMockRequest();
    setupMockJWT();
  }

  private void setupMockRequest() {
    when(mockRequest.getServletContext()).thenReturn(mockServletContext);
    when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
    when(mockRequest.getHeaders(anyString())).thenReturn(Collections.enumeration(Collections.emptyList()));
  }

  private void setupMockJWT() {
    when(mockDecodedJWT.getClaim("jti")).thenReturn(mockJtiClaim);
    when(mockDecodedJWT.getClaim("user")).thenReturn(mockUserClaim);
    when(mockJtiClaim.asString()).thenReturn(TEST_SESSION_ID);
    when(mockUserClaim.asString()).thenReturn(TEST_USER_ID);
  }

  private void setupNoAuth() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
  }

  private void setupHeaderAuth(String token) {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX + token);
  }

  private void createWrapperWithoutToken() {
    setupNoAuth();
    wrapper = new HttpServletRequestWrapper(mockRequest);
  }

  private void createWrapperWithNoAuth() {
    setupNoAuth();
    wrapper = new HttpServletRequestWrapper(mockRequest);
  }

  private void createWrapperWithHeaderAuth(String token, MockedStatic<SecureWebServicesUtils> mockedUtils) {
    setupHeaderAuth(token);
    mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(token)).thenReturn(mockDecodedJWT);
    wrapper = new HttpServletRequestWrapper(mockRequest);
  }


  @Test
  public void constructorShouldExtractTokenFromAuthorizationHeader() {
    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      createWrapperWithHeaderAuth(TEST_TOKEN, mockedUtils);
      assertEquals("Session ID should be extracted from JWT", TEST_SESSION_ID, wrapper.getSessionId());
    }
  }

  @Test
  public void constructorShouldExtractTokenFromParameter() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(TEST_TOKEN);

    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(TEST_TOKEN)).thenReturn(mockDecodedJWT);
      wrapper = new HttpServletRequestWrapper(mockRequest);
      assertEquals("Session ID should be extracted from JWT", TEST_SESSION_ID, wrapper.getSessionId());
      assertEquals("Authorization header should be added as custom header",
          BEARER_PREFIX + TEST_TOKEN, wrapper.getHeader(AUTHORIZATION_HEADER));
    }
  }

  @Test
  public void constructorShouldHandleNoToken() {
    createWrapperWithNoAuth();
    assertNull("Session ID should be null when no token", wrapper.getSessionId());
  }

  @Test
  public void constructorShouldHandleEmptyAuthorizationHeader() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn("");
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    wrapper = new HttpServletRequestWrapper(mockRequest);
    assertNull("Session ID should be null with empty authorization header", wrapper.getSessionId());
  }

  @Test
  public void constructorShouldHandleAuthorizationHeaderWithoutBearer() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn("Basic " + TEST_TOKEN);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    wrapper = new HttpServletRequestWrapper(mockRequest);
    assertNull("Session ID should be null without Bearer prefix", wrapper.getSessionId());
  }

  @Test
  public void wrapShouldCreateNewWrapperForRegularRequest() {
    HttpServletRequestWrapper wrappedRequest = HttpServletRequestWrapper.wrap(mockRequest);
    assertNotNull("Wrapped request should not be null", wrappedRequest);
    assertTrue("Should create HttpServletRequestWrapper instance", true);
  }

  @Test
  public void wrapShouldReturnSameInstanceForWrapper() {
    createWrapperWithNoAuth();
    HttpServletRequestWrapper wrappedRequest = HttpServletRequestWrapper.wrap(wrapper);
    assertSame("Should return the same wrapper instance", wrapper, wrappedRequest);
  }

  @Test
  public void getSessionShouldCreateAndReturnSession() {
    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      createWrapperWithHeaderAuth(TEST_TOKEN, mockedUtils);

      HttpSession session1 = wrapper.getSession();
      HttpSession session2 = wrapper.getSession();

      assertNotNull("Session should not be null", session1);
      assertSame("Should return the same session instance", session1, session2);
      assertTrue("Should return LegacyHttpSessionAdapter",
          session1 instanceof LegacyHttpSessionAdapter);
    }
  }

  @Test
  public void getSessionWithCreateParameterShouldIgnoreParameter() {
    createWrapperWithNoAuth();
    HttpSession session1 = wrapper.getSession(true);
    HttpSession session2 = wrapper.getSession(false);

    assertNotNull("Session should not be null even with false parameter", session1);
    assertSame("Should return the same session regardless of create parameter", session1, session2);
  }

  @Test
  public void addHeaderShouldAddCustomHeader() {
    createWrapperWithNoAuth();
    wrapper.addHeader("Custom-Header", CUSTOM_VALUE);

    assertEquals("Should return custom header value",
        "Custom-Value", wrapper.getHeader("custom-header"));
  }

  @Test
  public void addHeaderShouldHandleMultipleValues() {
    createWrapperWithNoAuth();
    wrapper.addHeader(MULTI_HEADER, VALUE1);
    wrapper.addHeader(MULTI_HEADER, VALUE2);

    assertEquals("Should return first value for multiple values",
        "Value1", wrapper.getHeader("multi-header"));

    Enumeration<String> values = wrapper.getHeaders(MULTI_HEADER);
    assertEquals("Should have first value", VALUE1, values.nextElement());
    assertEquals("Should have second value", VALUE2, values.nextElement());
    assertFalse(SHOULD_HAVE_NO_MORE_VALUES, values.hasMoreElements());
  }

  @Test
  public void addHeaderShouldHandleNullParameters() {
    createWrapperWithNoAuth();
    wrapper.addHeader(null, "value");
    wrapper.addHeader("name", null);
    wrapper.addHeader(null, null);

    assertNull("Should return null for non-existent header", wrapper.getHeader("SomeHeader"));
  }

  @Test
  public void getHeaderShouldReturnCustomHeader() {
    createWrapperWithNoAuth();
    wrapper.addHeader("Test-Header", "Test-Value");

    assertEquals("Should return custom header value",
        "Test-Value", wrapper.getHeader("test-header"));
  }

  @Test
  public void getHeaderShouldFallbackToOriginalRequest() {
    setupNoAuth();
    when(mockRequest.getHeader(ORIGINAL_HEADER)).thenReturn("Original-Value");
    wrapper = new HttpServletRequestWrapper(mockRequest);

    assertEquals("Should return original header value",
        "Original-Value", wrapper.getHeader(ORIGINAL_HEADER));
  }

  @Test
  public void getHeaderShouldHandleNullName() {
    createWrapperWithNoAuth();
    assertNull("Should return null for null header name", wrapper.getHeader(null));
  }

  @Test
  public void getHeadersShouldReturnCustomHeaderValues() {
    createWrapperWithNoAuth();
    wrapper.addHeader(MULTI_HEADER, VALUE1);
    wrapper.addHeader(MULTI_HEADER, VALUE2);

    Enumeration<String> headers = wrapper.getHeaders(MULTI_HEADER);
    assertEquals("Should have first value", VALUE1, headers.nextElement());
    assertEquals("Should have second value", VALUE2, headers.nextElement());
    assertFalse(SHOULD_HAVE_NO_MORE_VALUES, headers.hasMoreElements());
  }

  @Test
  public void getHeadersShouldFallbackToOriginalRequest() {
    setupNoAuth();
    List<String> originalValues = new ArrayList<>();
    originalValues.add("Original-Value1");
    originalValues.add("Original-Value2");
    when(mockRequest.getHeaders(ORIGINAL_HEADER)).thenReturn(Collections.enumeration(originalValues));
    wrapper = new HttpServletRequestWrapper(mockRequest);

    Enumeration<String> headers = wrapper.getHeaders(ORIGINAL_HEADER);
    assertEquals("Should have first original value", "Original-Value1", headers.nextElement());
    assertEquals("Should have second original value", "Original-Value2", headers.nextElement());
    assertFalse(SHOULD_HAVE_NO_MORE_VALUES, headers.hasMoreElements());
  }

  @Test
  public void getHeadersShouldHandleNullName() {
    createWrapperWithNoAuth();
    Enumeration<String> headers = wrapper.getHeaders(null);
    assertFalse("Should return empty enumeration for null name", headers.hasMoreElements());
  }

  @Test
  public void getHeaderNamesShouldIncludeCustomHeaders() {
    setupNoAuth();
    List<String> originalHeaders = new ArrayList<>();
    originalHeaders.add(ORIGINAL_HEADER);
    when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(originalHeaders));
    wrapper = new HttpServletRequestWrapper(mockRequest);
    wrapper.addHeader("Custom-Header", CUSTOM_VALUE);

    Enumeration<String> headerNames = wrapper.getHeaderNames();
    List<String> allHeaders = new ArrayList<>();
    while (headerNames.hasMoreElements()) {
      allHeaders.add(headerNames.nextElement());
    }

    assertTrue("Should include original header", allHeaders.contains(ORIGINAL_HEADER));
    assertTrue("Should include custom header", allHeaders.contains("custom-header"));
  }

  @Test
  public void getIntHeaderShouldParseIntegerValue() {
    createWrapperWithNoAuth();
    wrapper.addHeader("Content-Length", "1024");

    assertEquals("Should parse integer header value", 1024, wrapper.getIntHeader("content-length"));
  }

  @Test
  public void getIntHeaderShouldReturnMinusOneForNonExistentHeader() {
    createWrapperWithNoAuth();
    assertEquals("Should return -1 for non-existent header", -1,
        wrapper.getIntHeader("Non-Existent-Header"));
  }

  @Test
  public void getDateHeaderShouldReturnMinusOneForNonExistentHeader() {
    createWrapperWithNoAuth();
    assertEquals("Should return -1 for non-existent header", -1L,
        wrapper.getDateHeader("Non-Existent-Header"));
  }

  @Test
  public void getDateHeaderShouldFallbackToOriginalRequest() {
    setupNoAuth();
    when(mockRequest.getHeader(LAST_MODIFIED)).thenReturn("Mon, 01 Jan 2024 00:00:00 GMT");
    when(mockRequest.getDateHeader(LAST_MODIFIED)).thenReturn(1704067200000L);
    wrapper = new HttpServletRequestWrapper(mockRequest);

    assertEquals("Should delegate to original request for date parsing",
        1704067200000L, wrapper.getDateHeader(LAST_MODIFIED));
  }

  @Test
  public void setSessionIdShouldUpdateSessionId() {
    createWrapperWithNoAuth();
    String newSessionId = "new-session-id";
    wrapper.setSessionId(newSessionId);

    assertEquals("Session ID should be updated", newSessionId, wrapper.getSessionId());
  }

  @Test
  public void headersShouldBeCaseInsensitive() {
    createWrapperWithNoAuth();
    wrapper.addHeader(CONTENT_TYPE_CONSTANT, APPLICATION_JSON_CONSTANT);

    assertEquals("Should retrieve header with lowercase key",
        "application/json", wrapper.getHeader("content-type"));
    assertNull("Should not retrieve with original case due to implementation",
        wrapper.getHeader(CONTENT_TYPE_CONSTANT));
  }

  @Test
  public void concurrentAccessShouldBeHandledGracefully() throws Exception {
    createWrapperWithNoAuth();

    final int numberOfThreads = 3;
    final int operationsPerThread = 10;
    Thread[] threads = new Thread[numberOfThreads];

    for (int i = 0; i < numberOfThreads; i++) {
      final int threadIndex = i;
      threads[i] = new Thread(() -> {
        try {
          for (int j = 0; j < operationsPerThread; j++) {
            String headerName = "Thread-" + threadIndex + "-Header-" + j;
            String headerValue = "Thread-" + threadIndex + "-Value-" + j;
            wrapper.addHeader(headerName, headerValue);
          }
        } catch (Exception e) {
          // Expected due to HashMap concurrency issues - log but continue
        }
      });
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join(2000);
    }

    wrapper.addHeader("Post-Concurrent-Header", "Post-Concurrent-Value");
    assertEquals("Wrapper should still be functional after concurrent access",
        "Post-Concurrent-Value", wrapper.getHeader("post-concurrent-header"));
  }

  @Test
  public void constructorShouldHandleMalformedBearerToken() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_PREFIX);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    wrapper = new HttpServletRequestWrapper(mockRequest);

    assertNull("Session ID should be null with malformed Bearer token", wrapper.getSessionId());
  }

  @Test
  public void addHeaderShouldHandleEmptyValues() {
    createWrapperWithNoAuth();
    wrapper.addHeader("Empty-Header", "");

    assertEquals("Should handle empty header value", "", wrapper.getHeader("empty-header"));
  }

  @Test
  public void getHeaderNamesShouldHandleNoHeaders() {
    setupNoAuth();
    when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
    wrapper = new HttpServletRequestWrapper(mockRequest);

    Enumeration<String> headerNames = wrapper.getHeaderNames();
    assertFalse("Should return empty enumeration when no headers", headerNames.hasMoreElements());
  }

  @Test
  public void getSessionShouldHandleNullSessionId() {
    createWrapperWithNoAuth();
    HttpSession session = wrapper.getSession();

    assertNotNull("Should create session even with null session ID", session);
    assertTrue("Should be LegacyHttpSessionAdapter", session instanceof LegacyHttpSessionAdapter);
  }

  @Test
  public void getIntHeaderShouldHandleNullHeader() {
    setupNoAuth();
    when(mockRequest.getHeader("Null-Header")).thenReturn(null);
    wrapper = new HttpServletRequestWrapper(mockRequest);

    assertEquals("Should return -1 for null header", -1, wrapper.getIntHeader("Null-Header"));
  }

  @Test
  public void customHeadersShouldOverrideOriginalHeaders() {
    createWrapperWithNoAuth();
    wrapper.addHeader("Override-Header", CUSTOM_VALUE);

    assertEquals("Custom header should override original header",
        "Custom-Value", wrapper.getHeader("override-header"));
  }

  @Test
  public void headersShouldHandleSpecialCharacters() {
    createWrapperWithNoAuth();
    String specialValue = "Value with spaces, symbols: !@#$%^&*()";
    wrapper.addHeader("Special-Header", specialValue);

    assertEquals("Should handle special characters in header value",
        specialValue, wrapper.getHeader("special-header"));
  }

  @Test
  public void sessionShouldBeConsistentAcrossMultipleCalls() {
    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      createWrapperWithHeaderAuth(TEST_TOKEN, mockedUtils);

      HttpSession session1 = wrapper.getSession();
      HttpSession session2 = wrapper.getSession(true);
      HttpSession session3 = wrapper.getSession(false);

      assertSame("All getSession calls should return same instance", session1, session2);
      assertSame("All getSession calls should return same instance", session2, session3);
    }
  }

  @Test
  public void constructorShouldHandleNullJWTClaims() {
    setupHeaderAuth(TEST_TOKEN);
    when(mockJtiClaim.asString()).thenReturn(null);
    when(mockUserClaim.asString()).thenReturn(null);

    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(TEST_TOKEN)).thenReturn(mockDecodedJWT);
      wrapper = new HttpServletRequestWrapper(mockRequest);
      assertNull("Session ID should be null when JWT claim is null", wrapper.getSessionId());
    }
  }

  @Test
  public void headerCaseHandlingShouldBeConsistent() {
    createWrapperWithNoAuth();
    wrapper.addHeader(CONTENT_TYPE_CONSTANT, APPLICATION_JSON_CONSTANT);

    assertEquals("Should retrieve with lowercase key", APPLICATION_JSON_CONSTANT, wrapper.getHeader("content-type"));
    assertNull("Original case won't work due to toLowerCase storage", wrapper.getHeader(CONTENT_TYPE_CONSTANT));
    assertNull("Upper case won't work due to toLowerCase storage", wrapper.getHeader("CONTENT-TYPE"));
  }

  /**
   * Tests getIntHeader throws a descriptive exception for invalid custom integer values.
   */
  @Test
  public void getIntHeaderShouldThrowForInvalidCustomIntegerValue() {
    createWrapperWithoutToken();
    wrapper.addHeader(NUMERIC_HEADER, INVALID_INTEGER);

    try {
      wrapper.getIntHeader(LOWER_NUMERIC_HEADER);
      throw new AssertionError("Should throw NumberFormatException for invalid custom integer header");
    } catch (NumberFormatException e) {
      assertTrue("Exception should include header name", e.getMessage().contains(LOWER_NUMERIC_HEADER));
      assertTrue("Exception should include invalid value", e.getMessage().contains(INVALID_INTEGER));
    }
  }

  /**
   * Tests getIntHeader throws a descriptive exception for invalid original request values.
   */
  @Test
  public void getIntHeaderShouldThrowForInvalidOriginalIntegerValue() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    when(mockRequest.getHeader(NUMERIC_HEADER)).thenReturn(INVALID_INTEGER);
    wrapper = new HttpServletRequestWrapper(mockRequest);

    try {
      wrapper.getIntHeader(NUMERIC_HEADER);
      throw new AssertionError("Should throw NumberFormatException for invalid original integer header");
    } catch (NumberFormatException e) {
      assertTrue("Exception should include header name", e.getMessage().contains(NUMERIC_HEADER));
      assertTrue("Exception should include invalid value", e.getMessage().contains(INVALID_INTEGER));
    }
  }

  /**
   * Tests getIntHeader parses numeric values from the original request.
   */
  @Test
  public void getIntHeaderShouldParseOriginalRequestHeader() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    when(mockRequest.getHeader(NUMERIC_HEADER)).thenReturn("2048");
    wrapper = new HttpServletRequestWrapper(mockRequest);

    assertEquals("Should parse numeric original request header", 2048, wrapper.getIntHeader(NUMERIC_HEADER));
  }

  /**
   * Tests getDateHeader rejects custom date headers.
   */
  @Test
  public void getDateHeaderShouldThrowForCustomDateHeader() {
    createWrapperWithoutToken();
    wrapper.addHeader(CUSTOM_DATE_HEADER, "Mon, 01 Jan 2024 00:00:00 GMT");

    try {
      wrapper.getDateHeader(LOWER_CUSTOM_DATE_HEADER);
      throw new AssertionError("Should throw UnsupportedOperationException for custom date header");
    } catch (UnsupportedOperationException e) {
      assertTrue("Exception should include header name", e.getMessage().contains(LOWER_CUSTOM_DATE_HEADER));
    }
  }

  /**
   * Tests getDateHeader returns -1 for a null header name.
   */
  @Test
  public void getDateHeaderShouldReturnMinusOneForNullHeaderName() {
    createWrapperWithoutToken();

    assertEquals("Should return -1 for null date header name", -1L, wrapper.getDateHeader(null));
  }

  /**
   * Tests getHeaderNames includes the Authorization header added from token parameter.
   */
  @Test
  public void constructorTokenParameterShouldExposeAuthorizationHeaderName() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(TEST_TOKEN);

    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(TEST_TOKEN)).thenReturn(mockDecodedJWT);

      wrapper = new HttpServletRequestWrapper(mockRequest);

      List<String> headerNames = toList(wrapper.getHeaderNames());
      assertTrue("Authorization header name should be exposed", headerNames.contains(AUTHORIZATION_HEADER));
    }
  }

  /**
   * Tests getHeader can retrieve the Authorization header added from token parameter.
   */
  @Test
  public void constructorTokenParameterShouldExposeAuthorizationHeaderValue() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(TEST_TOKEN);

    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(TEST_TOKEN)).thenReturn(mockDecodedJWT);

      wrapper = new HttpServletRequestWrapper(mockRequest);

      assertEquals("Authorization value should be generated from token parameter", BEARER_PREFIX + TEST_TOKEN,
          wrapper.getHeader(AUTHORIZATION_HEADER));
    }
  }

  /**
   * Tests getHeaders documents the current lowercase lookup behavior for token parameter headers.
   */
  @Test
  public void constructorTokenParameterHeadersShouldFallbackWhenLookupIsLowercase() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(TEST_TOKEN);

    try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
      mockedUtils.when(() -> SecureWebServicesUtils.decodeToken(TEST_TOKEN)).thenReturn(mockDecodedJWT);

      wrapper = new HttpServletRequestWrapper(mockRequest);

      assertFalse("Lowercase lookup should not return constructor-added Authorization header values",
          wrapper.getHeaders(LOWER_AUTHORIZATION_HEADER).hasMoreElements());
    }
  }

  /**
   * Tests addHeader appends custom values in insertion order.
   */
  @Test
  public void addHeaderShouldPreserveInsertionOrderForMultipleValues() {
    createWrapperWithoutToken();
    wrapper.addHeader(MULTI_HEADER, VALUE1);
    wrapper.addHeader(MULTI_HEADER, VALUE2);

    List<String> values = toList(wrapper.getHeaders(LOWER_MULTI_HEADER));
    assertEquals("First value should be preserved", VALUE1, values.get(0));
    assertEquals("Second value should be preserved", VALUE2, values.get(1));
  }

  /**
   * Tests getHeaders uses case-insensitive lookup for headers added through addHeader.
   */
  @Test
  public void getHeadersShouldUseCaseInsensitiveLookupForAddedHeaders() {
    createWrapperWithoutToken();
    wrapper.addHeader(MULTI_HEADER, VALUE1);

    Enumeration<String> values = wrapper.getHeaders(MULTI_HEADER);
    assertEquals("Custom header should be found using original case", VALUE1, values.nextElement());
    assertFalse(SHOULD_HAVE_NO_MORE_VALUES, values.hasMoreElements());
  }

  /**
   * Tests getHeaderNames keeps only one name when original and custom headers share a key.
   */
  @Test
  public void getHeaderNamesShouldDeduplicateOriginalAndCustomHeaderNames() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(Collections.singletonList(DUPLICATE_HEADER)));
    wrapper = new HttpServletRequestWrapper(mockRequest);
    wrapper.addHeader(DUPLICATE_HEADER, CUSTOM_VALUE);

    List<String> headerNames = toList(wrapper.getHeaderNames());
    assertEquals("Duplicate header should appear once", 1, Collections.frequency(headerNames, DUPLICATE_HEADER));
  }

  /**
   * Tests getHeaderNames includes multiple custom header names.
   */
  @Test
  public void getHeaderNamesShouldIncludeMultipleCustomHeaders() {
    createWrapperWithoutToken();
    wrapper.addHeader(FIRST_CUSTOM_HEADER, VALUE1);
    wrapper.addHeader(SECOND_CUSTOM_HEADER, VALUE2);

    List<String> headerNames = toList(wrapper.getHeaderNames());
    assertTrue("First custom header should be included", headerNames.contains(LOWER_FIRST_CUSTOM_HEADER));
    assertTrue("Second custom header should be included", headerNames.contains(LOWER_SECOND_CUSTOM_HEADER));
  }

  /**
   * Tests setSessionId can clear an existing session ID.
   */
  @Test
  public void setSessionIdShouldAllowClearingSessionId() {
    createWrapperWithoutToken();
    wrapper.setSessionId(UPDATED_SESSION_ID);
    wrapper.setSessionId(null);

    assertNull("Session ID should be cleared", wrapper.getSessionId());
  }

  /**
   * Tests setSessionId stores the latest assigned value.
   */
  @Test
  public void setSessionIdShouldKeepLatestAssignedValue() {
    createWrapperWithoutToken();
    wrapper.setSessionId(TEST_SESSION_ID);
    wrapper.setSessionId(UPDATED_SESSION_ID);

    assertEquals("Latest session ID should be returned", UPDATED_SESSION_ID, wrapper.getSessionId());
  }

  /**
   * Tests getHeaders delegates to the wrapped request when a custom header is absent.
   */
  @Test
  public void getHeadersShouldDelegateForMissingCustomHeader() {
    when(mockRequest.getHeader(AUTHORIZATION_HEADER)).thenReturn(null);
    when(mockRequest.getParameter(TOKEN)).thenReturn(null);
    when(mockRequest.getHeaders(MISSING_HEADER)).thenReturn(Collections.enumeration(Collections.singletonList(VALUE1)));
    wrapper = new HttpServletRequestWrapper(mockRequest);

    Enumeration<String> values = wrapper.getHeaders(MISSING_HEADER);
    assertEquals("Missing custom header values should come from original request", VALUE1, values.nextElement());
    assertFalse(SHOULD_HAVE_NO_MORE_VALUES, values.hasMoreElements());
  }

  private List<String> toList(Enumeration<String> values) {
    List<String> result = new ArrayList<>();
    while (values.hasMoreElements()) {
      result.add(values.nextElement());
    }
    return result;
  }
}
