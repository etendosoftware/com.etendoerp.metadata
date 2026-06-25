/*
 * Copyright 2022-2025 Etendo Software S.L.
 *
 * Licensed under the Etendo Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://etendo.software/en/enterprise-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional unit tests for the LegacyProcessServlet class.
 * <p>
 * This class covers path utility methods
 * and legacy class derivation logic.
 * </p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LegacyProcessServletUtilsTest extends OBBaseTest {
    private static final String COMMAND_KEY = "Command";
    private static final String METHOD_IS_VALID_LOCATION = "isValidLocation";
    private static final String METHOD_IS_REDIRECT_REQUEST = "isRedirectRequest";
    private static final String METHOD_IS_LEGACY_FOLLOWUP_REQUEST = "isLegacyFollowupRequest";
    private static final String METHOD_EXTRACT_TARGET_PATH = "extractTargetPath";
    private static final String METHOD_EXTRACT_TARGET_PATH_FROM_REFERER = "extractTargetPathFromReferer";
    private static final String METHOD_DERIVE_LEGACY_CLASS = "deriveLegacyClass";
    private static final String PATH_PAGE_HTML = "/page.html";
    private static final String PATH_SALES_ORDER_EDIT_LINES = "/SalesOrder/EditLines.html";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpSession session;

    private LegacyProcessServlet legacyProcessServlet;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        legacyProcessServlet = new LegacyProcessServlet();
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getHeaders(anyString())).thenReturn(Collections.emptyEnumeration());
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getLocales()).thenReturn(Collections.emptyEnumeration());
        when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
        when(session.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getContextPath()).thenReturn("/etendo");
    }

    private Object invokePrivateMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Method method = obj.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(obj, args);
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
