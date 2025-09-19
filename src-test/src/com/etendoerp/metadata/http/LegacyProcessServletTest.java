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

import static com.etendoerp.metadata.MetadataTestConstants.API_DATA_PATH;
import static com.etendoerp.metadata.MetadataTestConstants.JWT_TOKEN_HASH;
import static com.etendoerp.metadata.MetadataTestConstants.SALES_INVOICE_HEADER_EDITION_HTML;
import static com.etendoerp.metadata.MetadataTestConstants.TOKEN;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the LegacyProcessServlet class.
 * <p>
 * This class tests the functionality of the LegacyProcessServlet, ensuring that it
 * correctly handles legacy HTML requests, manages JWT tokens, and processes
 * request contexts. It uses Mockito for mocking dependencies and JUnit for assertions.
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class LegacyProcessServletTest extends OBBaseTest {

    // Test constants
    private static final String TEST_KEY_VALUE = "key-123";

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

    /**
     * Sets up the test environment before each test case.
     * <p>
     * Initializes the {@link LegacyProcessServlet} instance, mocks the response writer,
     * and prepares default behavior for mocked {@link HttpServletRequest}, {@link HttpServletResponse},
     * and {@link HttpSession}.
     * </p>
     *
     * @throws Exception if an error occurs during setup
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
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);
    }

    /**
     * Tests that the servlet correctly handles legacy HTML requests by including
     * the appropriate dispatcher.
     *
     * @throws Exception if the service method fails
     */
    @Test
    public void serviceShouldHandleLegacyHtmlRequest() throws Exception {
        when(request.getPathInfo()).thenReturn(SALES_INVOICE_HEADER_EDITION_HTML);
        when(request.getParameter(TOKEN)).thenReturn(null);

        try (MockedStatic<RequestContext> contextMock = mockStatic(RequestContext.class);
             MockedStatic<OBContext> obContextMock = mockStatic(OBContext.class)) {
            contextMock.when(RequestContext::get).thenReturn(requestContext);
            legacyProcessServlet.service(request, response);
            verify(requestDispatcher).include(any(), any());
        }
    }

    /**
     * Tests that {@code isLegacyRequest} returns {@code true} for HTML files
     * (case-insensitive) and {@code false} for non-legacy paths.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void isLegacyRequestShouldReturnTrueForHtmlFiles() throws Exception {
        assertTrue(invokeIsLegacyRequest("/test/page.html"));
        assertTrue(invokeIsLegacyRequest("/test/page.HTML"));
        assertTrue(invokeIsLegacyRequest(SALES_INVOICE_HEADER_EDITION_HTML));

        assertFalse(invokeIsLegacyRequest(API_DATA_PATH));
        assertFalse(invokeIsLegacyRequest("/test/page.jsp"));
        assertFalse(invokeIsLegacyRequest("/test/page"));
    }

    /**
     * Tests that {@code handleTokenConsistency} stores a JWT token in the session
     * when it is provided in the request parameters.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void handleTokenConsistencyShouldStoreTokenWhenProvided() throws Exception {
        String token = "test-jwt-token";
        when(request.getParameter(TOKEN)).thenReturn(token);

        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);

        invokeHandleTokenConsistency(request, wrapper);

        verify(session).setAttribute(JWT_TOKEN_HASH, token);
    }

    /**
     * Tests that {@code handleTokenConsistency} decodes and validates a JWT token
     * from the session when no request token is present.
     *
     * @throws Exception if reflection invocation fails
     */
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

    /**
     * Tests that {@code handleTokenConsistency} throws an {@link OBException}
     * when token decoding fails.
     */
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

    /**
     * Tests that {@code handleRecordIdentifier} stores the record identifier
     * in the session when all required parameters are provided.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void handleRecordIdentifierShouldStoreWhenAllParametersPresent() throws Exception {
        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
        when(wrapper.getParameter("inpKey")).thenReturn(TEST_KEY_VALUE);
        when(wrapper.getParameter("inpwindowId")).thenReturn("window-456");
        when(wrapper.getParameter("inpkeyColumnId")).thenReturn("column-789");
        when(wrapper.getSession()).thenReturn(session);

        invokeHandleRecordIdentifier(wrapper);

        verify(session).setAttribute("window-456|COLUMN-789", TEST_KEY_VALUE);
    }

    /**
     * Tests that {@code handleRecordIdentifier} does not store anything
     * when required parameters are missing.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void handleRecordIdentifierShouldNotStoreWhenParametersMissing() throws Exception {
        HttpServletRequestWrapper wrapper = mock(HttpServletRequestWrapper.class);
        when(wrapper.getParameter("inpKey")).thenReturn(TEST_KEY_VALUE);
        when(wrapper.getParameter("inpwindowId")).thenReturn(null);
        when(wrapper.getParameter("inpkeyColumnId")).thenReturn("column-789");

        invokeHandleRecordIdentifier(wrapper);

        verify(session, never()).setAttribute(anyString(), anyString());
    }

    /**
     * Tests that {@code handleRequestContext} correctly sets up the request,
     * response, SecureApp variables, and OBContext.
     *
     * @throws Exception if reflection invocation fails
     */
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

    /**
     * Tests that {@code maybeValidateLegacyClass} throws an exception when
     * the corresponding legacy WAD servlet class cannot be found.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void maybeValidateLegacyClassShouldValidateExistingClass() throws Exception {
        try {
            invokeMaybeValidateLegacyClass(SALES_INVOICE_HEADER_EDITION_HTML);
        } catch (OBException e) {
            assertTrue(e.getMessage().contains("Legacy WAD servlet not found"));
            assertTrue(e.getMessage().contains("org.openbravo.erpWindows.SalesInvoice.Header"));
        }
    }

    /**
     * Tests that {@code maybeValidateLegacyClass} ignores non-legacy paths
     * without throwing exceptions.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void maybeValidateLegacyClassShouldIgnoreNonLegacyPaths() throws Exception {
        invokeMaybeValidateLegacyClass(API_DATA_PATH);
    }

    /**
     * Tests that {@code deriveLegacyClass} generates the expected fully-qualified
     * class name for given legacy paths, and returns {@code null} for invalid paths.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void deriveLegacyClassShouldCreateCorrectClassName() throws Exception {
        String result1 = invokeDerivateLegacyClass(SALES_INVOICE_HEADER_EDITION_HTML);
        assertEquals("org.openbravo.erpWindows.SalesInvoice.Header", result1);
        String result2 = invokeDerivateLegacyClass("/ProductMgmt/Product.html");
        assertEquals("org.openbravo.erpWindows.ProductMgmt.Product", result2);
        String result3 = invokeDerivateLegacyClass("/invalid");
        assertNull(result3);
    }

    /**
     * Tests that {@code injectCodeAfterFunctionCall} correctly injects
     * JavaScript code after a given function call when using regex matching.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void injectCodeAfterFunctionCallShouldInjectWithRegex() throws Exception {
        String original = "submitThisPage(param); doSomething();";
        String functionCall = "submitThisPage\\(([^)]+)\\);";
        String newCall = "sendMessage('processOrder');";
        String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, true);
        assertTrue(result.contains("submitThisPage(param);sendMessage('processOrder');"));
    }

    /**
     * Tests that {@code injectCodeAfterFunctionCall} correctly injects
     * JavaScript code after a given function call when using plain string matching.
     *
     * @throws Exception if reflection invocation fails
     */
    @Test
    public void injectCodeAfterFunctionCallShouldInjectWithoutRegex() throws Exception {
        String original = "closeThisPage(); doSomething();";
        String functionCall = "closeThisPage();";
        String newCall = "sendMessage('closeModal');";
        String result = invokeInjectCodeAfterFunctionCall(original, functionCall, newCall, false);
        assertTrue(result.contains("closeThisPage();sendMessage('closeModal');"));
    }

    // --- Helper Methods ---

    /**
     * Custom exception for reflection-related errors in tests.
     */
    private static class ReflectionTestException extends Exception {
        public ReflectionTestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Invokes the private {@code isLegacyRequest} method via reflection.
     *
     * @param path the request path to check
     * @return {@code true} if the path is considered legacy, {@code false} otherwise
     * @throws ReflectionTestException if reflection invocation fails
     */
    private boolean invokeIsLegacyRequest(String path) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("isLegacyRequest", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(legacyProcessServlet, path);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke isLegacyRequest method", e);
        }
    }

    /**
     * Invokes the private {@code handleTokenConsistency} method via reflection.
     *
     * @param req     the mocked HTTP request
     * @param wrapper the request wrapper
     * @throws ReflectionTestException if reflection invocation fails
     */
    private void invokeHandleTokenConsistency(HttpServletRequest req, HttpServletRequestWrapper wrapper) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleTokenConsistency",
                    HttpServletRequest.class, HttpServletRequestWrapper.class);
            method.setAccessible(true);
            method.invoke(legacyProcessServlet, req, wrapper);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke handleTokenConsistency method", e);
        }
    }

    /**
     * Invokes the private {@code handleRecordIdentifier} method via reflection.
     *
     * @param wrapper the request wrapper
     * @throws ReflectionTestException if reflection invocation fails
     */
    private void invokeHandleRecordIdentifier(HttpServletRequestWrapper wrapper) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleRecordIdentifier",
                    HttpServletRequestWrapper.class);
            method.setAccessible(true);
            method.invoke(null, wrapper);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke handleRecordIdentifier method", e);
        }
    }

    /**
     * Invokes the private {@code handleRequestContext} method via reflection.
     *
     * @param res     the mocked HTTP response
     * @param wrapper the request wrapper
     * @throws ReflectionTestException if reflection invocation fails
     */
    private void invokeHandleRequestContext(HttpServletResponse res, HttpServletRequestWrapper wrapper) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("handleRequestContext",
                    HttpServletResponse.class, HttpServletRequestWrapper.class);
            method.setAccessible(true);
            method.invoke(null, res, wrapper);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke handleRequestContext method", e);
        }
    }

    /**
     * Invokes the private {@code maybeValidateLegacyClass} method via reflection.
     *
     * @param pathInfo the request path to validate
     * @throws ReflectionTestException if reflection invocation fails
     */
    private void invokeMaybeValidateLegacyClass(String pathInfo) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("maybeValidateLegacyClass",
                    String.class);
            method.setAccessible(true);
            method.invoke(legacyProcessServlet, pathInfo);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke maybeValidateLegacyClass method", e);
        }
    }

    /**
     * Invokes the private {@code deriveLegacyClass} method via reflection.
     *
     * @param pathInfo the request path
     * @return the derived legacy class name, or {@code null} if invalid
     * @throws ReflectionTestException if reflection invocation fails
     */
    private String invokeDerivateLegacyClass(String pathInfo) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("deriveLegacyClass", String.class);
            method.setAccessible(true);
            return (String) method.invoke(legacyProcessServlet, pathInfo);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke deriveLegacyClass method", e);
        }
    }

    /**
     * Invokes the private {@code injectCodeAfterFunctionCall} method via reflection.
     *
     * @param original     the original JavaScript source
     * @param functionCall the function call pattern
     * @param newCall      the code to inject
     * @param isRegex      whether the function call pattern is a regex
     * @return the modified JavaScript source with injected code
     * @throws ReflectionTestException if reflection invocation fails
     */
    private String invokeInjectCodeAfterFunctionCall(String original, String functionCall, String newCall,
                                                     Boolean isRegex) throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("injectCodeAfterFunctionCall",
                    String.class, String.class, String.class, boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(legacyProcessServlet, original, functionCall, newCall, isRegex);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke injectCodeAfterFunctionCall method", e);
        }
    }

    /**
     * Invokes the private {@code generateReceiveAndPostMessageScript} method via reflection.
     *
     * @return the generated JavaScript script
     * @throws ReflectionTestException if reflection invocation fails
     */
    private String invokeGenerateReceiveAndPostMessageScript() throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("generateReceiveAndPostMessageScript");
            return (String) method.invoke(legacyProcessServlet);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke generateReceiveAndPostMessageScript method", e);
        }
    }

    /**
     * Invokes the private {@code generatePostMessageScript} method via reflection.
     *
     * @return the generated JavaScript script
     * @throws ReflectionTestException if reflection invocation fails
     */
    private String invokeGeneratePostMessageScript() throws ReflectionTestException {
        try {
            java.lang.reflect.Method method = LegacyProcessServlet.class.getDeclaredMethod("generatePostMessageScript");
            return (String) method.invoke(legacyProcessServlet);
        } catch (Exception e) {
            throw new ReflectionTestException("Failed to invoke generatePostMessageScript method", e);
        }
    }
}