package com.etendoerp.metadata.http;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

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

    private static final String COMMAND = "command";
    private static final String TAB_ID = "tabId";
    private static final String RECORD_ID = "recordId";
    private static final String ATTACHMENT_ID = "attachmentId";
    private static final String DESCRIPTION_PARAM = "description";
    private static final String ORG_ID = "orgId";
    private static final String CONTENT_DISPOSITION = "content-disposition";
    private static final String UPLOAD_CMD = "UPLOAD";
    private static final String DOWNLOAD_ALL_CMD = "DOWNLOAD_ALL";
    private static final String BAD_TAB = "bad-tab";
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
        when(mockRequest.getParameter(COMMAND)).thenReturn("LIST");
        when(mockRequest.getParameter(TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(null);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests LIST command with invalid tab ID returns 400.
     */
    @Test
    public void testListAttachments_InvalidTabId() throws Exception {
        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockTabLookup("LIST", BAD_TAB, dalMock, contextMock);
            when(mockDal.get(Tab.class, BAD_TAB)).thenReturn(null);

            servlet.doGet(mockRequest, mockResponse);

            assertBadRequestContains("Invalid tabId");
        }
    }

    private void mockStaticContext(MockedStatic<OBContext> contextMock) {
        contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
    }

    private void mockTabLookup(String command, String tabId,
            MockedStatic<OBDal> dalMock, MockedStatic<OBContext> contextMock) {
        when(mockRequest.getParameter(COMMAND)).thenReturn(command);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(tabId);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(TEST_RECORD_ID);
        dalMock.when(OBDal::getInstance).thenReturn(mockDal);
        mockStaticContext(contextMock);
    }

    private void mockAttachmentLookup(String command, MockedStatic<OBDal> dalMock,
            MockedStatic<OBContext> contextMock) {
        when(mockRequest.getParameter(COMMAND)).thenReturn(command);
        when(mockRequest.getParameter(ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        dalMock.when(OBDal::getInstance).thenReturn(mockDal);
        mockStaticContext(contextMock);
    }

    private void mockEditRequest(String descriptionParam, String body) throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn("EDIT");
        when(mockRequest.getParameter(ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(DESCRIPTION_PARAM)).thenReturn(descriptionParam);
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    private void mockUploadRequest() throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn(UPLOAD_CMD);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenReturn(mockPart);
        lenient().when(mockPart.getInputStream()).thenReturn(mockInputStream);
        lenient().when(mockInputStream.read(any(byte[].class))).thenReturn(-1);
    }

    private void assertBadRequestContains(String expectedMessage) {
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention expected message",
                stringWriter.toString().contains(expectedMessage));
    }

    private void assertUploadAttempted(String contentDisposition) throws Exception {
        mockUploadRequest();
        when(mockPart.getHeader(CONTENT_DISPOSITION)).thenReturn(contentDisposition);
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockStaticContext(contextMock);
            servlet.doPost(mockRequest, mockResponse);
            verify(mockRequest).getPart("file");
        }
    }

    private String buildLongFileName() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longName.append("a");
        }
        return longName + ".txt";
    }

    private void assertEditRequestSucceeds() throws Exception {
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockStaticContext(contextMock);
            servlet.doPost(mockRequest, mockResponse);
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    private void assertInternalServerError() {
        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private void assertMissingRequiredUploadParams() {
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention required params",
                stringWriter.toString().contains("tabId, recordId, and orgId are required"));
    }

    private void assertMissingEditParams() {
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention required params",
                stringWriter.toString().contains("attachmentId and tabId are required"));
    }

    private void assertEditBodyHandled(String body, String description, boolean verifyUpdate)
            throws Exception {
        mockEditRequest(description, body);
        assertEditRequestSucceeds();
        if (verifyUpdate) {
            verify(mockAttachManager).update(anyMap(), eq(TEST_ATTACHMENT_ID), eq(TEST_TAB_ID));
        }
    }

    private void assertMissingRecordId(String command, boolean isGet) throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn(command);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(null);
        if (isGet) {
            servlet.doGet(mockRequest, mockResponse);
        } else {
            servlet.doPost(mockRequest, mockResponse);
        }
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    private void assertDownloadAll(String tabId, boolean validTab) throws Exception {
        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockTabLookup(DOWNLOAD_ALL_CMD, tabId, dalMock, contextMock);
            when(mockDal.get(Tab.class, tabId)).thenReturn(validTab ? mockTab : null);
            servlet.doGet(mockRequest, mockResponse);
            if (validTab) {
                verify(mockResponse).setContentType("application/zip");
                verify(mockResponse).setHeader("Content-Disposition", "attachment; filename=\"attachments.zip\"");
                verify(mockAttachManager).downloadAll(eq(TEST_TAB_ID), eq(TEST_RECORD_ID), any(OutputStream.class));
            } else {
                assertBadRequestContains("Invalid tabId");
            }
        }
    }

    private void assertAttachmentLookup(boolean found) throws Exception {
        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockAttachmentLookup("DOWNLOAD", dalMock, contextMock);
            when(mockDal.get(Attachment.class, TEST_ATTACHMENT_ID)).thenReturn(found ? mockAttachment : null);
            servlet.doGet(found ? TEST_PATH : null, mockRequest, mockResponse);
            if (found) {
                verify(mockResponse).setContentType("application/octet-stream");
                verify(mockAttachManager).download(eq(TEST_ATTACHMENT_ID), any(OutputStream.class));
            } else {
                verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
                assertTrue("Should mention not found",
                        stringWriter.toString().contains("Attachment not found"));
            }
        }
    }

    private void assertUploadExceptionHandled() throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn(UPLOAD_CMD);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(TEST_TAB_ID);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(TEST_RECORD_ID);
        when(mockRequest.getParameter(ORG_ID)).thenReturn(TEST_ORG_ID);
        when(mockRequest.getPart("file")).thenThrow(new ServletException("Multipart error"));
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockStaticContext(contextMock);
            servlet.doPost(mockRequest, mockResponse);
            assertInternalServerError();
        }
    }

    private void assertUploadWithDescription() throws Exception {
        mockUploadRequest();
        when(mockRequest.getParameter(DESCRIPTION_PARAM)).thenReturn("test description");
        when(mockPart.getHeader(CONTENT_DISPOSITION))
                .thenReturn("form-data; name=\"file\"; filename=\"testfile.txt\"");
        try (MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {
            mockStaticContext(contextMock);
            servlet.doPost(mockRequest, mockResponse);
            verify(mockRequest).getPart("file");
        }
    }

    private void assertEditMissingTabId() throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn("EDIT");
        when(mockRequest.getParameter(ATTACHMENT_ID)).thenReturn(TEST_ATTACHMENT_ID);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(null);
        servlet.doPost(mockRequest, mockResponse);
        assertMissingEditParams();
    }

    private void assertUploadMissingParams() throws Exception {
        when(mockRequest.getParameter(COMMAND)).thenReturn(UPLOAD_CMD);
        when(mockRequest.getParameter(TAB_ID)).thenReturn(null);
        when(mockRequest.getParameter(RECORD_ID)).thenReturn(null);
        when(mockRequest.getParameter(ORG_ID)).thenReturn(null);
        servlet.doPost(mockRequest, mockResponse);
        assertMissingRequiredUploadParams();
    }

    private void assertUploadHeaderHandled(String contentDisposition) throws Exception {
        assertUploadAttempted(contentDisposition);
    }

    private String buildUploadHeader(String fileName) {
        return "form-data; name=\"file\"; filename=\"" + fileName + "\"";
    }

    private void assertLongFileNameHandled() throws Exception {
        assertUploadHeaderHandled(buildUploadHeader(buildLongFileName()));
    }

    private void assertSanitizedFileNameHandled() throws Exception {
        assertUploadHeaderHandled(buildUploadHeader("@#$"));
    }

    private void assertPathTraversalHeaderHandled() throws Exception {
        assertUploadHeaderHandled(buildUploadHeader("../../etc/passwd"));
    }

    private void assertNoFilenameHandled() throws Exception {
        assertUploadHeaderHandled("form-data; name=\"file\"");
    }

    private void assertNoContentDispositionHandled() throws Exception {
        assertUploadHeaderHandled(null);
    }

    private void assertEditBodyCase(String body, String description, boolean verifyUpdate)
            throws Exception {
        assertEditBodyHandled(body, description, verifyUpdate);
    }

    private void assertEditEmptyBodyCase(String body, String description) throws Exception {
        assertEmptyEditBody(body, description);
    }

    private void assertEmptyEditBody(String body, String description) throws Exception {
        mockEditRequest(description, body);
        assertEditRequestSucceeds();
    }

    private void assertSwsDownloadSucceeds() throws Exception {
        when(mockAttachment.getName()).thenReturn("file.pdf");
        assertAttachmentLookup(true);
    }

    private void assertAttachmentNotFound() throws Exception {
        assertAttachmentLookup(false);
    }

    private void assertDownloadAllSuccess() throws Exception {
        assertDownloadAll(TEST_TAB_ID, true);
    }

    private void assertDownloadAllInvalidTab() throws Exception {
        assertDownloadAll(BAD_TAB, false);
    }

    private void assertDeleteAllMissingRecordId() throws Exception {
        assertMissingRecordId("DELETE_ALL", false);
    }

    private void assertDownloadAllMissingRecordId() throws Exception {
        assertMissingRecordId(DOWNLOAD_ALL_CMD, true);
    }

    private void assertEditEmptyDescriptionBody() throws Exception {
        assertEditBodyCase("{\"description\":\"\"}", "fallback desc", false);
    }

    private void assertEditMultiLineBody() throws Exception {
        assertEditBodyCase("{\"description\":\"multi line desc\"}", null, true);
    }

    private void assertEditEmptyRequestBody() throws Exception {
        assertEditEmptyBodyCase("", null);
    }

    private void assertEditEmptyBodyNullDescription() throws Exception {
        assertEditBodyCase("{}", null, true);
    }

    /**
     * Tests DOWNLOAD_ALL success path.
     */
    @Test
    public void testDownloadAllSuccess() throws Exception {
        assertDownloadAllSuccess();
    }

    /**
     * Tests DOWNLOAD_ALL with invalid tab ID.
     */
    @Test
    public void testDownloadAllInvalidTabId() throws Exception {
        assertDownloadAllInvalidTab();
    }

    /**
     * Tests DOWNLOAD with attachment not found.
     */
    @Test
    public void testDownloadAttachmentNotFound() throws Exception {
        assertAttachmentNotFound();
    }

    /**
     * Tests EDIT with empty JSON body and null description parameter (no params to update).
     */
    @Test
    public void testEditAttachmentEmptyBodyNullDescription() throws Exception {
        assertEditEmptyBodyNullDescription();
    }

    /**
     * Tests EDIT with missing tabId.
     */
    @Test
    public void testEditAttachmentMissingTabId() throws Exception {
        assertEditMissingTabId();
    }

    /**
     * Tests EDIT with JSON body containing empty description string.
     */
    @Test
    public void testEditAttachmentEmptyDescriptionInBody() throws Exception {
        assertEditEmptyDescriptionBody();
    }

    /**
     * Tests EDIT with non-empty request body (reading JSON lines).
     */
    @Test
    public void testEditAttachmentWithMultiLineJsonBody() throws Exception {
        assertEditMultiLineBody();
    }

    /**
     * Tests SWS doGet method with path parameter.
     */
    @Test
    public void testSWSDoGetSuccess() throws Exception {
        assertSwsDownloadSucceeds();
    }

    /**
     * Tests upload with description parameter provided.
     */
    @Test
    public void testUploadAttachmentWithDescription() throws Exception {
        assertUploadWithDescription();
    }

    /**
     * Tests upload when exception is thrown during upload process.
     */
    @Test
    public void testUploadAttachmentExceptionDuringUpload() throws Exception {
        assertUploadExceptionHandled();
    }

    /**
     * Tests upload with missing all three required params.
     */
    @Test
    public void testUploadAttachmentMissingAllParams() throws Exception {
        assertUploadMissingParams();
    }

    /**
     * Tests getFileName with no content-disposition header (returns default).
     */
    @Test
    public void testUploadAttachmentNoContentDisposition() throws Exception {
        assertNoContentDispositionHandled();
    }

    /**
     * Tests getFileName with a filename that sanitizes to empty (all special chars).
     */
    @Test
    public void testUploadAttachmentFilenameSanitizesToEmpty() throws Exception {
        assertSanitizedFileNameHandled();
    }

    /**
     * Tests getFileName with a very long filename (over 255 chars).
     */
    @Test
    public void testUploadAttachmentLongFilename() throws Exception {
        assertLongFileNameHandled();
    }

    /**
     * Tests getFileName with path traversal attempt in filename.
     */
    @Test
    public void testUploadAttachmentPathTraversalFilename() throws Exception {
        assertPathTraversalHeaderHandled();
    }

    /**
     * Tests DELETE_ALL with missing recordId.
     */
    @Test
    public void testDeleteAllMissingRecordId() throws Exception {
        assertDeleteAllMissingRecordId();
    }

    /**
     * Tests DOWNLOAD_ALL with missing recordId.
     */
    @Test
    public void testDownloadAllMissingRecordId() throws Exception {
        assertDownloadAllMissingRecordId();
    }

    /**
     * Tests readRequestBody with empty body returns empty JSONObject.
     */
    @Test
    public void testEditAttachmentEmptyRequestBody() throws Exception {
        assertEditEmptyRequestBody();
    }

    /**
     * Tests getFileName when content-disposition header has no filename token.
     */
    @Test
    public void testUploadAttachmentContentDispositionNoFilename() throws Exception {
        assertNoFilenameHandled();
    }
}
