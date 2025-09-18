package com.etendoerp.metadata.http;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.etendoerp.metadata.data.RequestVariables;
import com.smf.securewebservices.utils.SecureWebServicesUtils;
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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import static com.etendoerp.metadata.MetadataTestConstants.API_DATA_PATH;
import static com.etendoerp.metadata.MetadataTestConstants.JWT_TOKEN_HASH;
import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the LegacyProcessServlet class.
 * <p>This class tests the functionality of the LegacyProcessServlet, ensuring that it
 * correctly handles legacy HTML requests, manages JWT tokens, and processes
 * request contexts. It uses Mockito for mocking dependencies and JUnit for assertions.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LegacyProcessServletTest extends OBBaseTest {

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

    private LegacyProcessServlet legacyProcessServlet;

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
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
    }


    @Test
    public void serviceShouldHandleLegacyHtmlRequest() throws Exception {
        String path = SALES_INVOICE_HEADER_EDITION_HTML;
        when(request.getPathInfo()).thenReturn(path);
        when(request.getParameter(TOKEN)).thenReturn(null);
        when(session.getAttribute("LEGACY_TOKEN")).thenReturn(null);

        try (MockedStatic<RequestContext> contextMock = mockStatic(RequestContext.class);
             MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
            contextMock.when(RequestContext::get).thenReturn(requestContext);
            legacyProcessServlet.service(request, response);
            verify(requestDispatcher).include(any(), any());
        }
    }

    @Test
    public void isLegacyRequestShouldReturnTrueForHtmlFiles() throws Exception {
        assertTrue(invokeIsLegacyRequest("/test/page.html"));
        assertTrue(invokeIsLegacyRequest("/test/page.HTML"));
        assertTrue(invokeIsLegacyRequest(SALES_INVOICE_HEADER_EDITION_HTML));

        assertFalse(invokeIsLegacyRequest(API_DATA_PATH));
        assertFalse(invokeIsLegacyRequest("/test/page.jsp"));
        assertFalse(invokeIsLegacyRequest("/test/page"));
    }

    @Test
    public void handleTokenConsistencyShouldStoreTokenWhenProvided() throws Exception {
        String token = "test-jwt-token";
        when(request.getParameter(TOKEN)).thenReturn(token);

        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);

        invokeHandleTokenConsistency(request, wrapper);

        verify(session).setAttribute(JWT_TOKEN_HASH, token);
    }

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
                assertTrue(e.getCause() instanceof OBException ||
                        (e.getCause() != null && e.getCause().getCause() instanceof OBException));
            }
        }
    }

    @Test
    public void handleRecordIdentifierShouldStoreWhenAllParametersPresent() throws Exception {
        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
        when(wrapper.getParameter("inpKey")).thenReturn("key-123");
        when(wrapper.getParameter("inpwindowId")).thenReturn("window-456");
        when(wrapper.getParameter("inpkeyColumnId")).thenReturn("column-789");
        when(wrapper.getSession()).thenReturn(session);

        invokeHandleRecordIdentifier(wrapper);

        verify(session).setAttribute("window-456|COLUMN-789", "key-123");
    }

    @Test
    public void handleRecordIdentifierShouldNotStoreWhenParametersMissing() throws Exception {
        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
        when(wrapper.getParameter("inpKey")).thenReturn("key-123");
        when(wrapper.getParameter("inpwindowId")).thenReturn(null);
        when(wrapper.getParameter("inpkeyColumnId")).thenReturn("column-789");
        when(wrapper.getSession()).thenReturn(session);

        invokeHandleRecordIdentifier(wrapper);

        verify(session, never()).setAttribute(anyString(), anyString());
    }

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

    @Test
    public void maybeValidateLegacyClassShouldValidateExistingClass() throws Exception {
        String pathInfo = SALES_INVOICE_HEADER_EDITION_HTML;
        try {
            invokeMaybeValidateLegacyClass(pathInfo);
        } catch (OBException e) {
            assertTrue(e.getMessage().contains("Legacy WAD servlet not found"));
            assertTrue(e.getMessage().contains("org.openbravo.erpWindows.SalesInvoice.Header"));
        }
    }

    @Test
    public void maybeValidateLegacyClassShouldIgnoreNonLegacyPaths() throws Exception {
        String pathInfo = API_DATA_PATH;
        invokeMaybeValidateLegacyClass(pathInfo);
    }

    @Test
    public void deriveLegacyClassShouldCreateCorrectClassName() throws Exception {
        String result1 = invokeDerivateLegacyClass(SALES_INVOICE_HEADER_EDITION_HTML);
        assertEquals("org.openbravo.erpWindows.SalesInvoice.Header", result1);
        String result2 = invokeDerivateLegacyClass("/ProductMgmt/Product.html");
        assertEquals("org.openbravo.erpWindows.ProductMgmt.Product", result2);
        String result3 = invokeDerivateLegacyClass("/invalid");
        assertNull(result3);
    }

    @Test
    public void injectCodeAfterFunctionCallShouldInjectWithRegex() throws Exception {
        String original = "submitThisPage(param); doSomething();";
        String functionCall = "submitThisPage\\(([^)]+)\\);";
        String newCall = "sendMessage('processOrder');";
        String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, true);
        assertTrue(result.contains("submitThisPage(param);sendMessage('processOrder');"));
    }

    @Test
    public void injectCodeAfterFunctionCallShouldInjectWithoutRegex() throws Exception {
        String original = "closeThisPage(); doSomething();";
        String functionCall = "closeThisPage();";
        String newCall = "sendMessage('closeModal');";
        String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, false);
        assertTrue(result.contains("closeThisPage();sendMessage('closeModal');"));
    }

    @Test
    public void generateReceiveAndPostMessageScriptShouldReturnCorrectScript() throws Exception {
        String script = invokeGenerateReceiveAndPostMessageScript();
        assertTrue(script.contains("window.addEventListener"));
        assertTrue(script.contains("message"));
        assertTrue(script.contains("fromForm"));
        assertTrue(script.contains("fromIframe"));
        assertTrue(script.contains("window.parent.postMessage"));
    }

    @Test
    public void generatePostMessageScriptShouldReturnCorrectScript() throws Exception {
        String script = invokeGeneratePostMessageScript();
        assertTrue(script.contains("sendMessage"));
        assertTrue(script.contains("fromForm"));
        assertTrue(script.contains("window.parent.postMessage"));
    }

    // --- Helper Methods ---

    private boolean invokeIsLegacyRequest(String path) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("isLegacyRequest", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(legacyProcessServlet, path);
    }

    private void invokeHandleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper wrapper) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleTokenConsistency",
                HttpServletRequest.class, HttpServletRequestWrapper.class);
        method.setAccessible(true);
        method.invoke(legacyProcessServlet, req, wrapper);
    }

    private void invokeHandleRecordIdentifier(HttpServletRequestWrapper wrapper) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleRecordIdentifier",
                HttpServletRequestWrapper.class);
        method.setAccessible(true);
        method.invoke(legacyProcessServlet, wrapper);
    }

    private void invokeHandleRequestContext(HttpServletResponse res, HttpServletRequestWrapper wrapper) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleRequestContext",
                HttpServletResponse.class, HttpServletRequestWrapper.class);
        method.setAccessible(true);
        method.invoke(legacyProcessServlet, res, wrapper);
    }

    private void invokeMaybeValidateLegacyClass(String pathInfo) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("maybeValidateLegacyClass",
                String.class);
        method.setAccessible(true);
        method.invoke(legacyProcessServlet, pathInfo);
    }

    private String invokeDerivateLegacyClass(String pathInfo) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);
        return (String) method.invoke(legacyProcessServlet, pathInfo);
    }

    private String invokeInjectCodeAfterFunctionCall(String original, String functionCall, String newCall,
                                                     Boolean isRegex) throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("injectCodeAfterFunctionCall",
                String.class, String.class, String.class, Boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(legacyProcessServlet, original, functionCall, newCall, isRegex);
    }

    private String invokeGenerateReceiveAndPostMessageScript() throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("generateReceiveAndPostMessageScript");
        method.setAccessible(true);
        return (String) method.invoke(legacyProcessServlet);
    }

    private String invokeGeneratePostMessageScript() throws Exception {
        java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("generatePostMessageScript");
        method.setAccessible(true);
        return (String) method.invoke(legacyProcessServlet);
    }
}