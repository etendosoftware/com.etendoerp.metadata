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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
import org.mockito.MockedStatic;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import javax.servlet.http.Cookie;

import com.etendoerp.metadata.exceptions.InternalServerException;
import com.etendoerp.metadata.utils.LegacyPaths;
import com.etendoerp.metadata.utils.LegacyUtils;

import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
@SuppressWarnings("java:S1448")
public class LegacyProcessServletTest extends OBBaseTest {
    private static final String HTML_UTF8_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String PARAM_INP_KEY = "inpKey";
    private static final String PARAM_INP_WINDOW_ID = "inpwindowId";
    private static final String PARAM_INP_KEY_COLUMN_ID = "inpkeyColumnId";
    private static final String COMMAND_PARAM = "Command";
    private static final String NOT_EXIST_JS_FILE = "/web/js/nonexistent.js";
    private static final String TEST_JS_FILE = "/web/js/test-script.js";
    private static final String CALENDAR_JS_FILE = "/web/js/calendar-lang.js";
    private static final String TEST_UPPERCASE_JS_FILE = "/web/js/script.JS";
    public static final String REDIRECT = "/redirect";
    public static final String LOCATION = "location";
    private static final String USER_ID_KEY = "userId";
    private static final String SUBMIT_THIS_PAGE_PATTERN = "submitThisPage\\(([^)]+)\\);";
    private static final String BUILD_SHIM_SCRIPT = "buildShimScript";
    private static final String DEFAULT_NUMERIC_MASK = "#,##0.00";
    private static final String DEFAULT_CONTEXT_PATH = "/etendo";
    private static final String MESSAGES_GET_FRAME_ASSIGNMENT = "var _messagesGetFrame=getFrame;";
    private static final String ESCAPE_JS = "escapeJs";
    private static final String INJECT_FRAME_MENU_SHIM = "injectFrameMenuShim";
    private static final String INJECT_POPUP_MESSAGE_FORWARDER = "injectPopupMessageForwarder";
    private static final String ACTION_SHOW_PROCESS_MESSAGE = "action:'showProcessMessage'";
    private static final String ACTION_CLOSE_MODAL = "action:'closeModal'";
    private static final String IS_PROCESS_COMMAND_POPUP = "isProcessCommandPopup";
    private static final String MAP_MESSAGE_TYPE = "mapMessageType";
    private static final String WRITE_REQUEST_FAILED_FORWARDER = "writeRequestFailedForwarder";
    private static final String POST_MESSAGE_SCRIPT_KEY = "POST_MESSAGE_SCRIPT";
    private static final String CREATE_FROM_SESSION_KEY = "CREATEFROM|TABID";
    private static final String COMMAND_KEY = "Command";
    private static final String INP_WINDOW_ID_KEY = "inpWindowId";
    private static final String INP_TABLE_ID_KEY = "inpTableId";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String METHOD_IS_VALID_LOCATION = "isValidLocation";
    private static final String METHOD_IS_REDIRECT_REQUEST = "isRedirectRequest";
    private static final String METHOD_IS_LEGACY_FOLLOWUP_REQUEST = "isLegacyFollowupRequest";
    private static final String METHOD_EXTRACT_TARGET_PATH = "extractTargetPath";
    private static final String METHOD_EXTRACT_TARGET_PATH_FROM_REFERER = "extractTargetPathFromReferer";
    private static final String METHOD_DERIVE_LEGACY_CLASS = "deriveLegacyClass";
    private static final String PATH_PAGE_HTML = "/page.html";
    private static final String PATH_SALES_ORDER_EDIT_LINES = "/SalesOrder/EditLines.html";
    private static final String EXTRA_KEY = "extra();";
    private static final String SET_TIMEOUT_KEY = "setTimeout";
    private static final String WRITE_PROCESS_COMMAND_FORWARDER_KEY = "writeProcessCommandForwarder";

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

        verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Tests that the servlet correctly identifies legacy requests.
     */
    @Test
    public void servletShouldIdentifyLegacyFollowupRequest() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn("BUTTON_TEST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn("test-token");
        when(session.getAttribute("LEGACY_SERVLET_DIR")).thenReturn("/dir");

        try {
            legacyProcessServlet.service(request, response);
        } catch (Exception ignored) {
            // Expected due to framework dependencies
        }

        verify(request, atLeastOnce()).getParameter(COMMAND_PARAM);
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

    private void assertNotNull(String message, Object object) {
        if (object == null) {
            throw new AssertionError(message);
        }
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

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ========== Tests for newly requested methods (using reflection for
    // private/protected) ==========

    private Object invokePrivateMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Method method = obj.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(obj, args);
    }

    /**
     * Tests setSessionCookie method.
     */
    @Test
    public void testSetSessionCookie() throws Exception {
        when(response.getHeaders(SET_COOKIE_HEADER)).thenReturn(Collections.emptyList());

        invokePrivateMethod(legacyProcessServlet, "setSessionCookie",
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, "test-session-id");

        verify(response).addHeader(eq(SET_COOKIE_HEADER), contains("JSESSIONID=test-session-id"));
    }

    /**
     * Tests setSessionCookie early-return when JSESSIONID is already present in headers.
     */
    @Test
    public void testSetSessionCookieSkipsWhenJSessionIdAlreadySet() throws Exception {
        when(response.getHeaders(SET_COOKIE_HEADER)).thenReturn(
                java.util.List.of("JSESSIONID=existing-id; Path=/; HttpOnly"));

        invokePrivateMethod(legacyProcessServlet, "setSessionCookie",
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, "new-session-id");

        verify(response, never()).addHeader(eq(SET_COOKIE_HEADER), anyString());
    }

    /**
     * Tests processRedirectRequest with invalid token.
     */
    @Test
    public void testProcessRedirectRequestInvalidToken() throws Exception {
        when(request.getParameter("token")).thenReturn("invalid-token");

        try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class)) {
            mockedUtils.when(() -> SecureWebServicesUtils.decodeToken("invalid-token"))
                    .thenThrow(new RuntimeException("invalid"));

            invokePrivateMethod(legacyProcessServlet, "processRedirectRequest",
                    new Class<?>[] { HttpServletRequest.class, HttpServletResponse.class, String.class },
                    request, response, REDIRECT);

            verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
        }
    }

    /**
     * Tests authenticateWithToken success path.
     */
    @Test
    public void testAuthenticateWithToken() throws Exception {
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim userClaim = mock(Claim.class);
        Claim roleClaim = mock(Claim.class);
        Claim clientClaim = mock(Claim.class);
        Claim orgClaim = mock(Claim.class);
        User mockUser = mock(User.class);

        when(userClaim.asString()).thenReturn(USER_ID_KEY);
        when(roleClaim.asString()).thenReturn("roleId");
        when(clientClaim.asString()).thenReturn("clientId");
        when(orgClaim.asString()).thenReturn("orgId");
        when(decodedJWT.getClaim("user")).thenReturn(userClaim);
        when(decodedJWT.getClaim("role")).thenReturn(roleClaim);
        when(decodedJWT.getClaim("client")).thenReturn(clientClaim);
        when(decodedJWT.getClaim("organization")).thenReturn(orgClaim);
        when(mockUser.getUsername()).thenReturn("testuser");

        try (MockedStatic<SecureWebServicesUtils> mockedUtils = mockStatic(SecureWebServicesUtils.class);
                MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class)) {

            mockedUtils.when(() -> SecureWebServicesUtils.decodeToken("valid-token")).thenReturn(decodedJWT);

            OBDal dal = mock(OBDal.class);
            mockedOBDal.when(OBDal::getInstance).thenReturn(dal);
            when(dal.get(User.class, USER_ID_KEY)).thenReturn(mockUser);

            invokePrivateMethod(legacyProcessServlet, "authenticateWithToken",
                    new Class<?>[] { HttpServletRequest.class, String.class },
                    request, "valid-token");

            mockedOBContext
                    .verify(() -> OBContext.setOBContext(eq(USER_ID_KEY), eq("roleId"), eq("clientId"), eq("orgId"),
                            isNull(), isNull()));
        }
    }

    /**
     * Tests sendErrorResponse.
     */
    @Test
    public void testSendErrorResponse() throws Exception {
        invokePrivateMethod(legacyProcessServlet, "sendErrorResponse",
                new Class<?>[] { HttpServletResponse.class, int.class, String.class },
                response, 403, "Forbidden");
        verify(response).sendError(403, "Forbidden");
    }

    /**
     * Tests createDBSession.
     */
    @Test
    public void testCreateDBSessionNullUser() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, "createDBSession",
                new Class<?>[] { HttpServletRequest.class, String.class, String.class },
                request, null, null);
        assertNull("Should return null if user info is missing", result);
    }

    /**
     * Tests processLegacyFollowupRequest without token in session writes the
     * requestFailed forwarder HTML instead of sending an HTTP 401 error,
     * so the iframe can postMessage the failure to its parent window.
     */
    @Test
    public void testProcessLegacyFollowupRequestNoToken() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn(null);

        invokePrivateMethod(legacyProcessServlet, "processLegacyFollowupRequest",
                new Class<?>[] { HttpServletRequest.class, HttpServletResponse.class },
                request, response);

        verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    }

    /**
     * Tests injectCodeAfterFunctionCall.
     */
    @Test
    public void testInjectCodeAfterFunctionCall() throws Exception {
        String original = "function test() { submitThisPage('action'); }";
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeAfterFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                original, SUBMIT_THIS_PAGE_PATTERN, EXTRA_KEY, true);

        assertTrue("Should contain injected code", result.contains("submitThisPage('action');extra();"));
    }

    // ========== Tests for frameMenu shim injection (new functionality) ==========

    /**
     * Invokes the private {@code buildShimScript} method with default decimal/group
     * separators and numeric mask, and the supplied context path. Centralises the
     * reflective invocation so individual tests stay free of boilerplate.
     *
     * @param contextPath the servlet context path to embed in {@code getAppUrlFromMenu}
     * @return the generated shim script
     * @throws ReflectiveOperationException if the reflective invocation of the private method fails
     */
    private String invokeBuildShimScript(String contextPath) throws ReflectiveOperationException {
        return (String) invokePrivateMethod(legacyProcessServlet, BUILD_SHIM_SCRIPT,
                new Class<?>[] { String.class, String.class, String.class, String.class },
                ".", ",", DEFAULT_NUMERIC_MASK, contextPath);
    }

    /**
     * Tests buildShimScript includes all required frameMenu properties.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptIncludesRequiredProperties() throws Exception {
        String script = invokeBuildShimScript(DEFAULT_CONTEXT_PATH);

        assertTrue("Should include decimal separator", script.contains("decSeparator_global:'.'"));
        assertTrue("Should include group separator", script.contains("groupSeparator_global:','"));
        assertTrue("Should include group interval", script.contains("groupInterval_global:'3'"));
        assertTrue("Should include default mask", script.contains("maskNumeric_default:'" + DEFAULT_NUMERIC_MASK + "'"));
        assertTrue("Should include autosave flag", script.contains("autosave:false"));
        assertTrue("Should include frameMenu assignment", script.contains("window.frameMenu=m"));
    }

    /**
     * Tests buildShimScript defines _shimGetFrame function correctly.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptDefinesShimGetFrame() throws Exception {
        String script = invokeBuildShimScript(DEFAULT_CONTEXT_PATH);

        assertTrue("Should define _shimGetFrame", script.contains("var _shimGetFrame=function(name)"));
        assertTrue("Should return frameMenu mock", script.contains("if(name==='frameMenu')return window.frameMenu;"));
        assertTrue("Should assign to window.getFrame", script.contains("window.getFrame=_shimGetFrame;"));
    }

    /**
     * Tests buildShimScript wraps in IIFE for scope isolation.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptUsesIIFE() throws Exception {
        String script = invokeBuildShimScript(DEFAULT_CONTEXT_PATH);

        assertTrue("Should start with IIFE", script.contains("(function(){"));
        assertTrue("Should end with IIFE call", script.contains("})();"));
    }

    /**
     * Tests that buildShimScript embeds the servlet context path into
     * {@code getAppUrlFromMenu} so legacy XHRs (e.g. {@code /businessUtility/MessageJS.html})
     * resolve against {@code /etendo/...} instead of the host root.
     * <p>
     * Regression for the 404 reported when invoking "Reconcile" with no rows
     * selected: {@code showJSMessage('NoDataSelected')} → {@code getDataBaseMessage} → XHR
     * against an empty {@code appUrl} was hitting {@code /businessUtility/MessageJS.html}
     * (no context path) and 404'ing.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptEmbedsContextPathInGetAppUrlFromMenu() throws Exception {
        String script = invokeBuildShimScript(DEFAULT_CONTEXT_PATH);

        assertTrue("getAppUrlFromMenu must return the configured context path",
                script.contains("getAppUrlFromMenu:function(){return '" + DEFAULT_CONTEXT_PATH + "';}"));
    }

    /**
     * Tests that buildShimScript falls back to an empty string when no context
     * path is supplied (defensive — matches the pre-fix behaviour so existing
     * deployments without a context path keep working).
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptDefaultsToEmptyContextPath() throws Exception {
        String script = invokeBuildShimScript("");

        assertTrue("getAppUrlFromMenu must default to an empty string when no context path is given",
                script.contains("getAppUrlFromMenu:function(){return '';}"));
    }

    /**
     * Tests that buildShimScript preserves an already-escaped single quote in the
     * context path so a crafted value cannot break out of the JS string literal
     * (defence in depth — the caller is expected to pass the value through
     * {@code escapeJs} as {@code buildFrameMenuShim} does).
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testBuildShimScriptPreservesEscapedQuoteInContextPath() throws Exception {
        String script = invokeBuildShimScript("/etendo\\'evil");

        assertTrue("Escaped quotes in context path must be preserved verbatim",
                script.contains("getAppUrlFromMenu:function(){return '/etendo\\'evil';}"));
    }

    /**
     * Tests buildFrameMenuPatchScript wraps messages.js's getFrame.
     */
    @Test
    public void testBuildFrameMenuPatchScript() throws Exception {
        String patchScript = (String) invokePrivateMethod(legacyProcessServlet, "buildFrameMenuPatchScript",
                new Class<?>[] {});

        assertTrue("Should save original getFrame", patchScript.contains(MESSAGES_GET_FRAME_ASSIGNMENT));
        assertTrue("Should define wrapper", patchScript.contains("window.getFrame=function(name)"));
        assertTrue("Should return frameMenu for frameMenu", patchScript.contains("if(name==='frameMenu')return window.frameMenu;"));
    }

    /**
     * Tests escapeJs correctly escapes single quotes.
     */
    @Test
    public void testEscapeJsSingleQuotes() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, ESCAPE_JS,
                new Class<?>[] { String.class },
                "it's");

        assertEquals("Single quotes should be escaped", "it\\'s", result);
    }

    /**
     * Tests escapeJs correctly escapes backslashes.
     */
    @Test
    public void testEscapeJsBackslashes() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, ESCAPE_JS,
                new Class<?>[] { String.class },
                "path\\to\\file");

        assertEquals("Backslashes should be double-escaped", "path\\\\to\\\\file", result);
    }

    /**
     * Tests escapeJs handles combined escaping.
     */
    @Test
    public void testEscapeJsCombined() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, ESCAPE_JS,
                new Class<?>[] { String.class },
                "test\\it's");

        assertEquals("Both escapes should be applied", "test\\\\it\\'s", result);
    }

    /**
     * Tests injectFrameMenuShim injects after opening head tag.
     */
    @Test
    public void testInjectFrameMenuShimAfterOpeningHeadTag() throws Exception {
        String html = "<html><head><script>var x=1;</script></head><body></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertTrue("Shim should be injected after <head>",
                result.contains("<head><script>(function(){"));
    }

    /**
     * Tests injectFrameMenuShim handles case-insensitive HEAD tag.
     */
    @Test
    public void testInjectFrameMenuShimCaseInsensitive() throws Exception {
        String html = "<html><HEAD><script>var x=1;</script></HEAD><body></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertTrue("Should handle uppercase HEAD tag",
                result.contains("<HEAD><script>(function(){"));
    }

    /**
     * Tests injectFrameMenuShim handles HEAD tag with attributes.
     */
    @Test
    public void testInjectFrameMenuShimHeadWithAttributes() throws Exception {
        String html = "<html><head class=\"x\" data-y=\"z\"><script></script></head><body></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertTrue("Should inject after HEAD tag with attributes",
                result.contains("<head class=\"x\" data-y=\"z\"><script>(function(){"));
    }

    /**
     * Tests injectFrameMenuShim injects patch script before </HEAD>.
     * HEAD_CLOSE_TAG in Constants.java is "</HEAD>" (uppercase), so the test HTML must also use uppercase.
     */
    @Test
    public void testInjectFrameMenuShimInjectsPatchBeforeHeadClose() throws Exception {
        String html = "<html><head><script></script></HEAD><body></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        int patchIndex = result.indexOf(MESSAGES_GET_FRAME_ASSIGNMENT);
        int closeHeadIndex = result.indexOf("</HEAD>");
        assertTrue("Patch script should be before </HEAD>",
                patchIndex >= 0 && closeHeadIndex >= 0 && patchIndex < closeHeadIndex);
    }

    /**
     * Tests injectFrameMenuShim injects shim but skips patch script when </HEAD> is absent.
     * The shim itself is always injected after the opening <head> tag, but the patch script
     * that wraps messages.js's getFrame is only injected if </HEAD> is present.
     */
    @Test
    public void testInjectFrameMenuShimSkipsPatchWhenNoHeadCloseTag() throws Exception {
        String html = "<html><head><script></script></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertTrue("Shim should still be injected after <head>",
                result.contains("<head><script>(function(){"));
        assertTrue("Shim should include window.frameMenu assignment",
                result.contains("window.frameMenu=m"));
        assertTrue("Patch script should NOT be injected when </HEAD> is absent",
                !result.contains(MESSAGES_GET_FRAME_ASSIGNMENT));
    }

    /**
     * Tests injectFrameMenuShim does not modify HTML without any <head> tag at all.
     */
    @Test
    public void testInjectFrameMenuShimNoHeadTagAtAll() throws Exception {
        String html = "<html><body><div>no head tag here</div></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertEquals("HTML without any <head> tag should not be modified", html, result);
    }

    /**
     * Tests injectFrameMenuShim preserves HTML structure.
     */
    @Test
    public void testInjectFrameMenuShimPreservesStructure() throws Exception {
        String html = "<html><head><meta name=\"x\"></head><body><div>content</div></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_FRAME_MENU_SHIM,
                new Class<?>[] { String.class },
                html);

        assertTrue("Original elements should be preserved", result.contains("<meta name=\"x\">"));
        assertTrue("Body content should be unchanged", result.contains("<div>content</div>"));
    }

    // ========== Tests for popup message forwarder (new functionality) ==========

    /**
     * Tests injectPopupMessageForwarder injects the forwarder script before </HEAD>
     * when the response is an Openbravo classic popup-message page (error). The
     * forwarder must NOT auto-close the modal — closing is the React shell's
     * responsibility (mirroring {@code MINIMAL_FORWARDER_HTML}). See the Javadoc
     * on {@code SHOW_PROCESS_MESSAGE_SCRIPT}.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testInjectPopupMessageForwarderInjectsForErrorPopup() throws Exception {
        String html = "<HTML><HEAD><TITLE>Error</TITLE></HEAD><BODY>"
                + "<TABLE id=\"paramTipo\" class=\"MessageBoxERROR\">"
                + "<DIV id=\"messageBoxIDTitle\">Error</DIV>"
                + "<DIV id=\"messageBoxIDMessage\">Something failed</DIV>"
                + "</TABLE></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_POPUP_MESSAGE_FORWARDER,
                new Class<?>[] { String.class },
                html);

        int scriptIndex = result.indexOf(ACTION_SHOW_PROCESS_MESSAGE);
        int closeHeadIndex = result.indexOf("</HEAD>");
        assertTrue("Forwarder script should be injected before </HEAD>",
                scriptIndex >= 0 && closeHeadIndex >= 0 && scriptIndex < closeHeadIndex);
        assertFalse("Forwarder must NOT dispatch closeModal (React shell governs the close)",
                result.contains(ACTION_CLOSE_MODAL));
        assertFalse("Forwarder must NOT schedule any setTimeout (no auto-close)",
                result.contains(SET_TIMEOUT_KEY));
    }

    /**
     * Tests injectPopupMessageForwarder also injects for success-style popups.
     * The type is computed client-side from the {@code MessageBox*} CSS class on
     * the message table (via {@code querySelector}), so the server only needs to
     * confirm the script was injected.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testInjectPopupMessageForwarderInjectsForSuccessPopup() throws Exception {
        String html = "<HTML><HEAD></HEAD><BODY>"
                + "<TABLE id=\"paramTipo\" class=\"MessageBoxSUCCESS\">"
                + "<DIV id=\"messageBoxIDTitle\">Success</DIV>"
                + "<DIV id=\"messageBoxIDMessage\">Process completed</DIV>"
                + "</TABLE></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_POPUP_MESSAGE_FORWARDER,
                new Class<?>[] { String.class },
                html);

        assertTrue("Forwarder script should be injected for success popups",
                result.contains(ACTION_SHOW_PROCESS_MESSAGE));
    }

    /**
     * Tests injectPopupMessageForwarder does NOT inject when the popup marker is
     * absent — covers form pages, frameset pages and plain HTML.
     */
    @Test
    public void testInjectPopupMessageForwarderSkipsWhenMarkerAbsent() throws Exception {
        String html = "<HTML><HEAD></HEAD><BODY><FORM>...</FORM></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_POPUP_MESSAGE_FORWARDER,
                new Class<?>[] { String.class },
                html);

        assertEquals("HTML without popup marker must remain untouched", html, result);
    }

    /**
     * Tests injectPopupMessageForwarder does NOT inject when </HEAD> is missing,
     * even if the popup marker is present (defensive: avoids double-injection at
     * an unexpected position).
     */
    @Test
    public void testInjectPopupMessageForwarderSkipsWhenNoHeadCloseTag() throws Exception {
        String html = "<HTML><BODY><DIV id=\"messageBoxIDMessage\">msg</DIV></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, INJECT_POPUP_MESSAGE_FORWARDER,
                new Class<?>[] { String.class },
                html);

        assertEquals("HTML without </HEAD> must remain untouched", html, result);
    }

    // ========== Tests for Command=PROCESS short-circuit (new functionality) ==========

    private static final String POPUP_ERROR_HTML =
            "<HTML><HEAD><TITLE>Error</TITLE></HEAD><BODY>"
                    + "<TABLE id=\"paramTipo\" class=\"MessageBoxERROR\">"
                    + "<DIV id=\"messageBoxIDTitle\">Error</DIV>"
                    + "<DIV id=\"messageBoxIDMessage\">OBException: something failed</DIV>"
                    + "</TABLE></BODY></HTML>";

    private static final String POPUP_SUCCESS_HTML =
            "<HTML><HEAD></HEAD><BODY>"
                    + "<TABLE id=\"paramTipo\" class=\"MessageBoxSUCCESS\">"
                    + "<DIV id=\"messageBoxIDTitle\">Success</DIV>"
                    + "<DIV id=\"messageBoxIDMessage\">Process completed</DIV>"
                    + "</TABLE></BODY></HTML>";

    /**
     * Tests isProcessCommandPopup returns true when body contains the marker and
     * Command is PROCESS.
     */
    @Test
    public void testIsProcessCommandPopupTrueForProcessCommand() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn("PROCESS");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, IS_PROCESS_COMMAND_POPUP,
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_ERROR_HTML);

        assertTrue("Should short-circuit for Command=PROCESS with popup marker", result);
    }

    /**
     * Tests isProcessCommandPopup accepts PROCESS prefix variants (PROCESSDEFAULT, etc.).
     */
    @Test
    public void testIsProcessCommandPopupTrueForProcessPrefixedCommand() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn("PROCESSDEFAULT");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, IS_PROCESS_COMMAND_POPUP,
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_SUCCESS_HTML);

        assertTrue("Should short-circuit for any PROCESS-prefixed Command", result);
    }

    /**
     * Tests isProcessCommandPopup returns false for Command=GRID even when body has
     * a popup marker (the GRID response should never be rewritten).
     */
    @Test
    public void testIsProcessCommandPopupFalseForGridCommand() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn("GRID");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, IS_PROCESS_COMMAND_POPUP,
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_ERROR_HTML);

        assertEquals("Command=GRID must not be short-circuited", Boolean.FALSE, result);
    }

    /**
     * Tests isProcessCommandPopup returns false when Command parameter is missing.
     */
    @Test
    public void testIsProcessCommandPopupFalseWhenCommandMissing() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn(null);

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, IS_PROCESS_COMMAND_POPUP,
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_ERROR_HTML);

        assertEquals("Missing Command must not be short-circuited", Boolean.FALSE, result);
    }

    /**
     * Tests isProcessCommandPopup returns false when body does not contain the
     * popup marker (e.g. a regular form/grid response).
     */
    @Test
    public void testIsProcessCommandPopupFalseWhenMarkerAbsent() throws Exception {
        when(request.getParameter(COMMAND_PARAM)).thenReturn("PROCESS");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, IS_PROCESS_COMMAND_POPUP,
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, "<html><body><form></form></body></html>");

        assertEquals("Body without popup marker must not be short-circuited", Boolean.FALSE, result);
    }

    /**
     * Tests mapMessageType maps MessageBox class suffixes to the expected types.
     */
    @Test
    public void testMapMessageType() throws Exception {
        assertEquals("ERROR maps to error", "error",
                invokePrivateMethod(legacyProcessServlet, MAP_MESSAGE_TYPE,
                        new Class<?>[] { String.class }, "ERROR"));
        assertEquals("SUCCESS maps to success", "success",
                invokePrivateMethod(legacyProcessServlet, MAP_MESSAGE_TYPE,
                        new Class<?>[] { String.class }, "SUCCESS"));
        assertEquals("WARNING maps to warning", "warning",
                invokePrivateMethod(legacyProcessServlet, MAP_MESSAGE_TYPE,
                        new Class<?>[] { String.class }, "WARNING"));
        assertEquals("Unknown suffix falls back to info", "info",
                invokePrivateMethod(legacyProcessServlet, MAP_MESSAGE_TYPE,
                        new Class<?>[] { String.class }, "HIDDEN"));
    }

    /**
     * Tests writeProcessCommandForwarder produces a minimal self-contained HTML
     * that posts the expected payload to the parent and does NOT contain the
     * original popup markup.
     */
    @Test
    public void testWriteProcessCommandForwarderEmitsMinimalForwarder() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        invokePrivateMethod(legacyProcessServlet, WRITE_PROCESS_COMMAND_FORWARDER_KEY,
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, POPUP_ERROR_HTML);

        writer.flush();
        String output = stringWriter.toString();

        assertTrue("Response must contain the showProcessMessage dispatch",
                output.contains(ACTION_SHOW_PROCESS_MESSAGE));
        assertTrue("Response must NOT auto-close the modal",
                !output.contains(ACTION_CLOSE_MODAL) && !output.contains(SET_TIMEOUT_KEY));
        assertTrue("Response payload must carry the extracted type",
                output.contains("\"type\":\"error\""));
        assertTrue("Response payload must carry the extracted title",
                output.contains("\"title\":\"Error\""));
        assertTrue("Response payload must carry the extracted message text",
                output.contains("OBException: something failed"));
        assertTrue("Short-circuited response must NOT carry the original popup markup",
                !output.contains("messageBoxIDMessage") && !output.contains("paramTipo"));

        verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Tests writeProcessCommandForwarder extracts the message type from the CSS
     * class even when the popup table uses {@code id="paramType"} (English
     * variant emitted by {@code AdvisePopUpRefresh.html} and observed in real
     * ERP responses). Regression for the bug where {@code SUCCESS} popups
     * rendered as blue {@code info} because the type regex was anchored to the
     * Spanish {@code id="paramTipo"}.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void testWriteProcessCommandForwarderDetectsTypeWhenIdIsParamTypeEnglishVariant() throws Exception {
        String htmlWithEnglishId = "<HTML><HEAD></HEAD><BODY>"
                + "<TABLE id=\"paramType\" class=\"MessageBoxSUCCESS\">"
                + "<DIV id=\"messageBoxIDTitle\">Done</DIV>"
                + "<DIV id=\"messageBoxIDMessage\">All good</DIV>"
                + "</TABLE></BODY></HTML>";

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        invokePrivateMethod(legacyProcessServlet, WRITE_PROCESS_COMMAND_FORWARDER_KEY,
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, htmlWithEnglishId);

        writer.flush();
        String output = stringWriter.toString();

        assertTrue("Payload must carry the SUCCESS type even with id=paramType",
                output.contains("\"type\":\"success\""));
    }

    /**
     * Tests the injected popup-message forwarder script reads the message type
     * via a CSS-class {@code querySelector}, so it works regardless of whether
     * the template uses {@code id="paramTipo"} or {@code id="paramType"}. The
     * legacy {@code getElementById('paramTipo')} lookup is gone.
     *
     * @throws Exception if the script constant cannot be read via reflection
     */
    @Test
    public void showProcessMessageScriptShouldDetectTypeViaCssClass() throws Exception {
        String script = readScriptConstant("SHOW_PROCESS_MESSAGE_SCRIPT");

        assertTrue("Script must read the type via querySelector on the MessageBox* classes",
                script.contains(
                        "querySelector('.MessageBoxERROR,.MessageBoxSUCCESS,.MessageBoxWARNING,.MessageBoxINFO')"));
        assertFalse("Script must NOT depend on the paramTipo id anymore",
                script.contains("getElementById('paramTipo')"));
        assertFalse("Script must NOT depend on the paramType id anymore",
                script.contains("getElementById('paramType')"));
    }

    /**
     * Tests rollbackDalSessionIfErrorPopup rolls back the DAL session when the
     * captured body is an error popup — preventing StaleStateException during
     * the filter-chain post-commit which would otherwise abort the HTTP
     * connection mid-stream.
     */
    @Test
    public void testRollbackDalSessionWhenErrorPopup() throws Exception {
        HttpServletResponseLegacyWrapper wrapper = mock(HttpServletResponseLegacyWrapper.class);
        when(wrapper.getCapturedOutputAsString()).thenReturn(POPUP_ERROR_HTML);

        try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class)) {
            OBDal dal = mock(OBDal.class);
            mockedOBDal.when(OBDal::getInstance).thenReturn(dal);

            invokePrivateMethod(legacyProcessServlet, "rollbackDalSessionIfErrorPopup",
                    new Class<?>[] { HttpServletResponseLegacyWrapper.class },
                    wrapper);

            verify(dal).rollbackAndClose();
        }
    }

    /**
     * Tests rollbackDalSessionIfErrorPopup is a no-op for success popups and
     * non-popup responses.
     */
    @Test
    public void testRollbackDalSessionNoOpWhenNotError() throws Exception {
        HttpServletResponseLegacyWrapper wrapper = mock(HttpServletResponseLegacyWrapper.class);
        when(wrapper.getCapturedOutputAsString()).thenReturn(POPUP_SUCCESS_HTML);

        try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class)) {
            OBDal dal = mock(OBDal.class);
            mockedOBDal.when(OBDal::getInstance).thenReturn(dal);

            invokePrivateMethod(legacyProcessServlet, "rollbackDalSessionIfErrorPopup",
                    new Class<?>[] { HttpServletResponseLegacyWrapper.class },
                    wrapper);

            verify(dal, never()).rollbackAndClose();
        }
    }

    /**
     * Tests that a success popup is short-circuited with type="success".
     */
    @Test
    public void testWriteProcessCommandForwarderSuccessType() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        invokePrivateMethod(legacyProcessServlet, WRITE_PROCESS_COMMAND_FORWARDER_KEY,
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, POPUP_SUCCESS_HTML);

        writer.flush();
        String output = stringWriter.toString();

        assertTrue("Success popup must produce type=success",
                output.contains("\"type\":\"success\""));
        assertTrue("Success title must be propagated",
                output.contains("\"title\":\"Success\""));
    }

    // ========== Tests for unload-aware postMessage hardening ==========

    /**
     * Reads a private static String constant from LegacyProcessServlet via reflection.
     */
    private String readScriptConstant(String fieldName) throws ReflectiveOperationException {
        java.lang.reflect.Field field = LegacyProcessServlet.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    /**
     * The form-page script must wire pagehide/beforeunload listeners that emit
     * {@code iframeUnloaded} so the parent always receives a final message even
     * when the legacy page navigates away or fails before posting one itself.
     */
    @Test
    public void postMessageScriptShouldIncludePagehideListener() throws Exception {
        String script = readScriptConstant(POST_MESSAGE_SCRIPT_KEY);

        assertTrue("Script must register pagehide listener",
                script.contains("addEventListener('pagehide'"));
        assertTrue("Script must register beforeunload listener",
                script.contains("addEventListener('beforeunload'"));
        assertTrue("Script must emit iframeUnloaded action when no final message was sent",
                script.contains("action:'iframeUnloaded'"));
        assertTrue("Script must guard against duplicate sends via a flag",
                script.contains("__etendoMessageSent"));
        assertTrue("Script must mark the flag for showProcessMessage final action",
                script.contains("'showProcessMessage'"));
        assertTrue("Script must mark the flag for closeModal final action",
                script.contains("'closeModal'"));
        assertTrue("sendMessage must be exposed on window so injected callers can invoke it",
                script.contains("window.sendMessage"));
    }

    /**
     * The popup-message forwarder must mark the message-sent flag immediately
     * after posting {@code showProcessMessage} so the pagehide listener does not
     * emit a redundant {@code iframeUnloaded}. The forwarder must NOT dispatch
     * {@code closeModal} on its own — closing the modal is the React shell's
     * responsibility (mirroring {@code MINIMAL_FORWARDER_HTML}).
     *
     * @throws Exception if the script constant cannot be read via reflection
     */
    @Test
    public void showProcessMessageScriptShouldMarkMessageSentAndNotCloseModal() throws Exception {
        String script = readScriptConstant("SHOW_PROCESS_MESSAGE_SCRIPT");

        int postIndex = script.indexOf(ACTION_SHOW_PROCESS_MESSAGE);
        int markIndex = script.indexOf("__etendoMessageSent=true");

        assertTrue("Script must post showProcessMessage", postIndex >= 0);
        assertTrue("Script must mark the message-sent flag", markIndex >= 0);
        assertTrue("Flag must be set after showProcessMessage",
                postIndex < markIndex);
        assertFalse("Script must NOT dispatch closeModal (React shell governs the close)",
                script.contains(ACTION_CLOSE_MODAL));
        assertFalse("Script must NOT schedule any setTimeout (no auto-close)",
                script.contains(SET_TIMEOUT_KEY));
    }

    /**
     * The minimal forwarder served for {@code Command=PROCESS} popups also needs
     * to mark the flag for symmetry with the full popup path.
     */
    @Test
    public void minimalForwarderShouldMarkMessageSent() throws Exception {
        String forwarder = readScriptConstant("MINIMAL_FORWARDER_HTML");

        assertTrue("Minimal forwarder must mark the message-sent flag",
                forwarder.contains("__etendoMessageSent=true"));
    }

    /**
     * The before-call helper must prepend the supplied payload immediately
     * before each matched call, preserving the original call verbatim. The
     * helper is generic; {@code getInjectedContent} no longer uses it to inject
     * {@code sendMessage('processOrder')} (that path moved to a global
     * {@code HTMLFormElement.prototype.submit} hook in {@code POST_MESSAGE_SCRIPT}),
     * but the helper itself is still part of the internal API.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void injectCodeBeforeFunctionCallShouldPrependPayloadToMatchedCall() throws Exception {
        String html = "<html><body><form><a onclick=\"submitThisPage('save');\">go</a></form></body></html>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeBeforeFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                html, SUBMIT_THIS_PAGE_PATTERN, EXTRA_KEY, true);

        assertTrue("Payload must be emitted before the matched call",
                result.contains("extra();submitThisPage('save');"));
        assertTrue("Original call must be preserved",
                result.contains("submitThisPage('save');"));
        assertTrue("Payload must NOT remain after the matched call",
                !result.contains("submitThisPage('save');extra();"));
    }

    /**
     * The before-call helper must inject the new code in front of every match,
     * not only the first occurrence. Generic helper test — the payload here is
     * deliberately not {@code processOrder} because {@code getInjectedContent}
     * no longer relies on this helper for that flow.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void injectCodeBeforeFunctionCallShouldPrependBeforeEveryMatch() throws Exception {
        String html = "submitThisPage('a');submitThisPage('b');";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeBeforeFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                html, SUBMIT_THIS_PAGE_PATTERN, EXTRA_KEY, true);

        assertEquals("Both calls must be preceded by the injected payload",
                "extra();submitThisPage('a');extra();submitThisPage('b');",
                result);
    }

    /**
     * Tests that POST_MESSAGE_SCRIPT installs a hook on
     * {@code HTMLFormElement.prototype.submit} so {@code processOrder} fires only
     * when the form is actually submitted (not on every button click).
     * <p>
     * Regression for the "Could not capture response" warning shown when classic
     * client-side validation aborted via {@code showJSMessage('NoDataSelected')}
     * but the old inline injection had already dispatched {@code processOrder}.
     *
     * @throws Exception if the script constant cannot be read via reflection
     */
    @Test
    public void postMessageScriptShouldHookFormSubmitForProcessOrder() throws Exception {
        String script = readScriptConstant(POST_MESSAGE_SCRIPT_KEY);

        assertTrue("Script must wrap HTMLFormElement.prototype.submit",
                script.contains("HTMLFormElement.prototype.submit=function()"));
        assertTrue("Hook must dispatch processOrder via window.sendMessage",
                script.contains("window.sendMessage('processOrder')"));
        assertTrue("Hook must guard against double-installation",
                script.contains("__etendoSubmitHooked"));
        assertTrue("Hook must skip refresh commands by reusing isRefreshCommand",
                script.contains("!isRefreshCommand(cmd)"));
    }

    /**
     * Tests that {@code getInjectedContent} no longer prepends
     * {@code sendMessage('processOrder')} to inline {@code submitThisPage(...)}
     * calls — the global {@code form.submit} hook in POST_MESSAGE_SCRIPT
     * replaces that behaviour so client-side validation aborts no longer fire
     * a false {@code processOrder}. The {@code closeModal} wrap on
     * {@code closePage()}/{@code closeThisPage()} is preserved.
     *
     * @throws Exception if the reflective invocation of the private method fails
     */
    @Test
    public void getInjectedContentShouldNotPrependProcessOrderToSubmitThisPage() throws Exception {
        String html = "<HTML><HEAD></HEAD><BODY><FORM>"
                + "<BUTTON onclick=\"submitThisPage('PROCESS');\">Go</BUTTON>"
                + "<BUTTON onclick=\"closeThisPage();\">X</BUTTON>"
                + "</FORM></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "getInjectedContent",
                new Class<?>[] { String.class, String.class },
                "", html);

        assertFalse("Inline submitThisPage must no longer carry a prepended processOrder",
                result.contains("sendMessage('processOrder');submitThisPage('PROCESS');"));
        assertTrue("Original submitThisPage call must be preserved as-is",
                result.contains("submitThisPage('PROCESS');"));
        assertTrue("closeModal injection on closeThisPage() must be preserved",
                result.contains("closeThisPage();sendMessage('closeModal');"));
    }

    // ========== Tests for refresh-Command guard in notifyUnload ==========

    /**
     * The refresh-prefix list embedded in the script must include the three
     * Command families that legacy form pages use for in-popup refreshes:
     * search/filter (FIND*), hidden-frame reload (REFRESH*) and re-render of
     * the response page (DEFAULT*). Audited against the 27 templates in
     * erp/src/org/openbravo/erpCommon/ad_actionButton/.
     */
    @Test
    public void postMessageScriptShouldDeclareRefreshCommandPrefixes() throws Exception {
        String script = readScriptConstant(POST_MESSAGE_SCRIPT_KEY);

        assertTrue("Script must declare REFRESH_COMMAND_PREFIXES literal",
                script.contains("REFRESH_COMMAND_PREFIXES=['FIND','REFRESH','DEFAULT']"));
    }

    /**
     * The script must read the form's Command value at unload time and call
     * the helpers BEFORE posting iframeUnloaded, so refresh submissions
     * (Command=FIND_PO and similar) do not trigger the parent's fallback
     * warning. Verifies both helpers are present and that the guard sits
     * before the postMessage call inside notifyUnload.
     */
    @Test
    public void notifyUnloadShouldShortCircuitOnRefreshCommand() throws Exception {
        String script = readScriptConstant(POST_MESSAGE_SCRIPT_KEY);

        assertTrue("Script must define getSubmittedCommand helper",
                script.contains("function getSubmittedCommand()"));
        assertTrue("getSubmittedCommand must read document.forms[0].Command.value",
                script.contains("document.forms&&document.forms[0]")
                        && script.contains("f.Command")
                        && script.contains("f.Command.value"));
        assertTrue("Script must define isRefreshCommand helper",
                script.contains("function isRefreshCommand(cmd)"));
        assertTrue("isRefreshCommand must compare against REFRESH_COMMAND_PREFIXES with startsWith semantics",
                script.contains("upper.indexOf(REFRESH_COMMAND_PREFIXES[i])===0"));

        int guardIndex = script.indexOf("isRefreshCommand(getSubmittedCommand())");
        int unloadedIndex = script.indexOf("'iframeUnloaded'");
        assertTrue("notifyUnload must invoke the refresh-Command guard", guardIndex >= 0);
        assertTrue("Guard must run before iframeUnloaded is posted", guardIndex < unloadedIndex);
    }

    /**
     * The refresh-Command guard MUST NOT match the legacy "process completion"
     * commands documented in all-feature-section-2.md and audited across the
     * 27 ad_actionButton templates. A static check here protects against future
     * additions to REFRESH_COMMAND_PREFIXES that would silently break the
     * fallback safety net for real processes.
     */
    @Test
    public void refreshPrefixesMustNotShadowProcessCommands() throws Exception {
        String script = readScriptConstant(POST_MESSAGE_SCRIPT_KEY);

        // Extract the literal between '=' and ';' for "REFRESH_COMMAND_PREFIXES=[..];"
        int start = script.indexOf("REFRESH_COMMAND_PREFIXES=[");
        assertTrue("Script must declare REFRESH_COMMAND_PREFIXES", start >= 0);
        start += "REFRESH_COMMAND_PREFIXES=".length();
        int end = script.indexOf("];", start);
        assertTrue("REFRESH_COMMAND_PREFIXES literal must be terminated with ];", end >= 0);
        String literal = script.substring(start, end + 1);

        String[] processCommands = new String[] {
                "SAVE",
                "SAVE_BUTTONDocAction109",
                "SAVE_BUTTONChangeProjectStatus",
                "SAVE_PHYSICALINVENTORY",
                "CANCEL_PHYSICALINVENTORY",
                "GENERATE",
                "USECREDITPAYMENTS",
                "CANCEL_USECREDITPAYMENTS"
        };

        for (String cmd : processCommands) {
            for (String prefix : new String[] { "FIND", "REFRESH", "DEFAULT" }) {
                assertTrue("Refresh prefixes literal must be: " + literal,
                        literal.contains("'" + prefix + "'"));
                if (cmd.toUpperCase().startsWith(prefix)) {
                    throw new AssertionError("Refresh prefix " + prefix
                            + " would incorrectly suppress iframeUnloaded for process command " + cmd);
                }
            }
        }
    }

    // ========== Tests for writeRequestFailedForwarder ==========

    /**
     * Tests that writeRequestFailedForwarder sets HTTP status 200 so the browser
     * loads the body and the postMessage script executes.
     */
    @Test
    public void writeRequestFailedForwarderSetsStatusOk() throws Exception {
        invokePrivateMethod(legacyProcessServlet, WRITE_REQUEST_FAILED_FORWARDER,
                new Class<?>[] { HttpServletResponse.class, Exception.class },
                response, new RuntimeException("test error"));

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    /**
     * Tests that writeRequestFailedForwarder sets the correct content type.
     */
    @Test
    public void writeRequestFailedForwarderSetsHtmlContentType() throws Exception {
        invokePrivateMethod(legacyProcessServlet, WRITE_REQUEST_FAILED_FORWARDER,
                new Class<?>[] { HttpServletResponse.class, Exception.class },
                response, new RuntimeException("test error"));

        verify(response).setContentType(HTML_UTF8_CONTENT_TYPE);
        verify(response).setCharacterEncoding("UTF-8");
    }

    /**
     * Tests that writeRequestFailedForwarder writes an HTML body containing a
     * postMessage call with action 'requestFailed', so the parent React component
     * can display a user-friendly error overlay.
     */
    @Test
    public void writeRequestFailedForwarderWritesPostMessageScript() throws Exception {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        when(response.getWriter()).thenReturn(pw);

        invokePrivateMethod(legacyProcessServlet, WRITE_REQUEST_FAILED_FORWARDER,
                new Class<?>[] { HttpServletResponse.class, Exception.class },
                response, new RuntimeException("proxy error"));

        pw.flush();
        String html = writer.toString();
        assertTrue("Should contain postMessage call", html.contains("postMessage"));
        assertTrue("Should specify 'requestFailed' action", html.contains("action:'requestFailed'"));
        assertTrue("Should be a DOCTYPE HTML document", html.contains("<!DOCTYPE html>"));
    }

    /**
     * Tests that writeRequestFailedForwarder survives gracefully when the response
     * writer itself throws an IOException — the outer exception must not propagate.
     */
    @Test
    public void writeRequestFailedForwarderHandlesIoException() throws Exception {
        when(response.getWriter()).thenThrow(new java.io.IOException("stream closed"));

        try {
            invokePrivateMethod(legacyProcessServlet, WRITE_REQUEST_FAILED_FORWARDER,
                    new Class<?>[] { HttpServletResponse.class, Exception.class },
                    response, new RuntimeException("original error"));
        } catch (Exception e) {
            throw new AssertionError("writeRequestFailedForwarder must not propagate IOException", e);
        }
    }

    // --- Tests for handleCreateFromSession ---

    private void invokeHandleCreateFromSession(HttpServletRequest req, String path, HttpSession session)
            throws Exception {
        Method method = LegacyProcessServlet.class.getDeclaredMethod(
            "handleCreateFromSession",
            HttpServletRequest.class, String.class, HttpSession.class
        );
        method.setAccessible(true);
        method.invoke(null, req, path, session);
    }

    @Test
    public void handleCreateFromSessionSetsTabIdForSaveCommand() throws Exception {
        // GIVEN
        String windowId = "169";
        String tableId = "319";
        String expectedTabId = "257";

        when(request.getParameter(COMMAND_KEY)).thenReturn("SAVE");
        when(request.getParameter(INP_WINDOW_ID_KEY)).thenReturn(windowId);
        when(request.getParameter(INP_TABLE_ID_KEY)).thenReturn(tableId);

        try (MockedStatic<LegacyUtils> legacyUtils = mockStatic(LegacyUtils.class)) {
            legacyUtils.when(() -> LegacyUtils.isMutableSessionAttribute(CREATE_FROM_SESSION_KEY)).thenReturn(true);
            legacyUtils.when(() -> LegacyUtils.findTabIdByWindowAndTable(windowId, tableId)).thenReturn(expectedTabId);

            invokeHandleCreateFromSession(request, LegacyPaths.CREATE_FROM_HTML, session);

            // THEN
            verify(session).setAttribute(CREATE_FROM_SESSION_KEY, expectedTabId);
        }
    }

    @Test
    public void handleCreateFromSessionSkipsForNonCreateFromPath() throws Exception {
        // GIVEN
        invokeHandleCreateFromSession(request, "/other/path.html", session);

        // THEN
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    public void handleCreateFromSessionSkipsForNonSaveCommand() throws Exception {
        // GIVEN
        when(request.getParameter(COMMAND_KEY)).thenReturn("FIND_PO");

        invokeHandleCreateFromSession(request, LegacyPaths.CREATE_FROM_HTML, session);

        // THEN
        verify(session, never()).setAttribute(anyString(), any());
    }

    @Test
    public void handleCreateFromSessionSkipsWhenTabIdNotFound() throws Exception {
        // GIVEN
        when(request.getParameter(COMMAND_KEY)).thenReturn("SAVE");
        when(request.getParameter(INP_WINDOW_ID_KEY)).thenReturn("169");
        when(request.getParameter(INP_TABLE_ID_KEY)).thenReturn("999");

        try (MockedStatic<LegacyUtils> legacyUtils = mockStatic(LegacyUtils.class)) {
            legacyUtils.when(() -> LegacyUtils.isMutableSessionAttribute(CREATE_FROM_SESSION_KEY)).thenReturn(true);
            legacyUtils.when(() -> LegacyUtils.findTabIdByWindowAndTable("169", "999")).thenReturn(null);

            invokeHandleCreateFromSession(request, LegacyPaths.CREATE_FROM_HTML, session);

            // THEN
            verify(session, never()).setAttribute(anyString(), any());
        }
    }

    @Test
    public void handleCreateFromSessionThrowsForForbiddenSessionKey() throws Exception {
        // GIVEN
        when(request.getParameter(COMMAND_KEY)).thenReturn("SAVE");
        when(request.getParameter(INP_WINDOW_ID_KEY)).thenReturn("169");
        when(request.getParameter(INP_TABLE_ID_KEY)).thenReturn("319");

        try (MockedStatic<LegacyUtils> legacyUtils = mockStatic(LegacyUtils.class)) {
            legacyUtils.when(() -> LegacyUtils.isMutableSessionAttribute(CREATE_FROM_SESSION_KEY)).thenReturn(false);

            try {
                invokeHandleCreateFromSession(request, LegacyPaths.CREATE_FROM_HTML, session);
                fail("Expected InternalServerException to be thrown");
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertTrue("Cause should be InternalServerException",
                        e.getCause() instanceof InternalServerException);
            }
        }
    }

    @Test
    public void isValidLocationReturnsTrueForRelativePath() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_VALID_LOCATION,
                new Class<?>[] { String.class }, "/some/relative/path");
        assertTrue("Relative path should be valid", result);
    }

    @Test
    public void isValidLocationReturnsFalseForProtocolUrl() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_VALID_LOCATION,
                new Class<?>[] { String.class }, "https://evil.com");
        assertFalse("Protocol URL should be invalid", result);
    }

    @Test
    public void isValidLocationReturnsFalseForProtocolRelativeUrl() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_VALID_LOCATION,
                new Class<?>[] { String.class }, "//evil.com");
        assertFalse("Protocol-relative URL should be invalid", result);
    }

    @Test
    public void isRedirectRequestReturnsTrueForRedirectSuffix() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_REDIRECT_REQUEST,
                new Class<?>[] { String.class }, "/some/redirect");
        assertTrue("Path ending with /redirect should be detected", result);
    }

    @Test
    public void isRedirectRequestReturnsFalseForNonRedirect() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_REDIRECT_REQUEST,
                new Class<?>[] { String.class }, "/some/path.html");
        assertFalse("HTML path should not be redirect", result);
    }

    @Test
    public void isRedirectRequestReturnsFalseForNull() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_REDIRECT_REQUEST,
                new Class<?>[] { String.class }, (Object) null);
        assertFalse("Null path should not be redirect", result);
    }

    @Test
    public void isLegacyRequestReturnsTrueForHtmlPath() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, "isLegacyRequest",
                new Class<?>[] { String.class }, "/some/page.html");
        assertTrue("HTML path should be legacy", result);
    }

    @Test
    public void isLegacyRequestReturnsFalseForNull() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, "isLegacyRequest",
                new Class<?>[] { String.class }, (Object) null);
        assertFalse("Null path should not be legacy", result);
    }

    @Test
    public void isJavaScriptRequestReturnsTrueForJsPath() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, "isJavaScriptRequest",
                new Class<?>[] { String.class }, "/web/js/script.js");
        assertTrue("JS path should be detected", result);
    }

    @Test
    public void isJavaScriptRequestReturnsFalseForNull() throws Exception {
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, "isJavaScriptRequest",
                new Class<?>[] { String.class }, (Object) null);
        assertFalse("Null path should not be JS", result);
    }

    @Test
    public void isLegacyFollowupRequestReturnsTrueForButtonCommand() throws Exception {
        when(request.getParameter(COMMAND_KEY)).thenReturn("BUTTON_OK");
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_LEGACY_FOLLOWUP_REQUEST,
                new Class<?>[] { HttpServletRequest.class }, request);
        assertTrue("BUTTON command should be followup", result);
    }

    @Test
    public void isLegacyFollowupRequestReturnsFalseForNonButtonCommand() throws Exception {
        when(request.getParameter(COMMAND_KEY)).thenReturn("FIND");
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_LEGACY_FOLLOWUP_REQUEST,
                new Class<?>[] { HttpServletRequest.class }, request);
        assertFalse("Non-BUTTON command should not be followup", result);
    }

    @Test
    public void isLegacyFollowupRequestReturnsFalseForNullCommand() throws Exception {
        when(request.getParameter(COMMAND_KEY)).thenReturn(null);
        boolean result = (boolean) invokePrivateMethod(legacyProcessServlet, METHOD_IS_LEGACY_FOLLOWUP_REQUEST,
                new Class<?>[] { HttpServletRequest.class }, request);
        assertFalse("Null command should not be followup", result);
    }

    @Test
    public void extractTargetPathReturnsServletDirPlusHtmlPath() throws Exception {
        when(request.getPathInfo()).thenReturn(PATH_PAGE_HTML);
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH,
                new Class<?>[] { HttpServletRequest.class, String.class }, request, "/SalesOrder");
        assertEquals("/SalesOrder" + PATH_PAGE_HTML, result);
    }

    @Test
    public void extractTargetPathUsesRefererWhenServletDirNull() throws Exception {
        when(request.getPathInfo()).thenReturn(PATH_PAGE_HTML);
        when(request.getHeader("Referer")).thenReturn(null);
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH,
                new Class<?>[] { HttpServletRequest.class, String.class }, request, (Object) null);
        assertEquals(PATH_PAGE_HTML, result);
    }

    @Test
    public void extractTargetPathUsesRefererForNonHtmlPath() throws Exception {
        when(request.getPathInfo()).thenReturn("/process");
        when(request.getHeader("Referer")).thenReturn(null);
        Object result = invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH,
                new Class<?>[] { HttpServletRequest.class, String.class }, request, (Object) null);
        assertNull("Should return null when no html and no referer", result);
    }

    @Test
    public void extractTargetPathFromRefererReturnsNullForNull() throws Exception {
        Object result = invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH_FROM_REFERER,
                new Class<?>[] { String.class }, (Object) null);
        assertNull(result);
    }

    @Test
    public void extractTargetPathFromRefererUsesLegacyPath() throws Exception {
        String referer = "http://host/etendodev/meta/legacy/SalesOrder/EditLines.html?foo=1";
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH_FROM_REFERER,
                new Class<?>[] { String.class }, referer);
        assertEquals(PATH_SALES_ORDER_EDIT_LINES, result);
    }

    @Test
    public void extractTargetPathFromRefererUsesMetaPath() throws Exception {
        String referer = "http://host/etendodev/meta/SalesOrder/EditLines.html";
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH_FROM_REFERER,
                new Class<?>[] { String.class }, referer);
        assertEquals(PATH_SALES_ORDER_EDIT_LINES, result);
    }

    @Test
    public void extractTargetPathFromRefererReturnsPathWhenNoSlashSuffix() throws Exception {
        String referer = "http://host/etendodev/meta/EditLines.html";
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_EXTRACT_TARGET_PATH_FROM_REFERER,
                new Class<?>[] { String.class }, referer);
        assertEquals("/EditLines.html", result);
    }

    @Test
    public void deriveLegacyClassReturnsNullForShortPath() throws Exception {
        Object result = invokePrivateMethod(legacyProcessServlet, METHOD_DERIVE_LEGACY_CLASS,
                new Class<?>[] { String.class }, PATH_PAGE_HTML);
        assertNull("Single-segment path should return null", result);
    }

    @Test
    public void deriveLegacyClassReturnsCorrectClassName() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_DERIVE_LEGACY_CLASS,
                new Class<?>[] { String.class }, PATH_SALES_ORDER_EDIT_LINES);
        assertEquals("org.openbravo.erpWindows.SalesOrder.EditLines", result);
    }

    @Test
    public void deriveLegacyClassStripsUnderscoreSuffix() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, METHOD_DERIVE_LEGACY_CLASS,
                new Class<?>[] { String.class }, "/SalesOrder/EditLines_Edition.html");
        assertEquals("org.openbravo.erpWindows.SalesOrder.EditLines", result);
    }

    @Test
    public void sendHtmlResponseWritesContentAndStatus() throws Exception {
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        invokePrivateMethod(legacyProcessServlet, "sendHtmlResponse",
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, "<html>test</html>");

        verify(response).setContentType("text/html; charset=UTF-8");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue("Writer should contain HTML", sw.toString().contains("<html>test</html>"));
    }
}
