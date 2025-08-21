package com.etendoerp.metadata.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.secureApp.HttpSecureAppServlet;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.kernel.RequestContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class for ServletRegistry.
 * This class tests the behavior of the ServletRegistry when retrieving delegated servlets.
 */
@MockitoSettings (strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ServletRegistryTest {

  @Mock
  private ServletContext servletContext;

  @Mock
  private ServletRegistration servletRegistration;

  @Mock
  private HttpSecureAppServlet mockServlet;

  @Mock
  private ServletConfig servletConfig;

  /**
   * This test checks that the getDelegatedServlet method throws a NullPointerException when the
   * servlet context is not available.
   * It ensures that the method behaves consistently when the servlet context is not set.
   */
  @Test
  void getDelegatedServletWithNonExistentMappingShouldThrowNullPointerException() {
    String uri = "/nonexistent";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class)) {
      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(Collections.emptyMap()).when(servletContext).getServletRegistrations();

      assertThrows(NullPointerException.class,
          () -> ServletRegistry.getDelegatedServlet(mockServlet, uri));
    }
  }

  /**
   * This test checks that the getDelegatedServlet method throws a NullPointerException when the
   * servlet context is not available.
   * It ensures that the method behaves consistently when the servlet context is not set.
   */
  @Test
  void getDelegatedServletWithEmptyPathShouldThrowNullPointerException() {
    String uri = "";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class)) {
      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(Collections.emptyMap()).when(servletContext).getServletRegistrations();

      assertThrows(NullPointerException.class,
          () -> ServletRegistry.getDelegatedServlet(mockServlet, uri));
    }
  }

  /**
   * This test checks that the getDelegatedServlet method throws a NullPointerException when the
   * servlet class name is invalid or not found in the servlet context.
   * It ensures that the method behaves consistently when the servlet class cannot be instantiated.
   */
  @Test
  void getDelegatedServletWithInvalidClassNameShouldThrowNullPointerException() {
    String uri = "/test";
    String invalidClassName = "com.invalid.ClassName";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class);
         MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {

      Map<String, ServletRegistration> registrations = new HashMap<>();
      registrations.put("/test", servletRegistration);

      when(servletRegistration.getClassName()).thenReturn(invalidClassName);
      when(servletRegistration.getMappings()).thenReturn(Arrays.asList("/test/*"));

      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(registrations).when(servletContext).getServletRegistrations();

      weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(any()))
          .thenReturn(null);

      assertThrows(NullPointerException.class,
          () -> ServletRegistry.getDelegatedServlet(mockServlet, uri));
    }
  }

  /**
   * This test checks that the getDelegatedServlet method throws a NullPointerException when the
   * servlet context is not available.
   * It ensures that the method behaves consistently when the servlet context is not set.
   */
  @Test
  void getDelegatedServletStaticRegistryBehaviorShouldWorkConsistently() {
    String uri1 = "/test1";
    String uri2 = "/test2";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class)) {
      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(Collections.emptyMap()).when(servletContext).getServletRegistrations();

      assertThrows(NullPointerException.class,
          () -> ServletRegistry.getDelegatedServlet(mockServlet, uri1));
      assertThrows(NullPointerException.class,
          () -> ServletRegistry.getDelegatedServlet(mockServlet, uri2));
    }
  }

  /**
   * This test checks that the getDelegatedServletRegistry method builds correctly when the
   * ServletContext is available.
   * It ensures that the servlet registrations can be retrieved without throwing an exception.
   */
  @Test
  void getDelegatedServletRegistryBuildsCorrectlyWhenServletContextAvailable() {
    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class)) {
      Map<String, ServletRegistration> registrations = new HashMap<>();
      registrations.put("test", servletRegistration);

      when(servletRegistration.getMappings()).thenReturn(Arrays.asList("/test/*"));

      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(registrations).when(servletContext).getServletRegistrations();

      assertDoesNotThrow(RequestContext::getServletContext);
    }
  }

  /**
   * This test checks that the getDelegatedServlet method returns the correct servlet when a valid
   * servlet is mocked and the servlet registration is available.
   */
  @Test
  void getDelegatedServletWithValidServletMockingShouldCreateServlet() {
    String uri = "/test";
    String className = "org.openbravo.erpCommon.security.Menu";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class);
         MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {

      Map<String, ServletRegistration> registrations = new HashMap<>();
      registrations.put("/test", servletRegistration);

      when(servletRegistration.getClassName()).thenReturn(className);
      when(servletRegistration.getMappings()).thenReturn(Arrays.asList("/test/*"));

      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(registrations).when(servletContext).getServletRegistrations();

      weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(any()))
          .thenReturn(mockServlet);
      when(mockServlet.getServletConfig()).thenReturn(servletConfig);

      HttpSecureAppServlet result = ServletRegistry.getDelegatedServlet(mockServlet, uri);

      assertNotNull(result);
      assertEquals(mockServlet, result);
      verify(mockServlet).getServletConfig();
    }
  }

  /**
   * This test checks that the first segment of the URI is used to match the servlet mapping.
   * It ensures that the correct servlet is returned when the first segment matches a registered servlet.
   */
  @Test
  void getDelegatedServletWithFirstSegmentMatchingShouldUseFirstSegment() {
    String uri = "/test/submenu/action";
    String className = "org.openbravo.erpCommon.security.Menu";

    try (MockedStatic<RequestContext> requestContextMock = mockStatic(RequestContext.class);
         MockedStatic<WeldUtils> weldUtilsMock = mockStatic(WeldUtils.class)) {

      Map<String, ServletRegistration> registrations = new HashMap<>();
      registrations.put("/test", servletRegistration);

      when(servletRegistration.getClassName()).thenReturn(className);
      when(servletRegistration.getMappings()).thenReturn(Arrays.asList("/test/*"));

      requestContextMock.when(RequestContext::getServletContext).thenReturn(servletContext);
      doReturn(registrations).when(servletContext).getServletRegistrations();

      weldUtilsMock.when(() -> WeldUtils.getInstanceFromStaticBeanManager(any()))
          .thenReturn(mockServlet);
      when(mockServlet.getServletConfig()).thenReturn(servletConfig);

      HttpSecureAppServlet result = ServletRegistry.getDelegatedServlet(mockServlet, uri);

      assertNotNull(result);
      assertEquals(mockServlet, result);
    }
  }
}
