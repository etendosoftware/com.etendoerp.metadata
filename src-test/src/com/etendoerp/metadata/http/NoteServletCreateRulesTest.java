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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.http.HttpStatus;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.OrganizationEnabled;
import org.openbravo.client.application.Note;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.SecurityChecker;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Tests the rules applied when a note is created through {@link NotesServlet} (ETP-5999):
 * <ul>
 * <li>a blank note or a note longer than the dictionary length of the note column is rejected;</li>
 * <li>the note is stored with the organization of the record it is attached to, as the classic
 * notes section does, falling back to the session organization.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class NoteServletCreateRulesTest extends NoteServletTestSupport {

    private static final int MAX_NOTE_LENGTH = 5;

    @Mock
    private ModelProvider mockModelProvider;

    @Mock
    private SecurityChecker mockSecurityChecker;

    @Mock
    private Entity mockRecordEntity;

    @Mock
    private Entity mockNoteEntity;

    @Mock
    private Property mockNoteProperty;

    @Mock
    private Organization mockRecordOrganization;

    /**
     * Tests that a note made only of white space is rejected and never saved.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateBlankNoteReturnsBadRequest() throws Exception {
        stubRequestBody(buildBody("   "));

        servlet.doPost(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should report the missing note", getResponseContent().contains("Missing required parameter: note"));
    }

    /**
     * Tests that a note longer than the dictionary length of the note column is rejected and never
     * saved.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateTooLongNoteReturnsBadRequest() throws Exception {
        postNote("123456", null, true);

        verify(mockResponse).setStatus(HttpStatus.SC_BAD_REQUEST);
        assertTrue("Should report the maximum length",
                getResponseContent().contains("maximum length of " + MAX_NOTE_LENGTH));
        verify(mockDal, never()).save(any());
    }

    /**
     * Tests that a note exactly as long as the dictionary length is accepted.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteWithinMaxLengthIsSaved() throws Exception {
        postNote("12345", null, true);

        verify(mockResponse).setStatus(HttpStatus.SC_OK);
        verify(mockDal).save(mockNote);
    }

    /**
     * Tests that the note takes the organization of an organization aware record.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteUsesRecordOrganization() throws Exception {
        OrganizationEnabled currentRecord = mock(OrganizationEnabled.class);
        when(currentRecord.getOrganization()).thenReturn(mockRecordOrganization);

        postNote(TEST_NOTE_CONTENT, currentRecord, false);

        verify(mockNote).setOrganization(mockRecordOrganization);
    }

    /**
     * Tests that a note attached to an organization record takes that organization.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteOnOrganizationUsesTheOrganizationItself() throws Exception {
        postNote(TEST_NOTE_CONTENT, mockRecordOrganization, false);

        verify(mockNote).setOrganization(mockRecordOrganization);
    }

    /**
     * Tests that the session organization is used when the record has no organization.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteFallsBackToSessionOrganizationWhenRecordHasNone() throws Exception {
        postNote(TEST_NOTE_CONTENT, mock(OrganizationEnabled.class), false);

        verify(mockNote).setOrganization(mockOrganization);
    }

    /**
     * Tests that the session organization is used when the record cannot be found.
     *
     * @throws Exception
     *             when the servlet invocation fails
     */
    @Test
    public void testCreateNoteFallsBackToSessionOrganizationWhenRecordIsMissing() throws Exception {
        postNote(TEST_NOTE_CONTENT, null, false);

        verify(mockNote).setOrganization(mockOrganization);
    }

    /**
     * Posts a note for the test record with every platform singleton mocked.
     *
     * @param content
     *            the text of the note
     * @param currentRecord
     *            the record the DAL returns for the test record id, null when it does not exist
     * @param limitLength
     *            whether the note column defines {@link #MAX_NOTE_LENGTH} as its length
     * @throws Exception
     *             when the servlet invocation fails
     */
    private void postNote(String content, Object currentRecord, boolean limitLength) throws Exception {
        stubRequestBody(buildBody(content));

        try (MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
                MockedStatic<OBContext> contextMock = mockStatic(OBContext.class);
                MockedStatic<OBProvider> providerMock = mockStatic(OBProvider.class);
                MockedStatic<ModelProvider> modelMock = mockStatic(ModelProvider.class);
                MockedStatic<SecurityChecker> checkerMock = mockStatic(SecurityChecker.class)) {

            setupDalAndContext(dalMock, contextMock);
            lenient().when(mockProvider.get(Note.class)).thenReturn(mockNote);
            providerMock.when(OBProvider::getInstance).thenReturn(mockProvider);
            // Lenient: a rejected note never reaches the table lookup
            lenient().when(mockDal.get(Table.class, TEST_TABLE_ID)).thenReturn(mockTable);
            stubNoteJsonFields(mockNote);
            checkerMock.when(SecurityChecker::getInstance).thenReturn(mockSecurityChecker);
            modelMock.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
            stubRecordLookup(currentRecord);
            if (limitLength) {
                stubNoteLength();
            }

            servlet.doPost(mockRequest, mockResponse);
        }
    }

    /**
     * Makes the runtime model resolve the test table and the DAL return the given record.
     *
     * @param currentRecord
     *            the record returned for the test record id, null when it does not exist
     */
    private void stubRecordLookup(Object currentRecord) {
        lenient().when(mockModelProvider.getEntityByTableId(TEST_TABLE_ID)).thenReturn(mockRecordEntity);
        lenient().when(mockRecordEntity.getMappingClass()).thenAnswer(invocation -> Object.class);
        lenient().when(mockDal.get(Object.class, TEST_RECORD_ID)).thenReturn(currentRecord);
    }

    /**
     * Makes the note column define {@link #MAX_NOTE_LENGTH} as its dictionary length.
     */
    private void stubNoteLength() {
        when(mockModelProvider.getEntity(Note.ENTITY_NAME, false)).thenReturn(mockNoteEntity);
        when(mockNoteEntity.getProperty(Note.PROPERTY_NOTE, false)).thenReturn(mockNoteProperty);
        when(mockNoteProperty.getFieldLength()).thenReturn(MAX_NOTE_LENGTH);
    }

    /**
     * Builds a note creation body for the test table and record.
     *
     * @param content
     *            the text of the note
     * @return the JSON body
     * @throws Exception
     *             when the body cannot be built
     */
    private String buildBody(String content) throws Exception {
        JSONObject body = new JSONObject();
        body.put(PARAM_TABLE, TEST_TABLE_ID);
        body.put(PARAM_RECORD, TEST_RECORD_ID);
        body.put(PARAM_NOTE, content);
        return body.toString();
    }
}
