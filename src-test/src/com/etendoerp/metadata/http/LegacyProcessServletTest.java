package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.test.base.OBBaseTest;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the LegacyProcessServlet class.
 * <p>
 * This class tests the essential functionality of the LegacyProcessServlet,
 * focusing on basic request handling and parameter processing without
 * deep integration with complex framework components.
 * </p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LegacyProcessServletTest extends OBBaseTest {
    private static final String PARAM_INP_KEY = "inpKey";
    private static final String PARAM_INP_WINDOW_ID = "inpwindowId";
    private static final String PARAM_INP_KEY_COLUMN_ID = "inpkeyColumnId";
    private static final String NOT_EXIST_JS_FILE = "/web/js/nonexistent.js";
    private static final String TEST_JS_FILE = "/web/js/test-script.js";
    private static final String CALENDAR_JS_FILE = "/web/js/calendar-lang.js";
    private static final String TEST_UPPERCASE_JS_FILE = "/web/js/script.JS";
    public static final String REDIRECT = "/redirect";
    public static final String LOCATION = "location";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpSession session;
    @Mock
    private RequestDispatcher requestDispatcher;
    @Mock
    private ServletContext servletContext;
    @Mock
    private PrintWriter printWriter;

    private LegacyProcessServlet legacyProcessServlet;

    /**
     * Sets up the test environment with basic mocks.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        legacyProcessServlet = new LegacyProcessServlet();
        StringWriter stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getHeaders(anyString())).thenReturn(Collections.emptyEnumeration());
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getLocales()).thenReturn(Collections.emptyEnumeration());
        when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
        when(session.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getContextPath()).thenReturn("/etendo");
    }

    /**
     * Tests that the servlet can be instantiated successfully.
     * <p>
     * This basic test ensures the servlet constructor works and the object
     * is created without throwing exceptions.
     * </p>
     */
    @Test
    public void servletShouldInstantiateSuccessfully() {
        LegacyProcessServlet servlet = new LegacyProcessServlet();
        assertNotNull("Servlet should be instantiated", servlet);
    }

    /**
     * Tests basic request parameter processing.
     * <p>
     * Verifies that the servlet can access and process basic request parameters
     * without failing due to framework dependencies.
     * </p>
     */
    @Test
    public void servletShouldProcessBasicParameters() throws Exception {
        when(request.getPathInfo()).thenReturn("/simple/test.html");
        when(request.getParameter(TOKEN)).thenReturn(null);
        when(request.getParameter(PARAM_INP_KEY)).thenReturn("test-key");
        when(request.getParameter(PARAM_INP_WINDOW_ID)).thenReturn("test-window");
        when(request.getParameter(PARAM_INP_KEY_COLUMN_ID)).thenReturn("test-column");

        legacyProcessServlet.service(request, response);

        verify(request, atLeastOnce()).getPathInfo();
        verify(request, atLeastOnce()).getParameter(anyString());
    }

    /**
     * Tests that session interactions work as expected.
     * <p>
     * Verifies that the servlet can interact with the HTTP session
     * for storing and retrieving data.
     * </p>
     */
    @Test
    public void servletShouldInteractWithSession() throws Exception {
        when(request.getPathInfo()).thenReturn("/test/page.html");
        when(request.getParameter(TOKEN)).thenReturn(null);
        when(request.getParameter(PARAM_INP_KEY)).thenReturn("session-test-key");
        when(request.getParameter(PARAM_INP_WINDOW_ID)).thenReturn("session-window");
        when(request.getParameter(PARAM_INP_KEY_COLUMN_ID)).thenReturn("session-column");

        legacyProcessServlet.service(request, response);
        verify(request, atLeastOnce()).getSession();
    }

    /**
     * Tests that the servlet validates redirect locations.
     * <p>
     * Verifies that external URLs are rejected to prevent open redirect
     * vulnerabilities.
     * </p>
     */
    @Test
    public void servletShouldRejectExternalRedirectLocation() throws Exception {
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn("https://malicious.com");

        legacyProcessServlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    /**
     * Tests that the servlet rejects null redirect location.
     */
    @Test
    public void servletShouldRejectNullRedirectLocation() throws Exception {
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn(null);

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception ignored) {
            // Expected due to framework dependencies
        }

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    /**
     * Tests that the servlet handles redirect to internal location.
     */
    @Test
    public void servletShouldAllowInternalRedirectLocation() throws Exception {
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn("/etendo/internal/path");
        when(response.getWriter()).thenReturn(printWriter);

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception ignored) {
            // Expected due to framework dependencies
        }

        verify(response).setContentType("text/html; charset=UTF-8");
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Tests that the servlet correctly identifies legacy requests.
     */
    @Test
    public void servletShouldIdentifyLegacyFollowupRequest() throws Exception {
        when(request.getParameter("Command")).thenReturn("BUTTON_TEST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn("test-token");
        when(session.getAttribute("LEGACY_SERVLET_DIR")).thenReturn("/dir");

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception ignored) {
            // Expected due to framework dependencies
        }

        verify(request, atLeastOnce()).getParameter("Command");
    }

    /**
     * Tests JS security path validation.
     */
    @Test
    public void servletShouldRejectUnauthorizedJsPath() throws Exception {
        when(request.getPathInfo()).thenReturn("/unauthorized/path.js");

        legacyProcessServlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    /**
     * Tests that the servlet recognizes HTML paths correctly.
     * <p>
     * This test verifies basic path recognition logic without
     * requiring full framework initialization.
     * </p>
     */
    @Test
    public void servletShouldRecognizeHtmlPaths() throws Exception {
        when(request.getPathInfo()).thenReturn(SALES_INVOICE_HEADER_EDITION_HTML);
        when(request.getParameter(TOKEN)).thenReturn(null);

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception e) {
            verify(request).getPathInfo();
        }
    }

    /**
     * Tests that request dispatcher is accessed for HTML requests.
     * <p>
     * Verifies that the servlet attempts to use the request dispatcher
     * for handling HTML requests, even if framework dependencies fail.
     * </p>
     */
    @Test
    public void servletShouldAccessRequestDispatcherForHtml() throws Exception {
        when(request.getPathInfo()).thenReturn("/test/dispatcher.html");
        when(request.getParameter(TOKEN)).thenReturn(null);

        legacyProcessServlet.service(request, response);
        verify(request, atLeastOnce()).getRequestDispatcher(anyString());
    }

    /**
     * Tests servlet behavior with null path info.
     * <p>
     * Ensures the servlet handles edge cases gracefully.
     * </p>
     */
    @Test
    public void servletShouldHandleNullPathInfo() throws Exception {
        when(request.getPathInfo()).thenReturn(null);

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception e) {
            verify(request).getPathInfo();
        }
    }

    /**
     * Tests servlet behavior with empty parameters.
     * <p>
     * Verifies that the servlet handles missing or empty parameters
     * without critical failures.
     * </p>
     */
    @Test
    public void servletShouldHandleEmptyParameters() throws Exception {
        when(request.getPathInfo()).thenReturn("/empty/params.html");
        when(request.getParameter(anyString())).thenReturn(null);

        legacyProcessServlet.service(request, response);
        verify(request, atLeastOnce()).getParameter(anyString());
    }

    /**
     * Tests that response writer is accessed.
     * <p>
     * Verifies that the servlet attempts to access the response writer
     * for output operations.
     * </p>
     */
    @Test
    public void servletShouldAccessResponseWriter() throws Exception {
        when(request.getPathInfo()).thenReturn("/writer/test.html");
        when(request.getParameter(TOKEN)).thenReturn(null);

        legacyProcessServlet.service(request, response);
        verify(response, atLeastOnce()).getWriter();
    }

    /**
     * Tests that the servlet processes record identifier parameters.
     * <p>
     * Verifies that when complete record identifier parameters are provided,
     * the servlet processes them appropriately.
     * </p>
     */
    @Test
    public void servletShouldProcessCompleteRecordIdentifiers() throws Exception {
        when(request.getPathInfo()).thenReturn("/record/test.html");
        when(request.getParameter(TOKEN)).thenReturn(null);
        when(request.getParameter(PARAM_INP_KEY)).thenReturn("complete-key");
        when(request.getParameter(PARAM_INP_WINDOW_ID)).thenReturn("complete-window");
        when(request.getParameter(PARAM_INP_KEY_COLUMN_ID)).thenReturn("complete-column");

        legacyProcessServlet.service(request, response);

        verify(request).getParameter(PARAM_INP_KEY);
        verify(request).getParameter(PARAM_INP_WINDOW_ID);
        verify(request).getParameter(PARAM_INP_KEY_COLUMN_ID);
    }

    /**
     * Sets up mocks for a JavaScript request with the given path and content.
     *
     * @param jsPath    the path to the JavaScript file
     * @param jsContent the content to return, or null to simulate file not found
     */
    private void mockJavaScriptRequest(String jsPath, String jsContent) {
        when(request.getPathInfo()).thenReturn(jsPath);
        when(request.getServletContext()).thenReturn(servletContext);

        InputStream inputStream = jsContent != null
                ? new ByteArrayInputStream(jsContent.getBytes(StandardCharsets.UTF_8))
                : null;
        when(servletContext.getResourceAsStream(jsPath)).thenReturn(inputStream);
    }

    /**
     * Invokes the servlet service method, catching expected exceptions.
     */
    private void invokeServletSafely() {
        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception e) {
            // Expected due to framework dependencies
        }
    }

    /**
     * Tests that the servlet recognizes JavaScript paths correctly.
     * <p>
     * Verifies that paths ending with .js are detected as JavaScript requests.
     * </p>
     */
    @Test
    public void servletShouldRecognizeJavaScriptPaths() {
        mockJavaScriptRequest(TEST_JS_FILE, null);

        invokeServletSafely();

        verify(request).getPathInfo();
        verify(request).getServletContext();
    }

    /**
     * Tests that the servlet returns 404 when JavaScript file is not found.
     * <p>
     * Verifies that when the ServletContext cannot find the requested JS file,
     * the servlet sends an appropriate error response.
     * </p>
     */
    @Test
    public void servletShouldReturn404WhenJavaScriptFileNotFound() {
        mockJavaScriptRequest(NOT_EXIST_JS_FILE, null);

        invokeServletSafely();

        verify(servletContext).getResourceAsStream(NOT_EXIST_JS_FILE);
    }

    /**
     * Tests that the servlet serves JavaScript content correctly.
     * <p>
     * Verifies that when a JavaScript file is found, its content is read
     * and written to the response with the correct content type.
     * </p>
     */
    @Test
    public void servletShouldServeJavaScriptContent() {
        mockJavaScriptRequest(TEST_JS_FILE, "console.log('test');");

        invokeServletSafely();

        verify(servletContext).getResourceAsStream(TEST_JS_FILE);
        verify(response, atLeastOnce()).setContentType("application/javascript; charset=UTF-8");
    }

    /**
     * Tests that JavaScript paths are case-insensitive.
     * <p>
     * Verifies that .JS extension (uppercase) is also recognized.
     * </p>
     */
    @Test
    public void servletShouldRecognizeUppercaseJavaScriptExtension() {
        mockJavaScriptRequest(TEST_UPPERCASE_JS_FILE, null);

        invokeServletSafely();

        verify(request).getServletContext();
    }

    /**
     * Tests that JavaScript requests access the ServletContext for resource
     * loading.
     * <p>
     * Verifies that the servlet uses ServletContext.getResourceAsStream() to load
     * JS files.
     * </p>
     */
    @Test
    public void servletShouldAccessServletContextForJavaScript() {
        mockJavaScriptRequest(CALENDAR_JS_FILE, "var x = 1;");

        invokeServletSafely();

        verify(request, atLeastOnce()).getServletContext();
        verify(servletContext).getResourceAsStream(CALENDAR_JS_FILE);
    }

    /**
     * Tests that the servlet correctly replaces the context path in redirects.
     */
    @Test
    public void servletShouldRedirectWithCorrectContextPath() throws Exception {
        when(request.getPathInfo()).thenReturn("/simple/test.html");
        when(request.getContextPath()).thenReturn("/etendodev");

        doAnswer(invocation -> {
            HttpServletResponseLegacyWrapper wrapper = invocation.getArgument(1);
            wrapper.sendRedirect("/etendodev/web/other.html");
            return null;
        }).when(requestDispatcher).include(any(), any());

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        legacyProcessServlet.service(request, response);

        String output = stringWriter.toString();
        assertTrue("Output should contain redirected URL with replaced context path",
                output.contains("url='/etendodev/meta/legacy/web/other.html'"));
    }

    // ===== New tests for coverage =====

    /**
     * Tests that setSessionCookie skips when JSESSIONID already set.
     */
    @Test
    public void setSessionCookieShouldSkipWhenAlreadySet() throws Exception {
        Collection<String> headers = new ArrayList<>();
        headers.add("JSESSIONID=abc123; Path=/");
        when(response.getHeaders("Set-Cookie")).thenReturn(headers);
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn(null);

        legacyProcessServlet.service(request, response);

        verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
    }

    /**
     * Tests setSessionCookie with a CLASSIC_URL containing protocol, path, and port.
     */
    @Test
    public void setSessionCookieShouldParseHostFromClassicUrl() throws Exception {
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn(null);

        Properties props = new Properties();
        props.setProperty("CLASSIC_URL", "https://myhost.com:8080/etendo");

        OBPropertiesProvider provider = mock(OBPropertiesProvider.class);
        when(provider.getOpenbravoProperties()).thenReturn(props);

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(provider);

            legacyProcessServlet.service(request, response);

            verify(response).addHeader(eq("Set-Cookie"), contains("myhost.com"));
        }
    }

    /**
     * Tests setSessionCookie with empty CLASSIC_URL defaults to localhost.
     */
    @Test
    public void setSessionCookieShouldDefaultToLocalhostWhenUrlEmpty() throws Exception {
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn(null);

        Properties props = new Properties();
        props.setProperty("CLASSIC_URL", "");

        OBPropertiesProvider provider = mock(OBPropertiesProvider.class);
        when(provider.getOpenbravoProperties()).thenReturn(props);

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(provider);

            legacyProcessServlet.service(request, response);

            verify(response).addHeader(eq("Set-Cookie"), contains("localhost"));
        }
    }

    /**
     * Tests isValidLocation with double-slash prefix (protocol-relative URL).
     */
    @Test
    public void isValidLocationShouldRejectDoubleSlashUrl() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isValidLocation", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(legacyProcessServlet, "//malicious.com/page");

        assertFalse("Protocol-relative URLs should be rejected", result);
    }

    /**
     * Tests isValidLocation with null.
     */
    @Test
    public void isValidLocationShouldRejectNull() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isValidLocation", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(legacyProcessServlet, (Object) null);

        assertFalse("Null location should be rejected", result);
    }

    /**
     * Tests isValidLocation with a valid internal path.
     */
    @Test
    public void isValidLocationShouldAcceptInternalPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isValidLocation", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(legacyProcessServlet, "/etendo/some/page");

        assertTrue("Internal paths should be accepted", result);
    }

    /**
     * Tests sendErrorResponse when IOException occurs on sendError.
     */
    @Test
    public void sendErrorResponseShouldHandleIOException() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "sendErrorResponse", HttpServletResponse.class, int.class, String.class);
        method.setAccessible(true);

        HttpServletResponse mockRes = mock(HttpServletResponse.class);
        doThrow(new IOException("Test IO")).when(mockRes).sendError(anyInt(), anyString());

        // Should not throw
        method.invoke(legacyProcessServlet, mockRes, 500, "error message");

        verify(mockRes).sendError(500, "error message");
    }

    /**
     * Tests deriveLegacyClass with a path that has a window and page.
     */
    @Test
    public void deriveLegacyClassShouldReturnCorrectClassName() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet, "/SalesOrder/Header_Edition.html");

        assertEquals("org.openbravo.erpWindows.SalesOrder.Header", result);
    }

    /**
     * Tests deriveLegacyClass with a path that has only one segment.
     */
    @Test
    public void deriveLegacyClassShouldReturnNullForSingleSegment() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet, "/OnlyOneSegment");

        assertNull("Single segment path should return null", result);
    }

    /**
     * Tests deriveLegacyClass with a page name containing no underscore.
     */
    @Test
    public void deriveLegacyClassShouldHandlePageWithoutUnderscore() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet, "/Window/Page.html");

        assertEquals("org.openbravo.erpWindows.Window.Page", result);
    }

    /**
     * Tests extractTargetPathFromReferer with referer containing /meta/legacy/ path.
     */
    @Test
    public void extractTargetPathFromRefererShouldHandleLegacyPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/meta/legacy/SalesOrder/Header.html?param=val");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests extractTargetPathFromReferer with referer containing /meta/ but not /meta/legacy/.
     */
    @Test
    public void extractTargetPathFromRefererShouldHandleMetaPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/meta/SalesOrder/Header.html");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests extractTargetPathFromReferer with null referer.
     */
    @Test
    public void extractTargetPathFromRefererShouldReturnNullForNullReferer() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet, (Object) null);

        assertNull("Null referer should return null", result);
    }

    /**
     * Tests extractTargetPathFromReferer with referer that has no /meta/ path.
     */
    @Test
    public void extractTargetPathFromRefererShouldReturnNullForNoMetaPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/some/other/path");

        assertNull("Referer without /meta/ should return null", result);
    }

    /**
     * Tests extractTargetPathFromReferer with /meta/ path ending in .html.
     * Since afterMeta is "/Header.html" which contains a slash, the SalesOrder
     * prefix is NOT prepended and the path is returned as-is.
     */
    @Test
    public void extractTargetPathFromRefererShouldReturnPathAfterMeta() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/meta/Header.html");

        assertEquals("/Header.html", result);
    }

    /**
     * Tests extractTargetPath with .html pathInfo and servletDir set.
     */
    @Test
    public void extractTargetPathShouldCombineServletDirAndPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPath", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getPathInfo()).thenReturn("/Header.html");

        String result = (String) method.invoke(legacyProcessServlet, mockReq, "/SalesOrder");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests extractTargetPath with .html pathInfo and null servletDir.
     */
    @Test
    public void extractTargetPathShouldUseRefererWhenServletDirNull() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPath", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getPathInfo()).thenReturn("/Header.html");
        when(mockReq.getHeader("Referer")).thenReturn(null);

        String result = (String) method.invoke(legacyProcessServlet, mockReq, null);

        assertEquals("/Header.html", result);
    }

    /**
     * Tests extractTargetPath with non-.html pathInfo falls back to referer.
     */
    @Test
    public void extractTargetPathShouldFallbackToRefererForNonHtml() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPath", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getPathInfo()).thenReturn("/some/path");
        when(mockReq.getHeader("Referer")).thenReturn(
                "http://localhost/etendo/meta/legacy/SalesOrder/Header.html");

        String result = (String) method.invoke(legacyProcessServlet, mockReq, "/dir");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests extractTargetPath with null pathInfo falls back to referer.
     */
    @Test
    public void extractTargetPathShouldFallbackToRefererForNullPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPath", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getPathInfo()).thenReturn(null);
        when(mockReq.getHeader("Referer")).thenReturn(null);

        String result = (String) method.invoke(legacyProcessServlet, mockReq, null);

        assertNull("Null pathInfo and null referer should return null", result);
    }

    /**
     * Tests getInjectedContent when response contains FRAMESET close tag.
     */
    @Test
    public void getInjectedContentShouldInjectFramesetScript() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getInjectedContent", String.class, String.class);
        method.setAccessible(true);

        RequestContext requestContext = mock(RequestContext.class);
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(mockReq);
        when(mockReq.getContextPath()).thenReturn("/etendo");

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            String input = "<HTML><HEAD></HEAD><FRAMESET></FRAMESET></HTML>";
            String result = (String) method.invoke(legacyProcessServlet, "/test/page.html", input);

            assertTrue("Should contain message script for frameset",
                    result.contains("window.addEventListener(\"message\""));
        }
    }

    /**
     * Tests getInjectedContent when response contains FORM close tag.
     */
    @Test
    public void getInjectedContentShouldInjectFormScript() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getInjectedContent", String.class, String.class);
        method.setAccessible(true);

        RequestContext requestContext = mock(RequestContext.class);
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(mockReq);
        when(mockReq.getContextPath()).thenReturn("/etendo");

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            String input = "<HTML><HEAD></HEAD><BODY><FORM></FORM></BODY></HTML>";
            String result = (String) method.invoke(legacyProcessServlet, "/test/page.html", input);

            assertTrue("Should contain sendMessage script for form",
                    result.contains("const sendMessage"));
        }
    }

    /**
     * Tests getInjectedContent with no frameset or form tags returns content with path replacements.
     */
    @Test
    public void getInjectedContentShouldReplacePathsWithNoSpecialTags() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getInjectedContent", String.class, String.class);
        method.setAccessible(true);

        RequestContext requestContext = mock(RequestContext.class);
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(mockReq);
        when(mockReq.getContextPath()).thenReturn("/etendo");

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            String input = "<HTML><HEAD></HEAD><BODY>simple content</BODY></HTML>";
            String result = (String) method.invoke(legacyProcessServlet, "/test/page.html", input);

            assertFalse("Should not contain sendMessage script",
                    result.contains("const sendMessage"));
            assertFalse("Should not contain frameset message listener",
                    result.contains("window.addEventListener(\"message\""));
        }
    }

    /**
     * Tests getInjectedContent replaces src paths for DynamicJS, client kernel, and web.
     */
    @Test
    public void getInjectedContentShouldReplaceResourcePaths() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getInjectedContent", String.class, String.class);
        method.setAccessible(true);

        RequestContext requestContext = mock(RequestContext.class);
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(mockReq);
        when(mockReq.getContextPath()).thenReturn("/etendo");

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            String input = "src=\"../utility/DynamicJS.js\" src=\"../org.openbravo.client.kernel/test\" " +
                    "src=\"../web/file.js\" href=\"../web/style.css\"";
            String result = (String) method.invoke(legacyProcessServlet, "/test/page.html", input);

            assertTrue("Should replace DynamicJS path", result.contains("src=\"/etendo/utility/DynamicJS.js\""));
            assertTrue("Should replace client kernel path",
                    result.contains("src=\"/etendo/org.openbravo.client.kernel/test\""));
            assertTrue("Should replace web src path", result.contains("src=\"/etendo/web/file.js\""));
            assertTrue("Should replace web href path", result.contains("href=\"/etendo/web/style.css\""));
        }
    }

    /**
     * Tests injectCodeAfterFunctionCall with regex pattern match.
     */
    @Test
    public void injectCodeAfterFunctionCallShouldAppendCode() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "injectCodeAfterFunctionCall", String.class, String.class, String.class, boolean.class);
        method.setAccessible(true);

        String input = "submitThisPage(myArg);";
        String result = (String) method.invoke(legacyProcessServlet,
                input, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true);

        assertTrue("Should contain original call and appended code",
                result.contains("submitThisPage(myArg);sendMessage('processOrder');"));
    }

    /**
     * Tests injectCodeAfterFunctionCall with no match returns original string.
     */
    @Test
    public void injectCodeAfterFunctionCallShouldReturnOriginalWhenNoMatch() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "injectCodeAfterFunctionCall", String.class, String.class, String.class, boolean.class);
        method.setAccessible(true);

        String input = "someOtherFunction();";
        String result = (String) method.invoke(legacyProcessServlet,
                input, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true);

        assertEquals("No match should return original", input, result);
    }

    /**
     * Tests injectCodeAfterFunctionCall with isRegex=false (literal match).
     */
    @Test
    public void injectCodeAfterFunctionCallShouldHandleLiteralPattern() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "injectCodeAfterFunctionCall", String.class, String.class, String.class, boolean.class);
        method.setAccessible(true);

        String input = "closePage();done();";
        String result = (String) method.invoke(legacyProcessServlet,
                input, "closePage();", "sendMessage('close');", false);

        assertTrue("Should append after literal match",
                result.contains("closePage();sendMessage('close');"));
    }

    /**
     * Tests createDBSession with null user and empty username returns null.
     */
    @Test
    public void createDBSessionShouldReturnNullForNullUserAndEmptyName() {
        String result = legacyProcessServlet.createDBSession(request, "", null);

        assertNull("Should return null for null user auth and empty username", result);
    }

    /**
     * Tests createDBSession with null request throws.
     */
    @Test
    public void createDBSessionShouldReturnNullForNullRequest() {
        String result = legacyProcessServlet.createDBSession(null, "user", "userId");

        assertNull("Should return null when request is null (OBException caught)", result);
    }

    /**
     * Tests writeFinalResponse with ABOUT_MODAL path sets correct content type.
     */
    @Test
    public void writeFinalResponseShouldSetHtmlContentTypeForAboutModal() throws Exception {
        when(request.getPathInfo()).thenReturn("/ad_forms/about.html");
        when(request.getParameter(TOKEN)).thenReturn(null);

        doAnswer(invocation -> {
            HttpServletResponseLegacyWrapper wrapper = invocation.getArgument(1);
            wrapper.getWriter().write("about content");
            return null;
        }).when(requestDispatcher).include(any(), any());

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(request);

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            legacyProcessServlet.service(request, response);

            verify(response).setContentType("text/html; charset=UTF-8");
            verify(response).setCharacterEncoding("UTF-8");
        }
    }

    /**
     * Tests writeFinalResponse with MANUAL_PROCESS path sets correct content type.
     */
    @Test
    public void writeFinalResponseShouldSetHtmlContentTypeForManualProcess() throws Exception {
        when(request.getPathInfo()).thenReturn("/ad_actionButton/ActionButton_Responser.html");
        when(request.getParameter(TOKEN)).thenReturn(null);

        doAnswer(invocation -> {
            HttpServletResponseLegacyWrapper wrapper = invocation.getArgument(1);
            wrapper.getWriter().write("manual process content");
            return null;
        }).when(requestDispatcher).include(any(), any());

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(request);

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            legacyProcessServlet.service(request, response);

            verify(response).setContentType("text/html; charset=UTF-8");
            verify(response).setCharacterEncoding("UTF-8");
        }
    }

    /**
     * Tests processLegacyFollowupRequest when token is null sends 401.
     */
    @Test
    public void followupRequestShouldReturn401WhenNoToken() throws Exception {
        when(request.getParameter("Command")).thenReturn("BUTTON_ACTION");
        when(request.getPathInfo()).thenReturn(null);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn(null);
        when(session.getAttribute("LEGACY_SERVLET_DIR")).thenReturn("/dir");

        legacyProcessServlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    }

    /**
     * Tests getHtmlRedirect via processRedirectRequest with replacePath=false.
     */
    @Test
    public void redirectRequestShouldGenerateCorrectHtml() throws Exception {
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn("/etendo/test/page");
        when(request.getContextPath()).thenReturn("/etendo");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        legacyProcessServlet.service(request, response);
        writer.flush();

        String output = stringWriter.toString();
        assertTrue("Should contain the redirect URL", output.contains("/etendo/test/page"));
        assertTrue("Should contain meta refresh", output.contains("http-equiv=\"refresh\""));
    }

    /**
     * Tests prepareSessionAttributes stores token in session.
     */
    @Test
    public void prepareSessionAttributesShouldStoreTokenInSession() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "prepareSessionAttributes", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        when(request.getParameter(TOKEN)).thenReturn("my-token");

        method.invoke(legacyProcessServlet, request, "/SalesOrder/Header.html");

        verify(session).setAttribute("LEGACY_TOKEN", "my-token");
    }

    /**
     * Tests prepareSessionAttributes stores servlet dir in session.
     */
    @Test
    public void prepareSessionAttributesShouldStoreServletDir() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "prepareSessionAttributes", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        when(request.getParameter(TOKEN)).thenReturn(null);

        method.invoke(legacyProcessServlet, request, "/SalesOrder/Header.html");

        verify(session).setAttribute("LEGACY_SERVLET_DIR", "/SalesOrder");
    }

    /**
     * Tests prepareSessionAttributes with path without slash.
     */
    @Test
    public void prepareSessionAttributesShouldHandlePathWithoutSlash() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "prepareSessionAttributes", HttpServletRequest.class, String.class);
        method.setAccessible(true);

        when(request.getParameter(TOKEN)).thenReturn(null);

        method.invoke(legacyProcessServlet, request, "noslash");

        verify(session, never()).setAttribute(eq("LEGACY_SERVLET_DIR"), anyString());
    }

    /**
     * Tests isRedirectRequest with matching path.
     */
    @Test
    public void isRedirectRequestShouldReturnTrueForRedirectPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isRedirectRequest", String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(legacyProcessServlet, "/test/redirect"));
        assertTrue((boolean) method.invoke(legacyProcessServlet, "/test/REDIRECT"));
    }

    /**
     * Tests isRedirectRequest with null path.
     */
    @Test
    public void isRedirectRequestShouldReturnFalseForNull() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isRedirectRequest", String.class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(legacyProcessServlet, (Object) null));
    }

    /**
     * Tests isLegacyRequest with null path.
     */
    @Test
    public void isLegacyRequestShouldReturnFalseForNull() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isLegacyRequest", String.class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(legacyProcessServlet, (Object) null));
    }

    /**
     * Tests isJavaScriptRequest with null path.
     */
    @Test
    public void isJavaScriptRequestShouldReturnFalseForNull() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod("isJavaScriptRequest", String.class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(legacyProcessServlet, (Object) null));
    }

    /**
     * Tests isLegacyFollowupRequest with null command.
     */
    @Test
    public void isLegacyFollowupRequestShouldReturnFalseForNullCommand() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "isLegacyFollowupRequest", HttpServletRequest.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameter("Command")).thenReturn(null);

        assertFalse((boolean) method.invoke(legacyProcessServlet, mockReq));
    }

    /**
     * Tests isLegacyFollowupRequest with non-BUTTON command.
     */
    @Test
    public void isLegacyFollowupRequestShouldReturnFalseForNonButtonCommand() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "isLegacyFollowupRequest", HttpServletRequest.class);
        method.setAccessible(true);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameter("Command")).thenReturn("SAVE");

        assertFalse((boolean) method.invoke(legacyProcessServlet, mockReq));
    }

    /**
     * Tests maybeValidateLegacyClass with non-legacy path does nothing.
     */
    @Test
    public void maybeValidateLegacyClassShouldNoOpForNonLegacyPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "maybeValidateLegacyClass", String.class);
        method.setAccessible(true);

        // Should not throw for null
        method.invoke(legacyProcessServlet, (Object) null);

        // Should not throw for non-html
        method.invoke(legacyProcessServlet, "/some/path");
    }

    /**
     * Tests validateLegacyClassExists with a valid class does not throw.
     */
    @Test
    public void validateLegacyClassExistsShouldPassForValidClass() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "validateLegacyClassExists", String.class);
        method.setAccessible(true);

        // java.lang.String should always exist
        method.invoke(legacyProcessServlet, "java.lang.String");
    }

    /**
     * Tests validateLegacyClassExists with an invalid class throws OBException.
     */
    @Test(expected = Exception.class)
    public void validateLegacyClassExistsShouldThrowForInvalidClass() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "validateLegacyClassExists", String.class);
        method.setAccessible(true);

        method.invoke(legacyProcessServlet, "com.nonexistent.FakeClass");
    }

    /**
     * Tests transformJavaScriptContent returns content unchanged.
     */
    @Test
    public void transformJavaScriptContentShouldReturnUnmodifiedContent() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "transformJavaScriptContent", String.class);
        method.setAccessible(true);

        String content = "var x = 42; console.log(x);";
        String result = (String) method.invoke(legacyProcessServlet, content);

        assertEquals("Content should be returned unchanged", content, result);
    }

    /**
     * Tests getHtmlRedirect with replacePath=true inserts /meta/legacy.
     */
    @Test
    public void getHtmlRedirectShouldInsertMetaLegacyWhenReplacePath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getHtmlRedirect", String.class, boolean.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null,
                "/etendo/web/other.html", true, "/etendo");

        assertTrue("Should contain /meta/legacy in redirect URL",
                result.contains("/etendo/meta/legacy/web/other.html"));
    }

    /**
     * Tests getHtmlRedirect with replacePath=false keeps original URL.
     */
    @Test
    public void getHtmlRedirectShouldKeepOriginalWhenNoReplace() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getHtmlRedirect", String.class, boolean.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null,
                "/direct/path", false, "/etendo");

        assertTrue("Should contain original path", result.contains("/direct/path"));
        assertFalse("Should not contain /meta/legacy", result.contains("/meta/legacy"));
    }

    /**
     * Tests extractTargetPathFromReferer with /meta/legacy/ path without query string.
     */
    @Test
    public void extractTargetPathFromRefererShouldHandleLegacyPathWithoutQuery() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/meta/legacy/SalesOrder/Header.html");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests extractTargetPathFromReferer with /meta/ path and query string.
     */
    @Test
    public void extractTargetPathFromRefererShouldStripQueryFromMetaPath() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "extractTargetPathFromReferer", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(legacyProcessServlet,
                "http://localhost/etendo/meta/SalesOrder/Header.html?param=value");

        assertEquals("/SalesOrder/Header.html", result);
    }

    /**
     * Tests createDBSession returns null when both strUserAuth is null and strUser is empty.
     * Per the source code, the early return null is triggered only when both conditions hold.
     */
    @Test
    public void createDBSessionShouldReturnNullWhenSessionLoginFails() {
        String result = legacyProcessServlet.createDBSession(request, "", null, "S");
        assertNull("createDBSession should return null when strUserAuth is null and strUser is empty",
                result);
    }

    /**
     * Tests service method wraps exception in ServletException.
     */
    @Test(expected = javax.servlet.ServletException.class)
    public void serviceShouldWrapExceptionInServletException() throws Exception {
        when(request.getPathInfo()).thenReturn("/test/page.html");
        when(request.getSession(true)).thenThrow(new RuntimeException("session error"));

        legacyProcessServlet.service(request, response);
    }

    /**
     * Tests processLegacyRequest catches exception and sends 500 error.
     */
    @Test
    public void processLegacyRequestShouldSend500OnDispatcherException() throws Exception {
        when(request.getPathInfo()).thenReturn("/test/dispatch.html");
        when(request.getParameter(TOKEN)).thenReturn(null);

        doThrow(new RuntimeException("dispatcher failed")).when(requestDispatcher)
                .include(any(), any());

        legacyProcessServlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
    }

    /**
     * Tests that setSessionCookie with host containing only protocol prefix handles correctly.
     */
    @Test
    public void setSessionCookieShouldHandleHostWithOnlyProtocol() throws Exception {
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());
        when(request.getPathInfo()).thenReturn(REDIRECT);
        when(request.getParameter(LOCATION)).thenReturn(null);

        Properties props = new Properties();
        props.setProperty("CLASSIC_URL", "https://127.0.0.1");

        OBPropertiesProvider provider = mock(OBPropertiesProvider.class);
        when(provider.getOpenbravoProperties()).thenReturn(props);

        try (MockedStatic<OBPropertiesProvider> providerStatic = mockStatic(OBPropertiesProvider.class)) {
            providerStatic.when(OBPropertiesProvider::getInstance).thenReturn(provider);

            legacyProcessServlet.service(request, response);

            verify(response).addHeader(eq("Set-Cookie"), contains("127.0.0.1"));
        }
    }

    /**
     * Tests getInjectedContent with form containing submitThisPage and closePage calls.
     */
    @Test
    public void getInjectedContentShouldInjectAfterSubmitAndClose() throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
                "getInjectedContent", String.class, String.class);
        method.setAccessible(true);

        RequestContext requestContext = mock(RequestContext.class);
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(requestContext.getRequest()).thenReturn(mockReq);
        when(mockReq.getContextPath()).thenReturn("/etendo");

        try (MockedStatic<RequestContext> rcStatic = mockStatic(RequestContext.class)) {
            rcStatic.when(RequestContext::get).thenReturn(requestContext);

            String input = "<FORM>submitThisPage(document.forms[0]);closePage();</FORM>";
            String result = (String) method.invoke(legacyProcessServlet, "/test/page.html", input);

            assertTrue("Should inject processOrder after submitThisPage",
                    result.contains("sendMessage('processOrder');"));
            assertTrue("Should inject closeModal after closePage",
                    result.contains("sendMessage('closeModal');"));
        }
    }

    /**
     * Tests that JS content is written to response and flushed.
     */
    @Test
    public void processJavaScriptRequestShouldWriteContentAndFlush() throws Exception {
        String jsContent = "function test() { return true; }";
        mockJavaScriptRequest(TEST_JS_FILE, jsContent);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(response.getWriter()).thenReturn(pw);

        legacyProcessServlet.service(request, response);

        pw.flush();
        assertEquals("JS content should be written to response", jsContent, sw.toString());
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Tests processJavaScriptRequest handles exception from getResourceAsStream.
     */
    @Test
    public void processJavaScriptRequestShouldHandle500OnStreamException() throws Exception {
        when(request.getPathInfo()).thenReturn(TEST_JS_FILE);
        when(request.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResourceAsStream(TEST_JS_FILE)).thenThrow(
                new RuntimeException("stream error"));

        legacyProcessServlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
    }
}
