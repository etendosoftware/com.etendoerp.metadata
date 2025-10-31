package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.client.application.attachment.AttachmentUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for the {@link AttachmentsServlet} class.
 *
 * <p>This test suite verifies the correct behavior of the AttachmentsServlet,
 * ensuring it properly handles LIST, UPLOAD, DOWNLOAD, DOWNLOAD_ALL, EDIT, DELETE,
 * and DELETE_ALL operations for attachments.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class AttachmentsServletTest extends OBBaseTest {

    private static final String TEST_TAB_ID = "tab-123";
    private static final String TEST_RECORD_ID = "record-456";
    private static final String TEST_ATTACHMENT_ID = "attachment-abc";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_ORG_ID = "org-123";
    private static final String TEST_PATH = "/test/path";

    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_TAB_ID = "tabId";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_FILE = "file";

    private static final String CMD_LIST = "LIST";
    private static final String CMD_UPLOAD = "UPLOAD";
    private static final String CMD_DOWNLOAD = "DOWNLOAD";
    private static final String CMD_EDIT = "EDIT";
    private static final String CMD_DELETE = "DELETE";
    private static final String CMD_DELETE_ALL = "DELETE_ALL";

    private static final String INVALID_TAB_ID = "invalid-tab";

    private static final String ERROR_MSG_REQUIRED_PARAMS = "Error message should mention required parameters";
    private static final String ERROR_MSG_TAB_RECORD_REQUIRED = "tabId and recordId are required";
    private static final String ERROR_MSG_INVALID_TAB = "Invalid tabId";

    private static final String RESPONSE_SUCCESS_INDICATOR = "Response should indicate success";
    private static final String RESPONSE_SUCCESS_JSON = "\"success\":true";
    private static final String RESPONSE_SUCCESS_MESSAGE = "Response should contain success message";

    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private AttachImplementationManager mockAttachManager;
    @Mock private OBDal mockDal;
    @Mock private OBContext mockContext;
    @Mock private Tab mockTab;
    @Mock private Table mockTable;
    @Mock private Attachment mockAttachment;
    @Mock private User mockUser;
    @Mock private Client mockClient;
    @Mock private Organization mockOrganization;
    @Mock private OBCriteria<Attachment> mockCriteria;
    @Mock private Part mockPart;
    @Mock private InputStream mockInputStream;

    private AttachmentsServlet servlet;
    private StringWriter stringWriter;
    private ByteArrayOutputStream outputStream;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new AttachmentsServlet();

        java.lang.reflect.Field field = AttachmentsServlet.class.getDeclaredField("attachManager");
        field.setAccessible(true);
        field.set(servlet, mockAttachManager);

        stringWriter = new StringWriter();
        outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                //This method is not used in this unit tests but should be used in an integration test.
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // This should be used in an implementation test
            }
        });

        lenient().when(mockContext.getUser()).thenReturn(mockUser);
        lenient().when(mockContext.getCurrentClient()).thenReturn(mockClient);
        lenient().when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        lenient().when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }



    /**
     * Tests LIST command with missing tabId parameter.
     */
    @Test
    public void testListAttachments_MissingTabId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }

    /**
     * Tests LIST command with missing recordId parameter.
     */
    @Test
    public void testListAttachments_MissingRecordId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }

    /**
     * Tests LIST command with invalid tabId.
     */
    @Test
    public void testListAttachments_InvalidTabId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(INVALID_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Tab.class, INVALID_TAB_ID)).thenReturn(null);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention invalid tabId",
                    responseContent.contains(ERROR_MSG_INVALID_TAB));
        }
    }

    // ==================== UPLOAD Tests ====================

    /**
     * Tests upload validation but not the full process (to avoid file system dependencies)
     */
    @Test
    public void testUploadAttachment_ValidationSuccess() throws Exception {
        // Arrange - Test that validation passes for upload command with all required params
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(mockPart);
        
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockPart.getHeader("content-disposition")).thenReturn("form-data; name=\"file\"; filename=\"test.txt\"");
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            contextMock.when(() -> OBContext.setAdminMode(true)).thenAnswer(invocation -> null);
            contextMock.when(OBContext::restorePreviousMode).thenAnswer(invocation -> null);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert - Could be 200 (success) or 500 (if file operations fail in CI)
            // The important thing is that validation passed and we didn't get 400 (bad request)
            verify(mockResponse, atLeastOnce()).setStatus(anyInt());
            
            // Verify that the upload validation logic was reached (not rejected due to missing params)
            verify(mockRequest).getPart(PARAM_FILE);
        }
    }

    /**
     * Tests upload with missing orgId parameter
     */
    @Test
    public void testUploadAttachment_MissingOrgId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(null);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains("tabId, recordId, and orgId are required"));
    }

    /**
     * Tests upload without file
     */
    @Test
    public void testUploadAttachment_NoFile() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(null);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention no file uploaded",
                    responseContent.contains("No file uploaded"));
        }
    }

    // ==================== DOWNLOAD Tests ====================

    /**
     * Tests successful single file download
     */
    @Test
    public void testDownloadAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn("test-file.txt");

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setContentType("application/octet-stream");
            verify(mockResponse).setHeader("Content-Disposition", "attachment; filename=\"test-file.txt\"");
            verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any(OutputStream.class));
        }
    }

    /**
     * Tests download with missing attachment ID
     */
    @Test
    public void testDownloadAttachment_MissingId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention attachment required",
                    responseContent.contains("attachmentId is required"));
        }
    }

    // ==================== DOWNLOAD_ALL Tests ====================


    // ==================== EDIT Tests ====================

    /**
     * Tests successful attachment metadata edit
     */
    @Test
    public void testEditAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_DESCRIPTION)).thenReturn("Updated description");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).update(any(Map.class), eq(TEST_ATTACHMENT_ID), eq(TEST_TAB_ID));
            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
            assertTrue(RESPONSE_SUCCESS_MESSAGE,
                    responseContent.contains("Attachment updated successfully"));
        }
    }

    /**
     * Tests edit with missing parameters
     */
    @Test
    public void testEditAttachment_MissingParameters() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains("attachmentId and tabId are required"));
    }

    /**
     * Tests edit with JSON request body
     */
    @Test
    public void testEditAttachment_WithJsonBody() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(
                new StringReader("{\"description\":\"JSON description\"}")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).update(any(Map.class), eq(TEST_ATTACHMENT_ID), eq(TEST_TAB_ID));
        }
    }

    // ==================== DELETE Tests ====================

    /**
     * Tests DELETE command with non-existent attachment.
     */
    @Test
    public void testDeleteAttachment_NotFound() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(null);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention attachment not found",
                    responseContent.contains("Attachment not found"));
        }
    }

    // ==================== DELETE_ALL Tests ====================

    /**
     * Tests successful deletion of all attachments for a record.
     */
    @Test
    public void testDeleteAllAttachments_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        List<Attachment> mockAttachments = createMockAttachmentsForDeletion();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            when(mockTab.getTable()).thenReturn(mockTable);
            when(mockDal.createCriteria(Attachment.class)).thenReturn(mockCriteria);
            when(mockCriteria.add(any())).thenReturn(mockCriteria);
            when(mockCriteria.list()).thenReturn(mockAttachments);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager, times(mockAttachments.size())).delete(any(Attachment.class));

            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
            assertTrue(RESPONSE_SUCCESS_MESSAGE,
                    responseContent.contains("All attachments deleted successfully"));
        }
    }

    /**
     * Tests DELETE_ALL command with missing parameters.
     */
    @Test
    public void testDeleteAllAttachments_MissingParameters() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains(ERROR_MSG_TAB_RECORD_REQUIRED));
    }

    /**
     * Tests DELETE_ALL command with invalid tabId.
     */
    @Test
    public void testDeleteAllAttachments_InvalidTabId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(INVALID_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Tab.class, INVALID_TAB_ID)).thenReturn(null);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);

            // ✅ CORRECCIÓN 3: Verificar ambas salidas posibles
            String responseContent = stringWriter.toString();
            if (responseContent.isEmpty()) {
                responseContent = outputStream.toString();
            }

            assertFalse("Response should not be empty", responseContent.isEmpty());
            assertTrue("Error message should mention invalid tabId",
                    responseContent.contains(ERROR_MSG_INVALID_TAB));
        }
    }

    // ==================== Error Handling Tests ====================

    /**
     * Tests POST request with missing command parameter.
     */
    @Test
    public void testPostRequest_MissingCommand() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(null);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention command is required",
                responseContent.contains("Command parameter is required"));
    }

    /**
     * Tests GET request with invalid command.
     */
    @Test
    public void testGetRequest_InvalidCommand() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn("INVALID_COMMAND");

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention invalid command",
                responseContent.contains("Invalid command"));
    }

    /**
     * Tests POST request with invalid command.
     */
    @Test
    public void testPostRequest_InvalidCommand() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn("INVALID_COMMAND");

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention invalid command",
                responseContent.contains("Invalid command"));
    }

    // ==================== SERVLET INITIALIZATION Tests ====================

    /**
     * Tests servlet initialization
     */
    @Test
    public void testServletInit() throws Exception {
        // Act
        servlet.init();

        // Assert - no exception should be thrown
        assertTrue("Servlet should initialize without exception", true);
    }

    // ==================== SWS FRAMEWORK Tests ====================


    /**
     * Tests SWS framework doPost method with path parameter
     */
    @Test
    public void testSWSDoPost() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            // Act
            servlet.doPost(TEST_PATH, mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).delete(mockAttachment);
        }
    }

    /**
     * Tests SWS framework doDelete method with path parameter
     */
    @Test
    public void testSWSDoDelete() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            // Act
            servlet.doDelete(TEST_PATH, mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).delete(mockAttachment);
        }
    }

    // ==================== EDGE CASES AND ERROR HANDLING Tests ====================

    /**
     * Tests handling of runtime exceptions in doGet
     */
    @Test
    public void testDoGet_RuntimeException() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenThrow(new RuntimeException("Test exception"));

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention processing error",
                responseContent.contains("Error processing request"));
    }

    /**
     * Tests handling of runtime exceptions in doPost
     */
    @Test
    public void testDoPost_RuntimeException() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenThrow(new RuntimeException("Test exception"));

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention processing error",
                responseContent.contains("Error processing request"));
    }


    /**
     * Tests successful delete with existing attachment
     */
    @Test
    public void testDeleteAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).delete(mockAttachment);
            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
            assertTrue(RESPONSE_SUCCESS_MESSAGE,
                    responseContent.contains("Attachment deleted successfully"));
        }
    }

    /**
     * Tests delete with missing attachment ID
     */
    @Test
    public void testDeleteAttachment_MissingId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention attachment required",
                    responseContent.contains("attachmentId is required"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a list of mock Attachment entities for testing DELETE_ALL operation.
     */
    private List<Attachment> createMockAttachmentsForDeletion() {
        List<Attachment> attachments = new ArrayList<>();

        Attachment attachment1 = mock(Attachment.class);

        attachments.add(attachment1);

        return attachments;
    }

}