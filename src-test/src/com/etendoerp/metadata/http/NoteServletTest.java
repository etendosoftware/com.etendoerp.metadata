/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.http;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.access.User;

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
public class NoteServletTest extends NoteServletTestSupport {

    private static final String DIFFERENT_USER_ID = "user-999";

    // ==================== GET Tests ====================

    /**
     * Tests successful retrieval of notes with valid parameters.
     */
    @Test
    public void testGetNotes_Success() throws Exception {
        // Arrange
        stubGetParameters();

        List<Note> mockNotes = createMockNotesList();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteCriteria(mockNotes);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockResponse).setContentType("application/json");
            verify(mockResponse).setCharacterEncoding("UTF-8");

            String responseContent = getResponseContent();
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
        String responseContent = getResponseContent();
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
        String responseContent = getResponseContent();
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
            setupDal(dalMock);
            when(mockDal.get(Table.class, "invalid-table")).thenReturn(null);

            // Act
            servlet.doGet(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = getResponseContent();
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
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteCreation(providerMock);

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

            String responseContent = getResponseContent();
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

        stubRequestBody(requestJson.toString());

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = getResponseContent();
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

        stubRequestBody(requestJson.toString());

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = getResponseContent();
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

        stubRequestBody(requestJson.toString());

        // Act
        servlet.doPost(mockRequest, mockResponse);

        // Assert
        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        String responseContent = getResponseContent();
        assertTrue("Error message should mention missing note",
                responseContent.contains("Missing required parameter: note"));
    }

    /**
     * Tests POST request with invalid table ID.
     */
    @Test
    public void testCreateNote_InvalidTableId() throws Exception {
        // Arrange
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            setupDal(dalMock);
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(null);

            // Act
            servlet.doPost(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            String responseContent = getResponseContent();
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
        stubNotePath();
        setupNoteForDelete(mockUser);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockDal).remove(mockNote);
            verify(mockDal).flush();

            String responseContent = getResponseContent();
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
        String responseContent = getResponseContent();
        assertTrue("Error message should mention missing note ID",
                responseContent.contains("Missing note ID in path"));
    }

    /**
     * Tests DELETE request with non-existent note ID.
     */
    @Test
    public void testDeleteNote_NoteNotFound() throws Exception {
        // Arrange
        stubNotePath();
        when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(null);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_NOT_FOUND);

            String responseContent = getResponseContent();
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
        stubNotePath();

        User differentUser = mock(User.class);
        when(differentUser.getId()).thenReturn(DIFFERENT_USER_ID);

        setupNoteForDelete(differentUser);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockDal, never()).remove(any());

            String responseContent = getResponseContent();
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
        stubNotePath();
        setupNoteForDelete(null); // Null creator
        when(mockNote.getId()).thenReturn(TEST_NOTE_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);

            // Act
            servlet.doDelete(mockRequest, mockResponse);

            // Assert
            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            verify(mockDal, never()).remove(any());

            String responseContent = getResponseContent();
            assertTrue("Error message should mention insufficient permissions",
                    responseContent.contains("Insufficient permissions"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a list of mock notes for testing.
     *
     * @return a list holding a single note ready to be serialized
     */
    private List<Note> createMockNotesList() {
        List<Note> notes = new ArrayList<>();

        Note note1 = mock(Note.class);
        stubNoteJsonFields(note1);

        notes.add(note1);
        return notes;
    }
}