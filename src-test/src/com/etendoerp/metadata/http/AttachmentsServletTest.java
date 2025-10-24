package com.etendoerp.metadata.http;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
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
 *
 * <p>Tests include validation of:</p>
 * <ul>
 * <li>Parameter validation for all operations</li>
 * <li>Command routing and execution</li>
 * <li>Attachment listing with metadata</li>
 * <li>Attachment upload with multipart form data</li>
 * <li>Single attachment download</li>
 * <li>Multiple attachments download as ZIP</li>
 * <li>Attachment metadata editing</li>
 * <li>Single and bulk deletion operations</li>
 * <li>Error response formatting</li>
 * <li>JSON serialization/deserialization</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class AttachmentsServletTest extends OBBaseTest {

    // Test constants
    private static final String TEST_TAB_ID = "tab-123";
    private static final String TEST_RECORD_ID = "record-456";
    private static final String TEST_ORG_ID = "org-789";
    private static final String TEST_ATTACHMENT_ID = "attachment-abc";
    private static final String TEST_FILE_NAME = "test-document.pdf";
    private static final String TEST_DESCRIPTION = "Test attachment description";
    private static final String TEST_DATA_TYPE = "application/pdf";
    private static final String TEST_USER_ID = "user-123";
    private static final byte[] TEST_FILE_CONTENT = "Test file content".getBytes();

    // Parameter names
    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_TAB_ID = "tabId";
    private static final String PARAM_RECORD_ID = "recordId";
    private static final String PARAM_ORG_ID = "orgId";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_FILE = "file";

    // Command constants
    private static final String CMD_LIST = "LIST";
    private static final String CMD_UPLOAD = "UPLOAD";
    private static final String CMD_DOWNLOAD = "DOWNLOAD";
    private static final String CMD_DOWNLOAD_ALL = "DOWNLOAD_ALL";
    private static final String CMD_EDIT = "EDIT";
    private static final String CMD_DELETE = "DELETE";
    private static final String CMD_DELETE_ALL = "DELETE_ALL";

    // Test validation constants
    private static final String INVALID_TAB_ID = "invalid-tab";

    // Error message constants
    private static final String ERROR_MSG_REQUIRED_PARAMS = "Error message should mention required parameters";
    private static final String ERROR_MSG_TAB_RECORD_REQUIRED = "tabId and recordId are required";
    private static final String ERROR_MSG_INVALID_TAB = "Error message should mention invalid tabId";

    // Response message constants
    private static final String RESPONSE_SUCCESS_INDICATOR = "Response should indicate success";
    private static final String RESPONSE_SUCCESS_JSON = "\"success\":true";
    private static final String RESPONSE_SUCCESS_MESSAGE = "Response should contain success message";

    // Mock objects
    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private AttachImplementationManager mockAttachManager;

    @Mock
    private OBDal mockDal;

    @Mock
    private OBContext mockContext;

    @Mock
    private Tab mockTab;

    @Mock
    private Table mockTable;

    @Mock
    private Attachment mockAttachment;

    @Mock
    private User mockUser;

    @Mock
    private Client mockClient;

    @Mock
    private Organization mockOrganization;

    @Mock
    private OBCriteria<Attachment> mockCriteria;

    @Mock
    private Part mockFilePart;

    private AttachmentsServlet servlet;
    private StringWriter stringWriter;
    private ByteArrayOutputStream outputStream;

    /**
     * Sets up the test environment before each test method execution.
     * Initialize mocks and configures common behavior.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new AttachmentsServlet();

        // Inject mock AttachImplementationManager using reflection
        java.lang.reflect.Field field = AttachmentsServlet.class.getDeclaredField("attachManager");
        field.setAccessible(true);
        field.set(servlet, mockAttachManager);

        stringWriter = new StringWriter();
        outputStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(mockResponse.getWriter()).thenReturn(printWriter);
        when(mockResponse.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // No-op: This method is intentionally empty as WriteListener is not needed for testing.
                // In a real servlet container, this would be used for non-blocking I/O operations.
            }
        });

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    // ==================== LIST Tests ====================

    /**
     * Tests successful listing of attachments with valid parameters.
     */
    @Test
    public void testListAttachments_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_LIST);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        List<JSONObject> mockAttachmentsList = createMockAttachmentsList();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<AttachmentUtils> attachUtilsMock = mockStatic(AttachmentUtils.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            attachUtilsMock.when(() -> AttachmentUtils.getTabAttachmentsForRows(eq(mockTab), any()))
                    .thenReturn(mockAttachmentsList);

            when(mockAttachment.getDataType()).thenReturn(TEST_DATA_TYPE);
            when(mockAttachment.getCreatedBy()).thenReturn(mockUser);
            when(mockAttachment.getCreationDate()).thenReturn(new Date());
            when(mockAttachment.getSequenceNumber()).thenReturn(1L);
            when(mockUser.getId()).thenReturn(TEST_USER_ID);
            when(mockUser.getIdentifier()).thenReturn("Test User");

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockResponse).setContentType("application/json");
            verify(mockResponse).setCharacterEncoding("UTF-8");

            String responseContent = stringWriter.toString();
            assertNotNull("Response should not be null", responseContent);
            assertTrue("Response should contain attachments key",
                    responseContent.contains("\"attachments\""));
        }
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
            assertTrue(ERROR_MSG_INVALID_TAB,
                    responseContent.contains("Invalid tabId"));
        }
    }

    /**
     * Tests default command when no command parameter is provided (should default to LIST).
     */
    @Test
    public void testGetRequest_DefaultToList() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
             MockedStatic<AttachmentUtils> attachUtilsMock = mockStatic(AttachmentUtils.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);
            attachUtilsMock.when(() -> AttachmentUtils.getTabAttachmentsForRows(eq(mockTab), any()))
                    .thenReturn(new ArrayList<>());

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    // ==================== UPLOAD Tests ====================

    /**
     * Tests successful attachment upload.
     */
    @Test
    public void testUploadAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getParameter(PARAM_DESCRIPTION)).thenReturn(TEST_DESCRIPTION);
        when(mockRequest.getPart(PARAM_FILE)).thenReturn(mockFilePart);

        when(mockFilePart.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"file\"; filename=\"" + TEST_FILE_NAME + "\"");
        when(mockFilePart.getInputStream())
                .thenReturn(new ByteArrayInputStream(TEST_FILE_CONTENT));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            doNothing().when(mockAttachManager).upload(anyMap(), eq(TEST_TAB_ID),
                    eq(TEST_RECORD_ID), eq(TEST_ORG_ID), any(File.class));

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).upload(anyMap(), eq(TEST_TAB_ID),
                    eq(TEST_RECORD_ID), eq(TEST_ORG_ID), any(File.class));

            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
        }
    }

    /**
     * Tests UPLOAD command with missing file.
     */
    @Test
    public void testUploadAttachment_MissingFile() throws Exception {
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

    /**
     * Tests UPLOAD command with missing required parameters.
     */
    @Test
    public void testUploadAttachment_MissingParameters() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_UPLOAD);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_ORG_ID)).thenReturn(TEST_ORG_ID);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains("tabId, recordId, and orgId are required"));
    }

    // ==================== DOWNLOAD Tests ====================

    /**
     * Tests successful single attachment download.
     */
    @Test
    public void testDownloadAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            when(mockAttachment.getName()).thenReturn(TEST_FILE_NAME);
            when(mockAttachment.getDataType()).thenReturn(TEST_DATA_TYPE);

            doNothing().when(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any());

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setContentType(TEST_DATA_TYPE);
            verify(mockResponse).setHeader(eq("Content-Disposition"),
                    contains(TEST_FILE_NAME));
            verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any());
        }
    }

    /**
     * Tests DOWNLOAD command with missing attachmentId.
     */
    @Test
    public void testDownloadAttachment_MissingAttachmentId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention required attachmentId",
                responseContent.contains("attachmentId is required"));
    }

    /**
     * Tests DOWNLOAD command with non-existent attachment.
     */
    @Test
    public void testDownloadAttachment_NotFound() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(null);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention attachment not found",
                    responseContent.contains("Attachment not found"));
        }
    }

    /**
     * Tests DOWNLOAD with null dataType (should use default).
     */
    @Test
    public void testDownloadAttachment_NullDataType() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);
            when(mockAttachment.getName()).thenReturn(TEST_FILE_NAME);
            when(mockAttachment.getDataType()).thenReturn(null);

            doNothing().when(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any());

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setContentType("application/octet-stream");
        }
    }

    // ==================== DOWNLOAD_ALL Tests ====================

    /**
     * Tests successful download of all attachments as ZIP.
     */
    @Test
    public void testDownloadAllAttachments_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD_ALL);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(PARAM_RECORD_ID)).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            doNothing().when(mockAttachManager).downloadAll(eq(TEST_TAB_ID),
                    eq(TEST_RECORD_ID), any());

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setContentType("application/zip");
            verify(mockResponse).setHeader("Content-Disposition",
                    "attachment; filename=\"attachments.zip\"");
            verify(mockAttachManager).downloadAll(eq(TEST_TAB_ID),
                    eq(TEST_RECORD_ID), any());
        }
    }

    /**
     * Tests DOWNLOAD_ALL command with missing parameters.
     */
    @Test
    public void testDownloadAllAttachments_MissingParameters() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DOWNLOAD_ALL);
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

    // ==================== EDIT Tests ====================

    /**
     * Tests successful attachment metadata edit.
     */
    @Test
    public void testEditAttachment_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);

        JSONObject requestJson = new JSONObject();
        requestJson.put(PARAM_DESCRIPTION, "Updated description");

        BufferedReader reader = new BufferedReader(
                new StringReader(requestJson.toString()));
        when(mockRequest.getReader()).thenReturn(reader);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            doNothing().when(mockAttachManager).update(anyMap(), eq(TEST_ATTACHMENT_ID),
                    eq(TEST_TAB_ID));

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).update(anyMap(), eq(TEST_ATTACHMENT_ID),
                    eq(TEST_TAB_ID));

            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
            assertTrue(RESPONSE_SUCCESS_MESSAGE,
                    responseContent.contains("updated successfully"));
        }
    }

    /**
     * Tests EDIT command with missing parameters.
     */
    @Test
    public void testEditAttachment_MissingParameters() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_EDIT);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_TAB_ID)).thenReturn(TEST_TAB_ID);

        BufferedReader reader = new BufferedReader(new StringReader("{}"));
        when(mockRequest.getReader()).thenReturn(reader);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue(ERROR_MSG_REQUIRED_PARAMS,
                responseContent.contains("attachmentId and tabId are required"));
    }

    // ==================== DELETE Tests ====================

    /**
     * Tests successful single attachment deletion.
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
            doNothing().when(mockAttachManager).delete(mockAttachment);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).delete(mockAttachment);

            String responseContent = stringWriter.toString();
            assertTrue(RESPONSE_SUCCESS_INDICATOR,
                    responseContent.contains(RESPONSE_SUCCESS_JSON));
            assertTrue(RESPONSE_SUCCESS_MESSAGE,
                    responseContent.contains("deleted successfully"));
        }
    }

    /**
     * Tests DELETE command with missing attachmentId.
     */
    @Test
    public void testDeleteAttachment_MissingAttachmentId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_COMMAND)).thenReturn(CMD_DELETE);
        when(mockRequest.getParameter(PARAM_ATTACHMENT_ID)).thenReturn(null);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention required attachmentId",
                responseContent.contains("attachmentId is required"));
    }

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

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);

            String fromWriter = stringWriter.toString();
            String fromStream = outputStream.toString();

            System.out.println("=== RESPONSE DEBUG ===");
            System.out.println("From Writer: [" + fromWriter + "]");
            System.out.println("From Stream: [" + fromStream + "]");
            System.out.println("=====================");

            String responseContent = !fromWriter.isEmpty() ? fromWriter : fromStream;

            assertFalse("Response should not be empty", responseContent.isEmpty());

            assertTrue(ERROR_MSG_INVALID_TAB,
                    responseContent.contains("Invalid tabId"));
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

    // ==================== Helper Methods ====================

    /**
     * Creates a list of mock attachments for testing LIST operation.
     */
    private List<JSONObject> createMockAttachmentsList() throws Exception {
        List<JSONObject> attachments = new ArrayList<>();

        JSONObject attachment1 = new JSONObject();
        attachment1.put("id", TEST_ATTACHMENT_ID);
        attachment1.put("name", TEST_FILE_NAME);
        attachment1.put("text", TEST_DESCRIPTION);

        attachments.add(attachment1);
        return attachments;
    }

    /**
     * Creates a list of mock Attachment entities for testing DELETE_ALL operation.
     */
    private List<Attachment> createMockAttachmentsForDeletion() {
        List<Attachment> attachments = new ArrayList<>();

        Attachment attachment1 = mock(Attachment.class);
        when(attachment1.getId()).thenReturn(TEST_ATTACHMENT_ID);
        when(attachment1.getName()).thenReturn(TEST_FILE_NAME);

        Attachment attachment2 = mock(Attachment.class);
        when(attachment2.getId()).thenReturn("attachment-def");
        when(attachment2.getName()).thenReturn("document2.pdf");

        attachments.add(attachment1);
        attachments.add(attachment2);

        return attachments;
    }
}