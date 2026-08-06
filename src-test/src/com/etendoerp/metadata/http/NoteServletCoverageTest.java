/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;

/**
 * Additional coverage tests for {@link NotesServlet}.
 * Targets uncovered paths: exception handlers in doGet/doPost/doDelete,
 * SWS path-based methods, findTable exception, empty request body,
 * extractNoteIdFromPath edge cases, deleteNote exception, and
 * canDeleteNote exception path.
 */
@RunWith(MockitoJUnitRunner.class)
public class NoteServletCoverageTest extends NoteServletTestSupport {

    private static final String ERROR_PROCESSING_REQUEST = "Error processing request";
    private static final String SHOULD_CONTAIN_ERROR_PROCESSING = "Should contain error processing";
    private static final String TEST_PATH = "/test/path";

    // ==================== Exception Handling in doGet ====================

    /**
     * Tests doGet wraps exceptions and returns 500.
     */
    @Test
    public void testDoGet_ExceptionHandling() throws Exception {
        when(mockRequest.getParameter(PARAM_TABLE)).thenThrow(new RuntimeException("Unexpected error"));

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertTrue(SHOULD_CONTAIN_ERROR_PROCESSING, getResponseContent().contains(ERROR_PROCESSING_REQUEST));
    }

    // ==================== Exception Handling in doPost ====================

    /**
     * Tests doPost wraps exceptions and returns 500.
     */
    @Test
    public void testDoPost_ExceptionHandling() throws Exception {
        when(mockRequest.getReader()).thenThrow(new RuntimeException("Parse error"));

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertTrue(SHOULD_CONTAIN_ERROR_PROCESSING, getResponseContent().contains(ERROR_PROCESSING_REQUEST));
    }

    // ==================== Exception Handling in doDelete ====================

    /**
     * Tests doDelete wraps exceptions and returns 500.
     */
    @Test
    public void testDoDelete_ExceptionHandling() throws Exception {
        when(mockRequest.getPathInfo()).thenThrow(new RuntimeException("Path error"));

        servlet.doDelete(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertTrue(SHOULD_CONTAIN_ERROR_PROCESSING, getResponseContent().contains(ERROR_PROCESSING_REQUEST));
    }

    // ==================== SWS Path-Based Methods ====================

    /**
     * Tests SWS doGet with path parameter delegates to handleGet.
     */
    @Test
    public void testSWSDoGet() throws Exception {
        stubGetParameters();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteCriteria(new ArrayList<>());

            servlet.doGet(TEST_PATH, mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
        }
    }

    /**
     * Tests SWS doPost with path parameter delegates to handlePost.
     */
    @Test
    public void testSWSDoPost() throws Exception {
        stubRequestBody("{}");

        servlet.doPost(TEST_PATH, mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention missing table", getResponseContent().contains("Missing required parameter: table"));
    }

    /**
     * Tests SWS doDelete with path parameter delegates to handleDelete.
     */
    @Test
    public void testSWSDoDelete() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn(null);

        servlet.doDelete(TEST_PATH, mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention missing note ID", getResponseContent().contains("Missing note ID in path"));
    }

    // ==================== findTable exception path ====================

    /**
     * Tests findTable returns null when an exception is thrown.
     */
    @Test
    public void testGetNotes_FindTableException() throws Exception {
        stubGetParameters();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            setupDal(dalMock);
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenThrow(new RuntimeException("DB error"));

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue("Should mention invalid table", getResponseContent().contains("Invalid table ID"));
        }
    }

    // ==================== extractNoteIdFromPath edge cases ====================

    /**
     * Tests DELETE with path "/" only (empty note ID after leading slash).
     * extractNoteIdFromPath returns null for "/" because length is not > 1,
     * so handleDelete responds with 400 "Missing note ID in path".
     */
    @Test
    public void testDeleteNote_SlashOnlyPath() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn("/");

        servlet.doDelete(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention missing note ID", getResponseContent().contains("Missing note ID in path"));
    }

    // ==================== readRequestBody empty body ====================

    /**
     * Tests POST with empty request body.
     */
    @Test
    public void testCreateNote_EmptyRequestBody() throws Exception {
        stubRequestBody("");

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should mention missing table", getResponseContent().contains("Missing required parameter: table"));
    }

    // ==================== GET with empty table/record strings ====================

    /**
     * Tests GET with empty string table parameter.
     */
    @Test
    public void testGetNotes_EmptyTableParameter() throws Exception {
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn("");
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests GET with empty string record parameter.
     */
    @Test
    public void testGetNotes_EmptyRecordParameter() throws Exception {
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(TEST_TABLE_ID);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn("");

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
    }

    // ==================== GET success with empty notes list ====================

    /**
     * Tests GET success returning empty notes list.
     */
    @Test
    public void testGetNotes_EmptyList() throws Exception {
        stubGetParameters();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteCriteria(new ArrayList<>());

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            assertEquals("Should return empty array", "[]", getResponseContent());
        }
    }

    // ==================== handleDelete internal exception ====================

    /**
     * Tests DELETE when deleteNote throws OBException (inner try-catch in handleDelete).
     */
    @Test
    public void testDeleteNote_DeleteThrowsException() throws Exception {
        stubNotePath();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteForDelete(mockUser);
            when(mockUser.getId()).thenReturn(TEST_USER_ID);

            doThrow(new OBException("Delete failed")).when(mockDal).remove(mockNote);

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== canDeleteNote exception path ====================

    /**
     * Tests DELETE when canDeleteNote throws exception (returns false).
     */
    @Test
    public void testDeleteNote_CanDeleteThrowsException() throws Exception {
        stubNotePath();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            User creatorUser = mock(User.class);
            when(creatorUser.getId()).thenThrow(new RuntimeException("User error"));
            setupNoteForDelete(creatorUser);

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
        }
    }

    // ==================== noteToJson with null dates ====================

    /**
     * Tests GET with note having null creationDate and updated.
     */
    @Test
    public void testGetNotes_NullDates() throws Exception {
        stubGetParameters();

        Note note1 = mock(Note.class);
        stubNoteJsonFields(note1);
        when(note1.getCreationDate()).thenReturn(null);
        when(note1.getUpdated()).thenReturn(null);

        List<Note> notes = new ArrayList<>();
        notes.add(note1);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupNoteCriteria(notes);

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            String content = getResponseContent();
            assertFalse("Should not contain creationDate for null", content.contains("creationDate"));
            assertFalse("Should not contain updated for null", content.contains("updated"));
        }
    }

    // ==================== sendErrorResponse exception path ====================

    /**
     * Tests sendErrorResponse when writing response throws IOException.
     */
    @Test
    public void testSendErrorResponse_IOException() throws Exception {
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(null);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);
        // Override writer to throw on first call after error
        when(mockResponse.getWriter()).thenThrow(new java.io.IOException("Write failed"));

        servlet.doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    // ==================== POST with invalid table for create ====================

    /**
     * Tests POST is rejected when findTable fails with an exception.
     */
    @Test
    public void testCreateNote_FindTableException() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {
            setupDal(dalMock);
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenThrow(new RuntimeException("DB error"));

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue("Should mention invalid table", getResponseContent().contains("Invalid table ID"));
        }
    }
}
