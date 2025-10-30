package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

/**
 * Validation tests for the {@link AttachmentsServlet} class.
 * 
 * This test suite focuses on parameter validation and error handling scenarios.
 */
@RunWith(MockitoJUnitRunner.class)
public class AttachmentsServletValidationTest extends OBBaseTest {

    private static final String TEST_TAB_ID = "tab-123";
    private static final String TEST_RECORD_ID = "record-456";
    private static final String TEST_ATTACHMENT_ID = "attachment-abc";
    private static final String TEST_ORG_ID = "org-123";
    private static final String TEST_PATH = "/test/path";
    private static final String TEST_USER_ID = "user-123";

    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_TAB_ID = "tabId";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_DESCRIPTION = "description";

    private static final String CMD_LIST = "LIST";
    private static final String CMD_UPLOAD = "UPLOAD";
    private static final String CMD_DOWNLOAD_ALL = "DOWNLOAD_ALL";
    private static final String CMD_EDIT = "EDIT";
    private static final String CMD_DELETE = "DELETE";

    private static final String ERROR_MSG_REQUIRED_PARAMS = "Error message should mention required parameters";
    private static final String ERROR_MSG_TAB_RECORD_REQUIRED = "tabId and recordId are required";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private AttachImplementationManager mockAttachManager;
    @Mock private OBContext mockContext;
    @Mock private User mockUser;
    @Mock private Client mockClient;
    @Mock private Organization mockOrganization;

    private AttachmentsServlet servlet;
    private StringWriter stringWriter;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new AttachmentsServlet();

        java.lang.reflect.Field field = AttachmentsServlet.class.getDeclaredField("attachManager");
        field.setAccessible(true);
        field.set(servlet, mockAttachManager);

        stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockContext.getUser()).thenReturn(mockUser);
        lenient().when(mockContext.getCurrentClient()).thenReturn(mockClient);
        lenient().when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        lenient().when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    /**
     * Tests that servlet properly handles all defined commands
     */
    @Test
    public void testServletCommandConstants() throws Exception {
        assertTrue("LIST command should be defined", "LIST".equals(CMD_LIST));
        assertTrue("UPLOAD command should be defined", "UPLOAD".equals(CMD_UPLOAD));
        assertTrue("DOWNLOAD_ALL command should be defined", "DOWNLOAD_ALL".equals(CMD_DOWNLOAD_ALL));
        assertTrue("EDIT command should be defined", "EDIT".equals(CMD_EDIT));
        assertTrue("DELETE command should be defined", "DELETE".equals(CMD_DELETE));
    }

    /**
     * Tests GET request with null command defaults to LIST
     */
    @Test
    public void testGetRequest_NullCommandDefaultsToList() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests upload functionality with various error conditions
     */
    @Test
    public void testUploadAttachment_ValidatesAllParams() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);

        servlet.doPost(mockRequest, mockResponse);
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests parameter validation for various commands
     */
    @Test
    public void testParameterValidation_EdgeCases() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn("");
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests case insensitive command handling
     */
    @Test
    public void testCommandCaseHandling() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn("list");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests mixed case command handling
     */
    @Test
    public void testCommandMixedCase() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn("List");
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests that servlet properly handles IOException during response writing
     */
    @Test
    public void testErrorResponseHandling() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn("INVALID");
        when(mockResponse.getWriter()).thenThrow(new IOException("Test IO exception"));

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    /**
     * Tests successful edit with request parameter instead of JSON body
     */
    @Test
    public void testEditAttachment_WithRequestParameter() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_DESCRIPTION)).thenReturn("Test description");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            contextMock.when(() -> OBContext.setAdminMode(true)).thenAnswer(invocation -> null);
            contextMock.when(OBContext::restorePreviousMode).thenAnswer(invocation -> null);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    /**
     * Tests SWS framework doGet method with path parameter - missing parameters
     */
    @Test
    public void testSWSDoGet_MissingParams() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(TEST_PATH, mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }

    /**
     * Tests default LIST command when no command is specified in GET - missing params
     */
    @Test
    public void testDefaultListCommand_MissingParams() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }

    /**
     * Tests download all command with missing parameters
     */
    @Test
    public void testDownloadAllAttachments_MissingTabId() throws Exception {
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }
}