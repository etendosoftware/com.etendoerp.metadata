package com.etendoerp.metadata.http;

import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.weld.test.WeldBaseTest;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional tests for MetadataFilter to increase branch and line coverage.
 */
public class MetadataFilterAdditionalTest extends WeldBaseTest {

    private MetadataFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    private static final String ACCEPT_HEADER = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_HTML = "text/html";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        filter = new MetadataFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        // Initialize the filter with default path
        FilterConfig config = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(servletContext);
        when(config.getInitParameter("forwardPath")).thenReturn(null);
        filter.init(config);
    }

    /**
     * Tests getRootCause with a direct exception (no cause chain).
     */
    @Test
    public void testGetRootCauseDirectException() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("getRootCause", Throwable.class);
        method.setAccessible(true);

        RuntimeException directException = new RuntimeException("direct");
        Throwable result = (Throwable) method.invoke(filter, directException);

        assertEquals(directException, result);
    }

    /**
     * Tests getRootCause with a nested exception chain.
     */
    @Test
    public void testGetRootCauseNestedChain() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("getRootCause", Throwable.class);
        method.setAccessible(true);

        RuntimeException root = new RuntimeException("root");
        RuntimeException middle = new RuntimeException("middle", root);
        RuntimeException top = new RuntimeException("top", middle);

        Throwable result = (Throwable) method.invoke(filter, top);

        assertEquals(root, result);
    }

    /**
     * Tests getRootCause with self-referencing cause.
     */
    @Test
    public void testGetRootCauseSelfReference() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("getRootCause", Throwable.class);
        method.setAccessible(true);

        RuntimeException selfRef = new RuntimeException("self") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        Throwable result = (Throwable) method.invoke(filter, selfRef);
        assertEquals(selfRef, result);
    }

    /**
     * Tests escape with null input.
     */
    @Test
    public void testEscapeNull() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("escape", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, (String) null);
        assertEquals("", result);
    }

    /**
     * Tests escape with ampersand.
     */
    @Test
    public void testEscapeAmpersand() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("escape", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "a&b");
        assertEquals("a&amp;b", result);
    }

    /**
     * Tests escape with angle brackets.
     */
    @Test
    public void testEscapeAngleBrackets() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("escape", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "<div>");
        assertEquals("&lt;div&gt;", result);
    }

    /**
     * Tests deriveLegacyClass with a valid URI.
     */
    @Test
    public void testDeriveLegacyClassValid() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/forward/SalesOrder/Header_Edition.html");
        assertNotNull(result);
        assertTrue(result.contains("org.openbravo.erpWindows"));
        assertTrue(result.contains("SalesOrder"));
        assertTrue(result.contains("Header"));
    }

    /**
     * Tests deriveLegacyClass with URI that does not contain forward path.
     */
    @Test
    public void testDeriveLegacyClassNoForwardPath() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/other/path");
        assertNull(result);
    }

    /**
     * Tests deriveLegacyClass with URI that has too few path parts.
     */
    @Test
    public void testDeriveLegacyClassTooFewParts() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/forward/onlyOneLevel");
        assertNull(result);
    }

    /**
     * Tests deriveLegacyClass with page name without underscore.
     */
    @Test
    public void testDeriveLegacyClassNoUnderscore() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("deriveLegacyClass", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/forward/Window/Page.html");
        assertNotNull(result);
        assertEquals("org.openbravo.erpWindows.Window.Page", result);
    }

    /**
     * Tests buildHtmlError with null message.
     */
    @Test
    public void testBuildHtmlErrorNullMessage() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlError",
            String.class, int.class, String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET", "/test", null);
        assertNotNull(result);
        assertTrue(result.contains("Unexpected error"));
    }

    /**
     * Tests buildHtmlErrorDetailed with ClassNotFoundException for legacy hint.
     */
    @Test
    public void testBuildHtmlErrorDetailedWithCNF() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlErrorDetailed",
            String.class, int.class, String.class, String.class, String.class, String.class, HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET",
            "/etendo/meta/forward/SalesOrder/Header.html",
            "java.lang.ClassNotFoundException",
            "org.openbravo.erpWindows.SalesOrder.Header",
            request);

        assertNotNull(result);
        assertTrue(result.contains("Hint"));
        assertTrue(result.contains("Legacy WAD servlet not found"));
    }

    /**
     * Tests buildHtmlErrorDetailed without CNF (no hint).
     */
    @Test
    public void testBuildHtmlErrorDetailedWithoutCNF() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlErrorDetailed",
            String.class, int.class, String.class, String.class, String.class, String.class, HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET",
            "/etendo/meta/api/test",
            "java.lang.RuntimeException",
            "some error",
            request);

        assertNotNull(result);
        assertFalse(result.contains("Hint"));
    }

    /**
     * Tests buildHtmlErrorDetailed with non-.html URI (no hint even if CNF).
     */
    @Test
    public void testBuildHtmlErrorDetailedNonHtmlUri() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlErrorDetailed",
            String.class, int.class, String.class, String.class, String.class, String.class, HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET",
            "/etendo/meta/api/test",
            "java.lang.ClassNotFoundException",
            "org.openbravo.erpWindows.SomeClass",
            request);

        assertNotNull(result);
        // Not an .html URI, so no hint is added
        assertFalse(result.contains("Legacy WAD servlet not found"));
    }

    /**
     * Tests buildLegacyHint with a valid URI.
     */
    @Test
    public void testBuildLegacyHint() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildLegacyHint", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/forward/SalesOrder/Header.html");
        assertNotNull(result);
        assertTrue(result.contains("Expected class"));
        assertTrue(result.contains("compile src-wad"));
    }

    /**
     * Tests buildLegacyHint with a URI that does not have a forward path.
     */
    @Test
    public void testBuildLegacyHintNoForwardPath() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildLegacyHint", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "/etendo/meta/other/path");
        assertNotNull(result);
        assertTrue(result.contains("compile src-wad"));
        assertFalse(result.contains("Expected class"));
    }

    /**
     * Tests handleException when response is null.
     */
    @Test
    public void testHandleExceptionNullResponse() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("handleException",
            javax.servlet.ServletRequest.class, javax.servlet.ServletResponse.class, Throwable.class);
        method.setAccessible(true);

        // Non-HTTP response -> will be cast to null
        javax.servlet.ServletResponse nonHttpResponse = mock(javax.servlet.ServletResponse.class);

        // Should not throw even with non-HTTP response
        method.invoke(filter, request, nonHttpResponse, new RuntimeException("test"));
    }

    /**
     * Tests handleException when response is committed.
     */
    @Test
    public void testHandleExceptionCommittedResponse() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("handleException",
            javax.servlet.ServletRequest.class, javax.servlet.ServletResponse.class, Throwable.class);
        method.setAccessible(true);

        when(response.isCommitted()).thenReturn(true);
        when(request.getRequestURI()).thenReturn("/test");
        when(request.getMethod()).thenReturn("GET");

        method.invoke(filter, request, response, new RuntimeException("test"));

        verify(response, never()).setStatus(anyInt());
    }

    /**
     * Tests handleException returns HTML when URI ends with .html.
     */
    @Test
    public void testHandleExceptionHtmlUriResponse() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("handleException",
            javax.servlet.ServletRequest.class, javax.servlet.ServletResponse.class, Throwable.class);
        method.setAccessible(true);

        StringWriter stringWriter = new StringWriter();
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        when(request.getRequestURI()).thenReturn("/test.html");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

        method.invoke(filter, request, response, new RuntimeException("html error"));

        verify(response).setContentType("text/html; charset=UTF-8");
        assertTrue(stringWriter.toString().contains("Etendo Meta Error"));
    }

    /**
     * Tests handleException with isc_dataFormat=json overrides HTML.
     */
    @Test
    public void testHandleExceptionIscJsonOverridesHtml() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("handleException",
            javax.servlet.ServletRequest.class, javax.servlet.ServletResponse.class, Throwable.class);
        method.setAccessible(true);

        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRequestURI()).thenReturn("/test.html");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);
        when(request.getParameter("isc_dataFormat")).thenReturn("json");

        method.invoke(filter, request, response, new RuntimeException("json error"));

        // Should NOT set HTML content type, should use JSON
        verify(response, never()).setContentType("text/html; charset=UTF-8");
    }

    /**
     * Tests relayCapturedOutput with null content type.
     */
    @Test
    public void testRelayCapturedOutputNullContentType() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("relayCapturedOutput",
            HttpServletResponse.class, byte[].class, int.class, String.class);
        method.setAccessible(true);

        ServletOutputStream mockOutput = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(mockOutput);

        byte[] body = "test content".getBytes();
        method.invoke(filter, response, body, 200, null);

        verify(response, never()).setContentType(anyString());
        verify(response).setStatus(200);
    }

    /**
     * Tests relayCapturedOutput with status 0 (should not set status).
     */
    @Test
    public void testRelayCapturedOutputZeroStatus() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("relayCapturedOutput",
            HttpServletResponse.class, byte[].class, int.class, String.class);
        method.setAccessible(true);

        byte[] body = "test".getBytes();
        ServletOutputStream mockOutput = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(mockOutput);

        method.invoke(filter, response, body, 0, "text/plain");

        verify(response, never()).setStatus(anyInt());
    }

    /**
     * Tests relayCapturedOutput with empty body.
     */
    @Test
    public void testRelayCapturedOutputEmptyBody() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("relayCapturedOutput",
            HttpServletResponse.class, byte[].class, int.class, String.class);
        method.setAccessible(true);

        byte[] body = new byte[0];
        method.invoke(filter, response, body, 200, "text/plain");

        // Should NOT try to write to output stream since body is empty
        verify(response, never()).getOutputStream();
    }

    /**
     * Tests shouldCaptureHtml with null response.
     */
    @Test
    public void testShouldCaptureHtmlNullResponse() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("shouldCaptureHtml",
            HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(filter, request, null));
    }

    /**
     * Tests doFilter with forward path match.
     */
    @Test
    public void testDoFilterForwardPathMatch() throws Exception {
        when(request.getPathInfo()).thenReturn("/forward/SalesOrder/Header");
        when(request.getRequestURI()).thenReturn("/etendo/meta/forward/SalesOrder/Header");
        when(request.getMethod()).thenReturn("GET");

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception e) {
            // Expected - ForwarderServlet may throw
        }

        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Tests handleException with Accept text/html header but non-.html URI.
     */
    @Test
    public void testHandleExceptionAcceptHtmlHeader() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("handleException",
            javax.servlet.ServletRequest.class, javax.servlet.ServletResponse.class, Throwable.class);
        method.setAccessible(true);

        StringWriter stringWriter = new StringWriter();
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
        when(request.getRequestURI()).thenReturn("/test/api");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);

        method.invoke(filter, request, response, new RuntimeException("accept html"));

        verify(response).setContentType("text/html; charset=UTF-8");
    }

    /**
     * Tests handleException with chained exception to verify root cause extraction.
     */
    @Test
    public void testHandleExceptionChainedCause() throws Exception {
        when(response.isCommitted()).thenReturn(false);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRequestURI()).thenReturn("/test/api");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(ACCEPT_HEADER)).thenReturn(APPLICATION_JSON);

        Exception root = new IllegalArgumentException("root cause");
        Exception wrapper = new RuntimeException("wrapper", root);

        FilterChain exceptionChain = (req, res) -> {
            throw new javax.servlet.ServletException(wrapper);
        };

        filter.doFilter(request, response, exceptionChain);

        verify(response).setStatus(anyInt());
    }

    /**
     * Tests buildHtmlErrorDetailed with CNF message containing org.openbravo.erpWindows.
     */
    @Test
    public void testBuildHtmlErrorDetailedCnfInMessage() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlErrorDetailed",
            String.class, int.class, String.class, String.class, String.class, String.class, HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET",
            "/etendo/meta/forward/SalesOrder/Header.html",
            "java.lang.RuntimeException",
            "org.openbravo.erpWindows.SalesOrder.Header not found",
            request);

        assertNotNull(result);
        // isCNF checks both class name AND message for org.openbravo.erpWindows
        assertTrue(result.contains("Hint"));
    }

    /**
     * Tests buildHtmlErrorDetailed with null exClass.
     */
    @Test
    public void testBuildHtmlErrorDetailedNullExClass() throws Exception {
        Method method = MetadataFilter.class.getDeclaredMethod("buildHtmlErrorDetailed",
            String.class, int.class, String.class, String.class, String.class, String.class, HttpServletRequest.class);
        method.setAccessible(true);

        String result = (String) method.invoke(filter, "cid-123", 500, "GET",
            "/etendo/meta/test.html",
            null,
            "some error",
            request);

        assertNotNull(result);
        assertFalse(result.contains("Hint"));
    }
}
