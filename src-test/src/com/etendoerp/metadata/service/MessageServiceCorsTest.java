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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.service;

import static com.etendoerp.metadata.MetadataTestConstants.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.etendoerp.metadata.MetadataTestConstants.HTTP_LOCALHOST_8080;
import static com.etendoerp.metadata.MetadataTestConstants.ORIGIN;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the {@code setCORSHeaders} behavior of {@link MessageService}.
 * <p>
 * The method is {@code protected}, so it is exercised indirectly through
 * {@link MessageService#process()}. These tests verify that CORS response headers
 * are set when an {@code Origin} header is present and are omitted when it is absent.
 */
public class MessageServiceCorsTest extends BaseMetadataServiceTest {

  private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

  @Override
  protected String getServicePath() {
    return "/meta/message";
  }

  /**
   * By default the test infrastructure does not stub the Origin header, so Mockito
   * returns null. Each test that needs an explicit value sets it individually.
   */
  @Before
  public void setUpCorsTest() {
    // Default: Origin header not present (null)
    lenient().when(mockRequest.getHeader(ORIGIN)).thenReturn(null);
  }

  /**
   * When the request carries a non-empty {@code Origin} header, {@code process()} must
   * add all five CORS headers to the response.
   */
  @Test
  public void processWithNonEmptyOriginSetsAllCorsHeaders() throws IOException {
    when(mockRequest.getHeader(ORIGIN)).thenReturn(HTTP_LOCALHOST_8080);

    new MessageService(mockRequest, mockResponse).process();

    verify(mockResponse).setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, HTTP_LOCALHOST_8080);
    verify(mockResponse).setHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS");
    verify(mockResponse).setHeader("Access-Control-Allow-Credentials", "true");
    verify(mockResponse).setHeader("Access-Control-Allow-Headers",
        "Content-Type, origin, accept, X-Requested-With");
    verify(mockResponse).setHeader("Access-Control-Max-Age", "1000");
  }

  /**
   * When the {@code Origin} header is {@code null}, no CORS headers should be written.
   */
  @Test
  public void processWithNullOriginDoesNotSetCorsHeaders() throws IOException {
    when(mockRequest.getHeader(ORIGIN)).thenReturn(null);

    new MessageService(mockRequest, mockResponse).process();

    verify(mockResponse, never()).setHeader(eq(ACCESS_CONTROL_ALLOW_ORIGIN), anyString());
    verify(mockResponse, never()).setHeader(eq(ACCESS_CONTROL_ALLOW_METHODS), anyString());
  }

  /**
   * When the {@code Origin} header is an empty string, no CORS headers should be written,
   * because the guard condition checks {@code !origin.isEmpty()}.
   */
  @Test
  public void processWithEmptyOriginDoesNotSetCorsHeaders() throws IOException {
    when(mockRequest.getHeader(ORIGIN)).thenReturn("");

    new MessageService(mockRequest, mockResponse).process();

    verify(mockResponse, never()).setHeader(eq(ACCESS_CONTROL_ALLOW_ORIGIN), anyString());
    verify(mockResponse, never()).setHeader(eq(ACCESS_CONTROL_ALLOW_METHODS), anyString());
  }

  /**
   * The {@code Access-Control-Allow-Origin} header value must exactly mirror the
   * origin sent by the client (not a wildcard).
   */
  @Test
  public void processWithOriginEchoesExactOriginValue() throws IOException {
    String customOrigin = "https://app.example.com";
    when(mockRequest.getHeader(ORIGIN)).thenReturn(customOrigin);

    new MessageService(mockRequest, mockResponse).process();

    verify(mockResponse).setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, customOrigin);
  }
}
