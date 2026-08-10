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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.apache.http.HttpStatus;
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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.SecurityChecker;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Table;

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
public class NoteServletSecurityTest extends NoteServletTestSupport {

    private static final String INVALID_TABLE_MESSAGE = "Invalid table ID";
    private static final String FORBIDDEN_RECORD_MESSAGE = "Insufficient permissions to access record";
    private static final String SHOULD_REPORT_INVALID_TABLE = "Should report an invalid table ID";
    private static final String SHOULD_REPORT_FORBIDDEN_RECORD = "Should report a forbidden record";
    private static final String ACCESS_DENIED = "Access denied";

    @Mock
    private ModelProvider mockModelProvider;

    @Mock
    private SecurityChecker mockSecurityChecker;

    @Mock
    private Entity mockEntity;

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
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(null);

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue(SHOULD_REPORT_INVALID_TABLE, getResponseContent().contains(INVALID_TABLE_MESSAGE));
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
            when(mockDal.get(Table.class, TEST_TABLE_ID)).thenThrow(new OBSecurityException(ACCESS_DENIED));

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
            assertTrue(SHOULD_REPORT_INVALID_TABLE, getResponseContent().contains(INVALID_TABLE_MESSAGE));
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
            setupTableLookup();
            denyRecordAccess(stubRecord());

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(SHOULD_REPORT_FORBIDDEN_RECORD, getResponseContent().contains(FORBIDDEN_RECORD_MESSAGE));
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

            OrganizationEnabled currentRecord = stubRecord();

            servlet.doPost(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockSecurityChecker).checkReadableAccess(currentRecord);
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

            verify(mockDal, times(1)).get(Table.class, TEST_TABLE_ID);
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
        stubNotePath();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDal(dalMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            setupStoredNote(null);
            denyRecordAccess(stubRecord());

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_FORBIDDEN);
            assertTrue(SHOULD_REPORT_FORBIDDEN_RECORD, getResponseContent().contains(FORBIDDEN_RECORD_MESSAGE));
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
        stubNotePath();

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDalAndContext(dalMock, contextMock);
            setupModelProvider(modelMock, mockEntity);
            setupSecurityChecker(checkerMock);
            setupStoredNote(mockUser);
            stubRecord();

            servlet.doDelete(mockRequest, mockResponse);

            verify(mockResponse).setStatus(HttpStatus.SC_OK);
            verify(mockDal).remove(mockNote);
            verify(mockDal).flush();
        }
    }

    // ==================== Helper Methods ====================

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
        when(mockModelProvider.getEntityByTableId(TEST_TABLE_ID)).thenReturn(entity);
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
        OrganizationEnabled currentRecord = mock(OrganizationEnabled.class);
        stubMappingClass();
        when(mockDal.get(Object.class, TEST_RECORD_ID)).thenReturn(currentRecord);

        return currentRecord;
    }

    /**
     * Makes the platform reject the read of the given record.
     *
     * @param currentRecord
     *            the record the caller is not allowed to read
     */
    private void denyRecordAccess(OrganizationEnabled currentRecord) {
        doThrow(new OBSecurityException(ACCESS_DENIED)).when(mockSecurityChecker)
                .checkReadableAccess(currentRecord);
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
     * Prepares the note lookup performed by the delete flow, including the ids read by the record
     * level access check.
     *
     * @param creator
     *            the user that created the note, null when the creator is not relevant
     */
    private void setupStoredNote(User creator) {
        setupNoteForDelete(creator);
        when(mockTable.getId()).thenReturn(TEST_TABLE_ID);
        when(mockNote.getRecord()).thenReturn(TEST_RECORD_ID);
    }
}
