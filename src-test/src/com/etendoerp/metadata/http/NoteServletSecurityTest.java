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

import static com.etendoerp.metadata.MetadataTestConstants.TABLE_ID;
import static com.etendoerp.metadata.MetadataTestConstants.TEST_USER_ID;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBSecurityException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.OrganizationEnabled;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.SecurityChecker;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.OBBaseTest;

/**
 * Security related unit tests for {@link NotesServlet}.
 *
 * <p>
 * These tests cover the two guarantees added to the servlet:
 * </p>
 * <ul>
 * <li>the AD_Table row is read in administrator mode, because AD_Table is System level metadata and
 * a functional role cannot read it, which made every request fail with a misleading
 * "Invalid table ID" response;</li>
 * <li>the record the note belongs to is checked against the caller privileges, mirroring the
 * classic NoteDataSource behaviour.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class NoteServletSecurityTest extends OBBaseTest {

    private static final String TEST_RECORD_ID = "test-record-123";
    private static final String TEST_NOTE_ID = "note-456";
    private static final String TEST_NOTE_CONTENT = "This is a test note";
    private static final String PARAM_TABLE = "table";
    private static final String PARAM_RECORD = "record";
    private static final String INVALID_TABLE_MESSAGE = "Invalid table ID";
    private static final String FORBIDDEN_RECORD_MESSAGE = "Insufficient permissions to access record";
    private static final String SHOULD_REPORT_INVALID_TABLE = "Should report an invalid table ID";
    private static final String SHOULD_REPORT_FORBIDDEN_RECORD = "Should report a forbidden record";
    private static final String ACCESS_DENIED = "Access denied";

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
    private ModelProvider mockModelProvider;

    @Mock
    private SecurityChecker mockSecurityChecker;

    @Mock
    private Entity mockEntity;

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

    private NotesServlet servlet;
    private StringWriter stringWriter;

    /**
     * Initializes the servlet under test, the captured response writer and the context defaults
     * shared by every scenario.
     *
     * @throws Exception
     *             when the base test setup or the response writer cannot be initialized
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        servlet = new NotesServlet();
        stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        lenient().when(mockResponse.getWriter()).thenReturn(printWriter);
        lenient().when(mockContext.getUser()).thenReturn(mockUser);
        lenient().when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
        lenient().when(mockUser.getId()).thenReturn(TEST_USER_ID);
    }

    // ==================== findTable runs in administrator mode ====================

    /**
     * Tests that fetching notes loads the AD_Table row in administrator mode, which is what allows a
     * functional role to use the endpoint at all.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testGetNotesLoadsTableInAdminMode() throws Exception {
        stubGetParameters();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class)) {

            setupDal(dalMock);
            setupModelProvider(modelMock, null);
            when(mockDal.get(Table.class, TABLE_ID)).thenReturn(mockTable);
            setupNoteCriteria(new ArrayList<>());

            servlet.doGet(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            contextMock.verify(() -> OBContext.setAdminMode(true), times(2));
        }
    }

    /**
     * Tests that an unknown table id is still reported as a bad request.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithUnknownTableReturnsBadRequest() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDal(dalMock);
            when(mockDal.get(Table.class, TABLE_ID)).thenReturn(null);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue(SHOULD_REPORT_INVALID_TABLE, stringWriter.toString().contains(INVALID_TABLE_MESSAGE));
            verify(mockDal, never()).save(any());
        }
    }

    /**
     * Tests that a security failure while reading the table is swallowed, reported as a bad request
     * and that the previous privilege mode is restored.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithDeniedTableReadReturnsBadRequest() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class)) {

            setupDal(dalMock);
            when(mockDal.get(Table.class, TABLE_ID)).thenThrow(new OBSecurityException(ACCESS_DENIED));

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue(SHOULD_REPORT_INVALID_TABLE, stringWriter.toString().contains(INVALID_TABLE_MESSAGE));
            contextMock.verify(OBContext::restorePreviousMode, times(1));
        }
    }

    // ==================== record level access ====================

    /**
     * Tests that a note is rejected with 403 when the caller cannot read the record it is attached
     * to, and that nothing is persisted.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithUnreadableRecordReturnsForbidden() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDal(dalMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            when(mockDal.get(Table.class, TABLE_ID)).thenReturn(mockTable);

            OrganizationEnabled record = stubRecord();
            doThrow(new OBSecurityException(ACCESS_DENIED)).when(mockSecurityChecker)
                    .checkReadableAccess(record);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(SHOULD_REPORT_FORBIDDEN_RECORD,
                    stringWriter.toString().contains(FORBIDDEN_RECORD_MESSAGE));
            verify(mockDal, never()).save(any());
        }
    }

    /**
     * Tests that a readable record lets the note be created and that the record is actually handed
     * over to the platform security check.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithReadableRecordCreatesNote() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            setupNoteCreation(providerMock);

            OrganizationEnabled record = stubRecord();

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockSecurityChecker).checkReadableAccess(record);
            verify(mockDal).save(mockNote);
        }
    }

    /**
     * Tests that a record which is not organization aware skips the platform security check instead
     * of failing.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithNonOrganizationRecordSkipsSecurityCheck() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, mockEntity);
            setupNoteCreation(providerMock);

            stubMappingClass();
            when(mockDal.get(Object.class, TEST_RECORD_ID)).thenReturn(new Object());

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            checkerMock.verifyNoInteractions();
        }
    }

    /**
     * Tests that a table without an entity in the runtime model does not block the note creation,
     * matching the classic datasource behaviour.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithoutRuntimeEntitySkipsRecordCheck() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, null);
            setupNoteCreation(providerMock);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockDal).save(mockNote);
        }
    }

    /**
     * Tests that the AD_Table row is read only once per request, since the loaded table is reused by
     * the creation step instead of being fetched again.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteReadsTableOnlyOnce() throws Exception {
        stubPostBody();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, null);
            setupNoteCreation(providerMock);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockDal, times(1)).get(Table.class, TABLE_ID);
            verify(mockNote).setTable(mockTable);
        }
    }

    // ==================== DELETE record level access ====================

    /**
     * Tests that a note cannot be deleted when the caller cannot read the record it belongs to.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testDeleteNoteWithUnreadableRecordReturnsForbidden() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDal(dalMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            setupStoredNote();

            OrganizationEnabled record = stubRecord();
            doThrow(new OBSecurityException(ACCESS_DENIED)).when(mockSecurityChecker)
                    .checkReadableAccess(record);

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(SHOULD_REPORT_FORBIDDEN_RECORD,
                    stringWriter.toString().contains(FORBIDDEN_RECORD_MESSAGE));
            verify(mockDal, never()).remove(any());
        }
    }

    /**
     * Tests that the note is deleted when the record is readable and the caller created the note.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testDeleteNoteWithReadableRecordRemovesNote() throws Exception {
        when(mockRequest.getPathInfo()).thenReturn("/" + TEST_NOTE_ID);

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            setupStoredNote();
            when(mockNote.getCreatedBy()).thenReturn(mockUser);

            stubRecord();

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockDal).remove(mockNote);
            verify(mockDal).flush();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Wires the DAL singleton to its mock.
     *
     * @param dalMock
     *            static mock of {@link OBDal}
     */
    private void setupDal(MockedStatic<OBDal> dalMock) {
        dalMock.when(OBDal::getInstance).thenReturn(mockDal);
    }

    /**
     * Wires the DAL and context singletons to their mocks. Only needed by the flows that read the
     * current user, client or organization from the context.
     *
     * @param dalMock
     *            static mock of {@link OBDal}
     * @param contextMock
     *            static mock of {@link OBContext}
     */
    private void setupDalAndContext(MockedStatic<OBDal> dalMock, MockedStatic<OBContext> contextMock) {
        setupDal(dalMock);
        contextMock.when(OBContext::getOBContext).thenReturn(mockContext);
    }

    /**
     * Wires the runtime model singleton so that the given entity is resolved for the test table.
     *
     * @param modelMock
     *            static mock of {@link ModelProvider}
     * @param entity
     *            the entity to resolve, or null to simulate a table missing from the runtime model
     */
    private void setupModelProvider(MockedStatic<ModelProvider> modelMock, Entity entity) {
        modelMock.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
        when(mockModelProvider.getEntityByTableId(TABLE_ID)).thenReturn(entity);
    }

    /**
     * Wires the platform security checker singleton to its mock.
     *
     * @param checkerMock
     *            static mock of {@link SecurityChecker}
     */
    private void setupSecurityChecker(MockedStatic<SecurityChecker> checkerMock) {
        checkerMock.when(SecurityChecker::getInstance).thenReturn(mockSecurityChecker);
    }

    /**
     * Makes the resolved entity return an organization aware record for the test record id.
     *
     * @return the record returned by the DAL for the test record id
     */
    private OrganizationEnabled stubRecord() {
        OrganizationEnabled record = mock(OrganizationEnabled.class);
        stubMappingClass();
        when(mockDal.get(Object.class, TEST_RECORD_ID)).thenReturn(record);

        return record;
    }

    /**
     * Makes the resolved entity expose a mapping class the DAL stubs can match. {@code doReturn} is
     * required because the wildcard return type of {@link Entity#getMappingClass()} cannot be
     * stubbed with {@code thenReturn}.
     */
    private void stubMappingClass() {
        doReturn(Object.class).when(mockEntity).getMappingClass();
    }

    /**
     * Prepares the request parameters of a notes fetch.
     */
    private void stubGetParameters() {
        when(mockRequest.getParameter(PARAM_TABLE)).thenReturn(TABLE_ID);
        when(mockRequest.getParameter(PARAM_RECORD)).thenReturn(TEST_RECORD_ID);
    }

    /**
     * Prepares a valid note creation request body.
     *
     * @throws Exception
     *             when the request reader cannot be stubbed
     */
    private void stubPostBody() throws Exception {
        String body = "{\"table\":\"" + TABLE_ID + "\",\"record\":\"" + TEST_RECORD_ID + "\",\"note\":\""
                + TEST_NOTE_CONTENT + "\"}";
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
    }

    /**
     * Prepares the criteria used to fetch the notes of a record.
     *
     * @param notes
     *            the notes returned by the criteria
     */
    private void setupNoteCriteria(List<Note> notes) {
        when(mockDal.createCriteria(Note.class)).thenReturn(mockCriteria);
        when(mockCriteria.add(any())).thenReturn(mockCriteria);
        when(mockCriteria.addOrderBy(anyString(), anyBoolean())).thenReturn(mockCriteria);
        when(mockCriteria.list()).thenReturn(notes);
    }

    /**
     * Prepares everything needed to create and serialize a new note.
     *
     * @param providerMock
     *            static mock of {@link OBProvider}
     */
    private void setupNoteCreation(MockedStatic<OBProvider> providerMock) {
        providerMock.when(OBProvider::getInstance).thenReturn(mockProvider);
        when(mockProvider.get(Note.class)).thenReturn(mockNote);
        when(mockDal.get(Table.class, TABLE_ID)).thenReturn(mockTable);
        stubNoteSerialization();
    }

    /**
     * Prepares the note lookup performed by the delete flow.
     */
    private void setupStoredNote() {
        when(mockDal.get(Note.class, TEST_NOTE_ID)).thenReturn(mockNote);
        when(mockNote.getTable()).thenReturn(mockTable);
        when(mockTable.getId()).thenReturn(TABLE_ID);
        when(mockNote.getRecord()).thenReturn(TEST_RECORD_ID);
    }

    /**
     * Prepares the note getters read while building the JSON response.
     */
    private void stubNoteSerialization() {
        lenient().when(mockNote.getId()).thenReturn(TEST_NOTE_ID);
        lenient().when(mockNote.getNote()).thenReturn(TEST_NOTE_CONTENT);
        lenient().when(mockNote.getTable()).thenReturn(mockTable);
        lenient().when(mockNote.getRecord()).thenReturn(TEST_RECORD_ID);
        lenient().when(mockNote.getCreatedBy()).thenReturn(mockUser);
        lenient().when(mockNote.getCreationDate()).thenReturn(new Date());
        lenient().when(mockNote.getUpdated()).thenReturn(new Date());
        lenient().when(mockTable.getId()).thenReturn(TABLE_ID);
        lenient().when(mockUser.getIdentifier()).thenReturn("Test User");
    }
}
