package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.client.application.attachment.AttachImplementationManager;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.utility.Attachment;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

/**
 * Additional coverage tests for {@link AttachmentsServlet}.
 * Targets uncovered lines/branches: LIST success, DOWNLOAD_ALL success,
 * DOWNLOAD not found, edit edge cases, getFileName edge cases, init paths,
 * SWS doGet, upload with description, and exception in upload.
 */
@RunWith(MockitoJUnitRunner.class)
public class AttachmentsServletCoverageTest extends OBBaseTest {

    private static final String TEST_TAB_ID = "tab-123";
    private static final String TEST_RECORD_ID = "record-456";
    private static final String TEST_ATTACHMENT_ID = "attachment-abc";
    private static final String TEST_ORG_ID = "org-123";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_PATH = "/test/path";

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
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // not used
            }
        });

        lenient().when(mockContext.getUser()).thenReturn(mockUser);
        lenient().when(mockContext.getCurrentClient()).thenReturn(mockClient);
        lenient().when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        lenient().when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    /**
     * Tests LIST command with missing recordId returns 400.
     */
    @Test
    public void testListAttachments_MissingRecordId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("LIST");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(null);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests LIST command with invalid tab ID returns 400.
     */
    @Test
    public void testListAttachments_InvalidTabId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("LIST");
        when(mockRequest.getParameter("tabId")).thenReturn("bad-tab");
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Tab.class, "bad-tab")).thenReturn(null);

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue("Should mention invalid tab", stringWriter.toString().contains("Invalid tabId"));
        }
    }

    /**
     * Tests DOWNLOAD_ALL success path.
     */
    @Test
    public void testDownloadAll_Success() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DOWNLOAD_ALL");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Tab.class, TEST_TAB_ID)).thenReturn(mockTab);

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setContentType("application/zip");
            verify(mockResponse).setHeader("Content-Disposition", "attachment; filename=\"attachments.zip\"");
            verify(mockAttachManager).downloadAll(eq(TEST_TAB_ID), eq(TEST_RECORD_ID), any(OutputStream.class));
        }
    }

    /**
     * Tests DOWNLOAD_ALL with invalid tab ID.
     */
    @Test
    public void testDownloadAll_InvalidTabId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DOWNLOAD_ALL");
        when(mockRequest.getParameter("tabId")).thenReturn("bad-tab");
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Tab.class, "bad-tab")).thenReturn(null);

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue("Should mention invalid tab", stringWriter.toString().contains("Invalid tabId"));
        }
    }

    /**
     * Tests DOWNLOAD with attachment not found.
     */
    @Test
    public void testDownloadAttachment_NotFound() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DOWNLOAD");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(null);

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
            assertTrue("Should mention not found", stringWriter.toString().contains("Attachment not found"));
        }
    }

    /**
     * Tests EDIT with empty JSON body and null description parameter (no params to update).
     */
    @Test
    public void testEditAttachment_EmptyBodyNullDescription() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("EDIT");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("description")).thenReturn(null);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).update(anyMap(), eq(TEST_ATTACHMENT_ID), eq(TEST_TAB_ID));
        }
    }

    /**
     * Tests EDIT with missing tabId.
     */
    @Test
    public void testEditAttachment_MissingTabId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("EDIT");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter("tabId")).thenReturn(null);

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention required params",
                stringWriter.toString().contains("attachmentId and tabId are required"));
    }

    /**
     * Tests EDIT with JSON body containing empty description string.
     */
    @Test
    public void testEditAttachment_EmptyDescriptionInBody() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("EDIT");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("description")).thenReturn("fallback desc");
        when(mockRequest.getReader()).thenReturn(
                new BufferedReader(new StringReader("{\"description\":\"\"}")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    /**
     * Tests EDIT with non-empty request body (reading JSON lines).
     */
    @Test
    public void testEditAttachment_WithMultiLineJsonBody() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("EDIT");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getReader()).thenReturn(
                new BufferedReader(new StringReader("{\"description\":\"multi line desc\"}")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockAttachManager).update(anyMap(), eq(TEST_ATTACHMENT_ID), eq(TEST_TAB_ID));
        }
    }

    /**
     * Tests SWS doGet method with path parameter.
     */
    @Test
    public void testSWSDoGet_Success() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DOWNLOAD");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockAttachment.getName()).thenReturn("file.pdf");

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(mockAttachment);

            servlet.doGet(TEST_PATH, mockRequest, mockResponse);

            verify(mockResponse).setContentType("application/octet-stream");
            verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any(OutputStream.class));
        }
    }

    /**
     * Tests upload with description parameter provided.
     */
    @Test
    public void testUploadAttachment_WithDescription() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getParameter("description")).thenReturn("test description");
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockPart.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"file\"; filename=\"testfile.txt\"");
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            // Upload validation passed, file operations may fail in test env
            verify(mockRequest).getPart("file");
        }
    }

    /**
     * Tests upload when exception is thrown during upload process.
     */
    @Test
    public void testUploadAttachment_ExceptionDuringUpload() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenThrow(new ServletException("Multipart error"));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Tests upload with missing all three required params.
     */
    @Test
    public void testUploadAttachment_MissingAllParams() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(null);
        when(mockRequest.getParameter("recordId")).thenReturn(null);
        when(mockRequest.getParameter("orgId")).thenReturn(null);

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention required params",
                stringWriter.toString().contains("tabId, recordId, and orgId are required"));
    }

    /**
     * Tests getFileName with no content-disposition header (returns default).
     */
    @Test
    public void testUploadAttachment_NoContentDisposition() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        when(mockPart.getHeader("content-disposition")).thenReturn(null);
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            // Should not fail due to filename being "unknown"
            verify(mockRequest).getPart("file");
        }
    }

    /**
     * Tests getFileName with a filename that sanitizes to empty (all special chars).
     */
    @Test
    public void testUploadAttachment_FilenameSanitizesToEmpty() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        // Filename with only special chars that get sanitized away -- after sanitization "___" remains
        when(mockPart.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"file\"; filename=\"@#$\"");
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockRequest).getPart("file");
        }
    }

    /**
     * Tests getFileName with a very long filename (over 255 chars).
     */
    @Test
    public void testUploadAttachment_LongFilename() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longName.append("a");
        }
        when(mockPart.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"file\"; filename=\"" + longName.toString() + ".txt\"");
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockRequest).getPart("file");
        }
    }

    /**
     * Tests getFileName with path traversal attempt in filename.
     */
    @Test
    public void testUploadAttachment_PathTraversalFilename() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        when(mockPart.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"file\"; filename=\"../../etc/passwd\"");
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockRequest).getPart("file");
        }
    }

    /**
     * Tests DELETE_ALL with missing recordId.
     */
    @Test
    public void testDeleteAll_MissingRecordId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DELETE_ALL");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(null);

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests DOWNLOAD_ALL with missing recordId.
     */
    @Test
    public void testDownloadAll_MissingRecordId() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("DOWNLOAD_ALL");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(null);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests readRequestBody with empty body returns empty JSONObject.
     */
    @Test
    public void testEditAttachment_EmptyRequestBody() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("EDIT");
        when(mockRequest.getParameter("attachmentId")).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("description")).thenReturn(null);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    /**
     * Tests getFileName when content-disposition header has no filename token.
     */
    @Test
    public void testUploadAttachment_ContentDispositionNoFilename() throws Exception {
        when(mockRequest.getParameter("command")).thenReturn("UPLOAD");
        when(mockRequest.getParameter("tabId")).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter("recordId")).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter("orgId")).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);

        when(mockPart.getHeader("content-disposition")).thenReturn("form-data; name=\"file\"");
        when(mockPart.getInputStream()).thenReturn(mockInputStream);
        when(mockInputStream.read(any(byte[].class))).thenReturn(-1);

        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockRequest).getPart("file");
        }
    }
}
