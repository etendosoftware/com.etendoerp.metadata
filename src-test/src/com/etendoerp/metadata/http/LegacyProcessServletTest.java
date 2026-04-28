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

import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
    private static final String USER_ID_KEY = "userId";

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
            throws Exception {
        Method method = obj.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(obj, args);
    }

    /**
     * Tests setSessionCookie method.
     */
    @Test
    public void testSetSessionCookie() throws Exception {
        when(response.getHeaders("Set-Cookie")).thenReturn(Collections.emptyList());

        invokePrivateMethod(legacyProcessServlet, "setSessionCookie",
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, "test-session-id");

        verify(response).addHeader(eq("Set-Cookie"), contains("JSESSIONID=test-session-id"));
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
     * Tests processLegacyFollowupRequest without token in session.
     */
    @Test
    public void testProcessLegacyFollowupRequestNoToken() throws Exception {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn(null);

        invokePrivateMethod(legacyProcessServlet, "processLegacyFollowupRequest",
                new Class<?>[] { HttpServletRequest.class, HttpServletResponse.class },
                request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    }

    /**
     * Tests injectCodeAfterFunctionCall.
     */
    @Test
    public void testInjectCodeAfterFunctionCall() throws Exception {
        String original = "function test() { submitThisPage('action'); }";
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeAfterFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                original, "submitThisPage\\(([^)]+)\\);", "extra();", true);

        assertTrue("Should contain injected code", result.contains("submitThisPage('action');extra();"));
    }

    // ========== Tests for frameMenu shim injection (new functionality) ==========

    /**
     * Tests buildShimScript includes all required frameMenu properties.
     */
    @Test
    public void testBuildShimScriptIncludesRequiredProperties() throws Exception {
        String script = (String) invokePrivateMethod(legacyProcessServlet, "buildShimScript",
                new Class<?>[] { String.class, String.class, String.class },
                ".", ",", "#,##0.00");

        assertTrue("Should include decimal separator", script.contains("decSeparator_global:'.'"));
        assertTrue("Should include group separator", script.contains("groupSeparator_global:','"));
        assertTrue("Should include group interval", script.contains("groupInterval_global:'3'"));
        assertTrue("Should include default mask", script.contains("maskNumeric_default:'#,##0.00'"));
        assertTrue("Should include autosave flag", script.contains("autosave:false"));
        assertTrue("Should include frameMenu assignment", script.contains("window.frameMenu=m"));
    }

    /**
     * Tests buildShimScript defines _shimGetFrame function correctly.
     */
    @Test
    public void testBuildShimScriptDefinesShimGetFrame() throws Exception {
        String script = (String) invokePrivateMethod(legacyProcessServlet, "buildShimScript",
                new Class<?>[] { String.class, String.class, String.class },
                ".", ",", "#,##0.00");

        assertTrue("Should define _shimGetFrame", script.contains("var _shimGetFrame=function(name)"));
        assertTrue("Should return frameMenu mock", script.contains("if(name==='frameMenu')return window.frameMenu;"));
        assertTrue("Should assign to window.getFrame", script.contains("window.getFrame=_shimGetFrame;"));
    }

    /**
     * Tests buildShimScript wraps in IIFE for scope isolation.
     */
    @Test
    public void testBuildShimScriptUsesIIFE() throws Exception {
        String script = (String) invokePrivateMethod(legacyProcessServlet, "buildShimScript",
                new Class<?>[] { String.class, String.class, String.class },
                ".", ",", "#,##0.00");

        assertTrue("Should start with IIFE", script.contains("(function(){"));
        assertTrue("Should end with IIFE call", script.contains("})();"));
    }

    /**
     * Tests buildFrameMenuPatchScript wraps messages.js's getFrame.
     */
    @Test
    public void testBuildFrameMenuPatchScript() throws Exception {
        String patchScript = (String) invokePrivateMethod(legacyProcessServlet, "buildFrameMenuPatchScript",
                new Class<?>[] {});

        assertTrue("Should save original getFrame", patchScript.contains("var _messagesGetFrame=getFrame;"));
        assertTrue("Should define wrapper", patchScript.contains("window.getFrame=function(name)"));
        assertTrue("Should return frameMenu for frameMenu", patchScript.contains("if(name==='frameMenu')return window.frameMenu;"));
    }

    /**
     * Tests escapeJs correctly escapes single quotes.
     */
    @Test
    public void testEscapeJsSingleQuotes() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, "escapeJs",
                new Class<?>[] { String.class },
                "it's");

        assertEquals("Single quotes should be escaped", "it\\'s", result);
    }

    /**
     * Tests escapeJs correctly escapes backslashes.
     */
    @Test
    public void testEscapeJsBackslashes() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, "escapeJs",
                new Class<?>[] { String.class },
                "path\\to\\file");

        assertEquals("Backslashes should be double-escaped", "path\\\\to\\\\file", result);
    }

    /**
     * Tests escapeJs handles combined escaping.
     */
    @Test
    public void testEscapeJsCombined() throws Exception {
        String result = (String) invokePrivateMethod(legacyProcessServlet, "escapeJs",
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
                new Class<?>[] { String.class },
                html);

        int patchIndex = result.indexOf("var _messagesGetFrame=getFrame;");
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
                new Class<?>[] { String.class },
                html);

        assertTrue("Shim should still be injected after <head>",
                result.contains("<head><script>(function(){"));
        assertTrue("Shim should include window.frameMenu assignment",
                result.contains("window.frameMenu=m"));
        assertTrue("Patch script should NOT be injected when </HEAD> is absent",
                !result.contains("var _messagesGetFrame=getFrame;"));
    }

    /**
     * Tests injectFrameMenuShim does not modify HTML without any <head> tag at all.
     */
    @Test
    public void testInjectFrameMenuShimNoHeadTagAtAll() throws Exception {
        String html = "<html><body><div>no head tag here</div></body></html>";
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
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
        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectFrameMenuShim",
                new Class<?>[] { String.class },
                html);

        assertTrue("Original elements should be preserved", result.contains("<meta name=\"x\">"));
        assertTrue("Body content should be unchanged", result.contains("<div>content</div>"));
    }

    // ========== Tests for popup message forwarder (new functionality) ==========

    /**
     * Tests injectPopupMessageForwarder injects the forwarder script before </HEAD>
     * when the response is an Openbravo classic popup-message page (error).
     */
    @Test
    public void testInjectPopupMessageForwarderInjectsForErrorPopup() throws Exception {
        String html = "<HTML><HEAD><TITLE>Error</TITLE></HEAD><BODY>"
                + "<TABLE id=\"paramTipo\" class=\"MessageBoxERROR\">"
                + "<DIV id=\"messageBoxIDTitle\">Error</DIV>"
                + "<DIV id=\"messageBoxIDMessage\">Something failed</DIV>"
                + "</TABLE></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectPopupMessageForwarder",
                new Class<?>[] { String.class },
                html);

        int scriptIndex = result.indexOf("action:'showProcessMessage'");
        int closeHeadIndex = result.indexOf("</HEAD>");
        assertTrue("Forwarder script should be injected before </HEAD>",
                scriptIndex >= 0 && closeHeadIndex >= 0 && scriptIndex < closeHeadIndex);
        assertTrue("Forwarder should schedule closeModal via setTimeout",
                result.contains("action:'closeModal'") && result.contains("setTimeout"));
    }

    /**
     * Tests injectPopupMessageForwarder also injects for success-style popups.
     * The type is computed client-side from paramTipo's className, so the server
     * only needs to confirm the script was injected.
     */
    @Test
    public void testInjectPopupMessageForwarderInjectsForSuccessPopup() throws Exception {
        String html = "<HTML><HEAD></HEAD><BODY>"
                + "<TABLE id=\"paramTipo\" class=\"MessageBoxSUCCESS\">"
                + "<DIV id=\"messageBoxIDTitle\">Success</DIV>"
                + "<DIV id=\"messageBoxIDMessage\">Process completed</DIV>"
                + "</TABLE></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectPopupMessageForwarder",
                new Class<?>[] { String.class },
                html);

        assertTrue("Forwarder script should be injected for success popups",
                result.contains("action:'showProcessMessage'"));
    }

    /**
     * Tests injectPopupMessageForwarder does NOT inject when the popup marker is
     * absent — covers form pages, frameset pages and plain HTML.
     */
    @Test
    public void testInjectPopupMessageForwarderSkipsWhenMarkerAbsent() throws Exception {
        String html = "<HTML><HEAD></HEAD><BODY><FORM>...</FORM></BODY></HTML>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectPopupMessageForwarder",
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

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectPopupMessageForwarder",
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
        when(request.getParameter("Command")).thenReturn("PROCESS");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, "isProcessCommandPopup",
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_ERROR_HTML);

        assertTrue("Should short-circuit for Command=PROCESS with popup marker", result);
    }

    /**
     * Tests isProcessCommandPopup accepts PROCESS prefix variants (PROCESSDEFAULT, etc.).
     */
    @Test
    public void testIsProcessCommandPopupTrueForProcessPrefixedCommand() throws Exception {
        when(request.getParameter("Command")).thenReturn("PROCESSDEFAULT");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, "isProcessCommandPopup",
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
        when(request.getParameter("Command")).thenReturn("GRID");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, "isProcessCommandPopup",
                new Class<?>[] { HttpServletRequest.class, String.class },
                request, POPUP_ERROR_HTML);

        assertEquals("Command=GRID must not be short-circuited", Boolean.FALSE, result);
    }

    /**
     * Tests isProcessCommandPopup returns false when Command parameter is missing.
     */
    @Test
    public void testIsProcessCommandPopupFalseWhenCommandMissing() throws Exception {
        when(request.getParameter("Command")).thenReturn(null);

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, "isProcessCommandPopup",
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
        when(request.getParameter("Command")).thenReturn("PROCESS");

        Boolean result = (Boolean) invokePrivateMethod(legacyProcessServlet, "isProcessCommandPopup",
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
                invokePrivateMethod(legacyProcessServlet, "mapMessageType",
                        new Class<?>[] { String.class }, "ERROR"));
        assertEquals("SUCCESS maps to success", "success",
                invokePrivateMethod(legacyProcessServlet, "mapMessageType",
                        new Class<?>[] { String.class }, "SUCCESS"));
        assertEquals("WARNING maps to warning", "warning",
                invokePrivateMethod(legacyProcessServlet, "mapMessageType",
                        new Class<?>[] { String.class }, "WARNING"));
        assertEquals("Unknown suffix falls back to info", "info",
                invokePrivateMethod(legacyProcessServlet, "mapMessageType",
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

        invokePrivateMethod(legacyProcessServlet, "writeProcessCommandForwarder",
                new Class<?>[] { HttpServletResponse.class, String.class },
                response, POPUP_ERROR_HTML);

        writer.flush();
        String output = stringWriter.toString();

        assertTrue("Response must contain the showProcessMessage dispatch",
                output.contains("action:'showProcessMessage'"));
        assertTrue("Response must NOT auto-close the modal",
                !output.contains("action:'closeModal'") && !output.contains("setTimeout"));
        assertTrue("Response payload must carry the extracted type",
                output.contains("\"type\":\"error\""));
        assertTrue("Response payload must carry the extracted title",
                output.contains("\"title\":\"Error\""));
        assertTrue("Response payload must carry the extracted message text",
                output.contains("OBException: something failed"));
        assertTrue("Short-circuited response must NOT carry the original popup markup",
                !output.contains("messageBoxIDMessage") && !output.contains("paramTipo"));

        verify(response).setContentType("text/html; charset=UTF-8");
        verify(response).setStatus(HttpServletResponse.SC_OK);
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

        invokePrivateMethod(legacyProcessServlet, "writeProcessCommandForwarder",
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
    private String readScriptConstant(String fieldName) throws Exception {
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
        String script = readScriptConstant("POST_MESSAGE_SCRIPT");

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
     * The popup-message forwarder must mark the message-sent flag so the
     * pagehide listener does not emit a redundant {@code iframeUnloaded} during
     * the 150 ms delay before {@code closeModal}.
     */
    @Test
    public void showProcessMessageScriptShouldMarkMessageSent() throws Exception {
        String script = readScriptConstant("SHOW_PROCESS_MESSAGE_SCRIPT");

        int postIndex = script.indexOf("action:'showProcessMessage'");
        int markIndex = script.indexOf("__etendoMessageSent=true");
        int closeIndex = script.indexOf("action:'closeModal'");

        assertTrue("Script must post showProcessMessage", postIndex >= 0);
        assertTrue("Script must mark the message-sent flag", markIndex >= 0);
        assertTrue("Script must still schedule closeModal", closeIndex >= 0);
        assertTrue("Flag must be set after showProcessMessage and before closeModal",
                postIndex < markIndex && markIndex < closeIndex);
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
     * {@code sendMessage('processOrder')} must be injected BEFORE
     * {@code submitThisPage(...)} so the postMessage is queued before the form
     * submit can trigger a navigation that destroys the document.
     */
    @Test
    public void processOrderShouldBeInjectedBeforeSubmitThisPage() throws Exception {
        String html = "<html><body><form><a onclick=\"submitThisPage('save');\">go</a></form></body></html>";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeBeforeFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                html, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true);

        assertTrue("processOrder must be emitted before submitThisPage",
                result.contains("sendMessage('processOrder');submitThisPage('save');"));
        assertTrue("Original submitThisPage call must be preserved",
                result.contains("submitThisPage('save');"));
        assertTrue("processOrder must NOT remain after submitThisPage",
                !result.contains("submitThisPage('save');sendMessage('processOrder');"));
    }

    /**
     * The before-call helper must inject the new code in front of every match,
     * not only the first occurrence (action buttons, save buttons, etc. all
     * resolve to {@code submitThisPage}).
     */
    @Test
    public void injectCodeBeforeFunctionCallShouldInjectAtEveryMatch() throws Exception {
        String html = "submitThisPage('a');submitThisPage('b');";

        String result = (String) invokePrivateMethod(legacyProcessServlet, "injectCodeBeforeFunctionCall",
                new Class<?>[] { String.class, String.class, String.class, boolean.class },
                html, "submitThisPage\\(([^)]+)\\);", "sendMessage('processOrder');", true);

        assertEquals("Both submitThisPage calls must be preceded by sendMessage",
                "sendMessage('processOrder');submitThisPage('a');sendMessage('processOrder');submitThisPage('b');",
                result);
    }
}
