package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for the {@link NotesServlet} class.
 *
 * <p>This test suite verifies the correct behavior of the NotesServlet,
 * ensuring it properly handles GET, POST, and DELETE operations for notes.
 * It covers validation, error handling, permission checks, and response formatting.</p>
 *
 * <p>Tests include validation of:</p>
 * <ul>
 * <li>Parameter validation for all operations</li>
 * <li>Note creation with proper attributes</li>
 * <li>Note retrieval and filtering</li>
 * <li>Note deletion with permission checks</li>
 * <li>Error response formatting</li>
 * <li>JSON serialization/deserialization</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class NoteServletTest extends OBBaseTest {

    private static final String TEST_TABLE_ID = "259";
    private static final String TEST_RECORD_ID = "test-record-123";
    private static final String TEST_NOTE_ID = "note-456";
    private static final String TEST_NOTE_CONTENT = "This is a test note";
    private static final String TEST_USER_ID = "user-789";
    private static final String DIFFERENT_USER_ID = "user-999";
    private static final String PARAM_TABLE = "table";
    private static final String PARAM_RECORD = "record";
    private static final String PARAM_NOTE = "note";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @Mock
    private OBDal mockDal;

    @Mock
    private OBProvider mockProvider;

    @Mock
    private OBContext mockContext;

    @Mock
    private Table mockTable;

    @Mock
    private Note mockNote;

    @Mock
    private User mockUser;

    @Mock
    private Organization mockOrganization;

    @Mock
    private OBCriteria<Note> mockCriteria;

    @Mock
    private java.sql.Connection mockConnection;

    private NotesServlet servlet;
    private StringWriter stringWriter;
    private PrintWriter printWriter;

    /**
     * Sets up the test environment before each test method execution.
     * Initializes mocks and configures common behavior.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new NotesServlet();
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);

        when(mockResponse.getWriter()).thenReturn(printWriter);
        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    // ==================== GET Tests ====================

    /**
     * Tests successful retrieval of notes with valid parameters.
     */
    @Test
    public void testGetNotes_Success() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(TEST_TABLE_ID);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);

        List<Note> mockNotes = createMockNotesList();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(mockTable);
            when(mockDal.createCriteria(Note.class)).thenReturn(mockCriteria);
            when(mockCriteria.add(any())).thenReturn(mockCriteria);
            when(mockCriteria.addOrderBy(anyString(), anyBoolean())).thenReturn(mockCriteria);
            when(mockCriteria.list()).thenReturn(mockNotes);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockResponse).setContentType("application/json");
            verify(mockResponse).setCharacterEncoding("UTF-8");

            String responseContent = stringWriter.toString();
            assertNotNull("Response should not be null", responseContent);
            assertTrue("Response should be a JSON array", responseContent.startsWith("["));
        }
    }

    /**
     * Tests GET request with missing table parameter.
     */
    @Test
    public void testGetNotes_MissingTableParameter() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing parameters",
                responseContent.contains("Missing required parameters"));
    }

    /**
     * Tests GET request with missing record parameter.
     */
    @Test
    public void testGetNotes_MissingRecordParameter() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(TEST_TABLE_ID);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(null);

        // Act
        servlet.doGet(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing parameters",
                responseContent.contains("Missing required parameters"));
    }

    /**
     * Tests GET request with invalid table ID.
     */
    @Test
    public void testGetNotes_InvalidTableId() throws Exception {
        // Arrange
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn("invalid-table");
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.get(Table.class, "invalid-table")).thenReturn(null);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention invalid table",
                    responseContent.contains("Invalid table ID"));
        }
    }

    // ==================== POST Tests ====================

    /**
     * Tests successful creation of a note with valid data.
     */
    @Test
    public void testCreateNote_Success() throws Exception {
        // Arrange
        String requestBody = createNoteRequestBody();
        BufferedReader reader = new BufferedReader(new StringReader(requestBody));
        when(mockRequest.getReader()).thenReturn(reader);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            providerMock.when(OBProvider::getInstance).thenReturn(mockProvider);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(mockTable);
            when(mockProvider.get(Note.class)).thenReturn(mockNote);
            when(mockNote.getId()).thenReturn(TEST_NOTE_ID);
            when(mockNote.getNote()).thenReturn(TEST_NOTE_CONTENT);
            when(mockNote.getTable()).thenReturn(mockTable);
            when(mockNote.getRecord()).thenReturn(TEST_RECORD_ID);
            when(mockNote.getCreatedBy()).thenReturn(mockUser);
            when(mockNote.getCreationDate()).thenReturn(new Date());
            when(mockNote.getUpdated()).thenReturn(new Date());
            when(mockTable.getId()).thenReturn(TEST_TABLE_ID);
            when(mockUser.getIdentifier()).thenReturn("Test User");

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockNote).setTable(mockTable);
            verify(mockNote).setRecord(TEST_RECORD_ID);
            verify(mockNote).setNote(TEST_NOTE_CONTENT);
            verify(mockNote).setActive(true);
            verify(mockDal).save(mockNote);
            verify(mockDal).flush();

            String responseContent = stringWriter.toString();
            assertNotNull("Response should not be null", responseContent);
            assertTrue("Response should contain note ID", responseContent.contains(TEST_NOTE_ID));
        }
    }

    /**
     * Tests POST request with missing table parameter.
     */
    @Test
    public void testCreateNote_MissingTableParameter() throws Exception {
        // Arrange
        JSONObject requestJson = new JSONObject();
        requestJson.put(PARAM_RECORD, TEST_RECORD_ID);
        requestJson.put(PARAM_NOTE, TEST_NOTE_CONTENT);

        BufferedReader reader = new BufferedReader(
                new StringReader(requestJson.toString()));
        when(mockRequest.getReader()).thenReturn(reader);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing table",
                responseContent.contains("Missing required parameter: table"));
    }

    /**
     * Tests POST request with missing record parameter.
     */
    @Test
    public void testCreateNote_MissingRecordParameter() throws Exception {
        // Arrange
        JSONObject requestJson = new JSONObject();
        requestJson.put(PARAM_TABLE, TEST_TABLE_ID);
        requestJson.put(PARAM_NOTE, TEST_NOTE_CONTENT);

        BufferedReader reader = new BufferedReader(
                new StringReader(requestJson.toString()));
        when(mockRequest.getReader()).thenReturn(reader);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing record",
                responseContent.contains("Missing required parameter: record"));
    }

    /**
     * Tests POST request with missing note content.
     */
    @Test
    public void testCreateNote_MissingNoteContent() throws Exception {
        // Arrange
        JSONObject requestJson = new JSONObject();
        requestJson.put(PARAM_TABLE, TEST_TABLE_ID);
        requestJson.put(PARAM_RECORD, TEST_RECORD_ID);

        BufferedReader reader = new BufferedReader(
                new StringReader(requestJson.toString()));
        when(mockRequest.getReader()).thenReturn(reader);

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing note",
                responseContent.contains("Missing required parameter: note"));
    }

    /**
     * Tests POST request with invalid table ID.
     */
    @Test
    public void testCreateNote_InvalidTableId() throws Exception {
        // Arrange
        String requestBody = createNoteRequestBody();
        BufferedReader reader = new BufferedReader(new StringReader(requestBody));
        when(mockRequest.getReader()).thenReturn(reader);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(null);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention invalid table",
                    responseContent.contains("Invalid table ID"));
        }
    }

    // ==================== DELETE Tests ====================

    /**
     * Tests successful deletion of a note by its creator.
     */
    @Test
    public void testDeleteNote_Success() throws Exception {
        // Arrange
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);
        when(mockDal.getConnection()).thenReturn(mockConnection);
        when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(mockNote);
        when(mockNote.getCreatedBy()).thenReturn(mockUser);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockConnection).setAutoCommit(false);
            verify(mockDal).remove(mockNote);
            verify(mockDal).flush();
            verify(mockDal).commitAndClose();

            String responseContent = stringWriter.toString();
            assertTrue("Response should indicate success",
                    responseContent.contains("\"success\":true"));
            assertTrue("Response should contain note ID",
                    responseContent.contains(TEST_NOTE_ID));
        }
    }

    /**
     * Tests DELETE request with missing note ID in path.
     */
    @Test
    public void testDeleteNote_MissingNoteId() throws Exception {
        // Arrange
        when(mockRequest.getPathInfo()).thenReturn(null);

        // Act
        servlet.doDelete(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = stringWriter.toString();
        assertTrue("Error message should mention missing note ID",
                responseContent.contains("Missing note ID in path"));
    }

    /**
     * Tests DELETE request with non-existent note ID.
     */
    @Test
    public void testDeleteNote_NoteNotFound() throws Exception {
        // Arrange
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.getConnection()).thenReturn(mockConnection);
            when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(null);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);
            verify(mockConnection).setAutoCommit(false);
            verify(mockDal).rollbackAndClose();

            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention note not found",
                    responseContent.contains("Note not found"));
        }
    }

    /**
     * Tests DELETE request when user doesn't have permission (not the creator).
     */
    @Test
    public void testDeleteNote_InsufficientPermissions() throws Exception {
        // Arrange
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);

        User differentUser = mock(User.class);
        when(differentUser.getId()).thenReturn(DIFFERENT_USER_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.getConnection()).thenReturn(mockConnection);
            when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(mockNote);
            when(mockNote.getCreatedBy()).thenReturn(differentUser);
            when(mockUser.getId()).thenReturn(TEST_USER_ID);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockConnection).setAutoCommit(false);
            verify(mockDal).rollbackAndClose();
            verify(mockDal, never()).remove(any());

            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention insufficient permissions",
                    responseContent.contains("Insufficient permissions"));
        }
    }

    /**
     * Tests DELETE request when note creator is null (edge case).
     */
    @Test
    public void testDeleteNote_NullCreator() throws Exception {
        // Arrange
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            dalMock.when(OBDal::getInstance).thenReturn(mockDal);
            contextMock.when(OBContext::getOBContext).thenReturn(mockContext);

            when(mockDal.getConnection()).thenReturn(mockConnection);
            when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(mockNote);
            when(mockNote.getCreatedBy()).thenReturn(null); // Null creator
            when(mockNote.getId()).thenReturn(TEST_NOTE_ID);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockConnection).setAutoCommit(false);
            verify(mockDal).rollbackAndClose();
            verify(mockDal, never()).remove(any());

            String responseContent = stringWriter.toString();
            assertTrue("Error message should mention insufficient permissions",
                    responseContent.contains("Insufficient permissions"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a list of mock notes for testing.
     */
    private List<Note> createMockNotesList() {
        List<Note> notes = new ArrayList<>();

        Note note1 = mock(Note.class);
        when(note1.getId()).thenReturn(TEST_NOTE_ID);
        when(note1.getNote()).thenReturn(TEST_NOTE_CONTENT);
        when(note1.getTable()).thenReturn(mockTable);
        when(note1.getRecord()).thenReturn(TEST_RECORD_ID);
        when(note1.getCreatedBy()).thenReturn(mockUser);
        when(note1.getCreationDate()).thenReturn(new Date());
        when(note1.getUpdated()).thenReturn(new Date());
        when(mockTable.getId()).thenReturn(TEST_TABLE_ID);
        when(mockUser.getIdentifier()).thenReturn("Test User");

        notes.add(note1);
        return notes;
    }

    /**
     * Creates a JSON request body for note creation.
     */
    private String createNoteRequestBody() throws Exception {
        JSONObject json = new JSONObject();
        json.put(PARAM_TABLE, TEST_TABLE_ID);
        json.put(PARAM_RECORD, TEST_RECORD_ID);
        json.put(PARAM_NOTE, TEST_NOTE_CONTENT);
        return json.toString();
    }
}