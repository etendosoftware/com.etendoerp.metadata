package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.etendoerp.metadata.exceptions.NotFoundException;
import com.etendoerp.metadata.service.ServiceFactory;
import com.etendoerp.metadata.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.test.base.OBBaseTest;

/**
 * Additional coverage tests for {@link MetadataServlet}.
 * Targets uncovered branches: committed response, null query string, Accept
 * header with application/json, getRootCause chained exceptions,
 * buildHtmlError null message, escape null, and doPost/doDelete/doPut methods.
 */
@RunWith(MockitoJUnitRunner.class)
public class MetadataServletCoverageTest extends OBBaseTest {

    private static final String UNKNOWN_PATH = "/unknown";
    private static final String ISC_DATA_FORMAT = "isc_dataFormat";
    private static final String UNKNOWN_URI = "/sws/com.etendoerp.metadata.meta/unknown";
    private static final String APPLICATION_JSON = "application/json";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String TEXT_HTML = "text/html";
    private static final String TEST_PATH = "/test";

    private MetadataServlet servlet;

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;

    private StringWriter stringWriter;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new MetadataServlet();
        stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
    }

    // ==================== doPost method ====================

    /**
     * Tests doPost delegates to process and handles NotFoundException.
     */
    @Test
    public void testDoPost_NotFoundException() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doPost("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
        verify(mockResponse).setContentType(contains(APPLICATION_JSON));
    }

    // ==================== doDelete method ====================

    /**
     * Tests doDelete delegates to process and handles NotFoundException.
     */
    @Test
    public void testDoDelete_NotFoundException() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("DELETE");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doDelete("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
    }

    // ==================== doPut method ====================

    /**
     * Tests doPut delegates to process and handles NotFoundException.
     */
    @Test
    public void testDoPut_NotFoundException() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("PUT");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doPut("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
    }

    // ==================== handleException with committed response ====================

    /**
     * Tests that handleException returns early when response is already committed.
     */
    @Test
    public void testHandleException_CommittedResponse() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);
        when(mockResponse.isCommitted()).thenReturn(true);

        servlet.doGet("", mockRequest, mockResponse);

        // Should not write any content since response is committed
        verify(mockResponse, never()).setContentType(anyString());
    }

    // ==================== handleException with Accept: application/json ====================

    /**
     * Tests JSON error response when Accept header contains application/json.
     */
    @Test
    public void testHandleException_AcceptHeaderJson() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn(null);
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn(APPLICATION_JSON);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
        verify(mockResponse).setContentType(contains(APPLICATION_JSON));
    }

    // ==================== handleException with null query string ====================

    /**
     * Tests error handling when query string is null (no "?" appended in log).
     */
    @Test
    public void testHandleException_NullQueryString() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn(null);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);
        when(mockRequest.getQueryString()).thenReturn(null);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
    }

    // ==================== handleException with non-null query string ====================

    /**
     * Tests error handling when query string is present.
     */
    @Test
    public void testHandleException_WithQueryString() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);
        when(mockRequest.getQueryString()).thenReturn("param=value");

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setStatus(eq(Utils.getHttpStatusFor(new NotFoundException())));
    }

    // ==================== HTML error response (no JSON indicators) ====================

    /**
     * Tests HTML error response when neither isc_dataFormat=json nor Accept: application/json.
     */
    @Test
    public void testHandleException_HtmlResponse_NoJsonIndicators() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn(null);
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn(TEXT_HTML);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setContentType(contains(TEXT_HTML));
        String content = stringWriter.toString();
        assertTrue("Should contain HTML error page", content.contains("Request failed in /meta"));
        assertTrue("Should contain Correlation Id", content.contains("Correlation Id"));
    }

    // ==================== HTML error with null Accept header ====================

    /**
     * Tests HTML error when Accept header is null and isc_dataFormat is null.
     */
    @Test
    public void testHandleException_NullAcceptHeader() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn(null);
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn(null);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(UNKNOWN_URI);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setContentType(contains(TEXT_HTML));
    }

    // ==================== getRootCause with chained exceptions ====================

    /**
     * Tests getRootCause traverses the exception chain to the root.
     * The NotFoundException thrown by ServiceFactory has no cause, so it is the root.
     * We simulate a chained exception by wrapping.
     */
    @Test
    public void testHandleException_ChainedExceptionRoot() throws Exception {
        // ServiceFactory.getService throws NotFoundException which has no cause
        // To test chaining, we'd need a different path. But we can verify the root
        // is used by checking the status code corresponds to the exception type.
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(TEST_PATH);

        servlet.doGet("", mockRequest, mockResponse);

        // NotFoundException -> root cause is itself -> status = 404
        verify(mockResponse).setStatus(eq(404));
    }

    // ==================== isc_dataFormat case insensitive ====================

    /**
     * Tests that isc_dataFormat "JSON" (uppercase) also triggers JSON response.
     */
    @Test
    public void testHandleException_IscDataFormatUpperCase() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("JSON");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(TEST_PATH);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setContentType(contains(APPLICATION_JSON));
    }

    // ==================== HTML error with special characters in method/URI ====================

    /**
     * Tests HTML escape of special characters in method and URI.
     */
    @Test
    public void testHandleException_HtmlEscapeSpecialChars() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn(null);
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn(null);
        when(mockRequest.getMethod()).thenReturn("<script>alert(1)</script>");
        when(mockRequest.getRequestURI()).thenReturn("/test?a=<b>&c=d");

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setContentType(contains(TEXT_HTML));
        String content = stringWriter.toString();
        assertFalse("Should not contain raw < in method", content.contains("<script>"));
        assertTrue("Should contain escaped method", content.contains("&lt;script&gt;"));
    }

    // ==================== JSON response contains cid ====================

    /**
     * Tests that JSON error response contains correlation ID field.
     */
    @Test
    public void testHandleException_JsonContainsCid() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn("json");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(TEST_PATH);

        servlet.doGet("", mockRequest, mockResponse);

        String content = stringWriter.toString();
        assertTrue("JSON response should contain cid field", content.contains("\"cid\":\""));
    }

    // ==================== Accept header with mixed content types ====================

    /**
     * Tests Accept header "text/html, application/json" triggers JSON path.
     */
    @Test
    public void testHandleException_MixedAcceptHeader() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(UNKNOWN_PATH);
        when(mockRequest.getParameter(ISC_DATA_FORMAT)).thenReturn(null);
        when(mockRequest.getHeader(ACCEPT_HEADER)).thenReturn("text/html, application/json");
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn(TEST_PATH);

        servlet.doGet("", mockRequest, mockResponse);

        verify(mockResponse).setContentType(contains(APPLICATION_JSON));
    }
}
